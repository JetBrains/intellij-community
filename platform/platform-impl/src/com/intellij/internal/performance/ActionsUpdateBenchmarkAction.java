// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.performance;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.progress.util.PotemkinProgress;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class ActionsUpdateBenchmarkAction extends DumbAwareAction {

  private static final Logger LOG = Logger.getInstance(ActionsUpdateBenchmarkAction.class);

  private static final long MIN_REPORTED_UPDATE_MILLIS = 5;
  private static final long MIN_REPORTED_NO_CHECK_CANCELED_MILLIS = 50;

  /** @noinspection StaticNonFinalField */
  public static Consumer<? super String> ourMissingKeysConsumer;

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Component originalComponent = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    Project project = e.getProject();
    InputEvent inputEvent = e.getInputEvent();
    Component component;
    if (inputEvent instanceof MouseEvent) {
      Component c = inputEvent.getComponent();
      component = Objects.requireNonNullElse(
        UIUtil.getDeepestComponentAt(c, ((MouseEvent)inputEvent).getX(), ((MouseEvent)inputEvent).getY()), c);
    }
    else {
      component = originalComponent;
    }
    if (project == null || component == null) return;
    updateAllActions(project, component);
  }

  private static void updateAllActions(@NotNull Project project, @NotNull Component component) {
    AtomicReference<String> activityName = new AtomicReference<>();
    PotemkinProgress progress = new PotemkinProgress("Updating all actions", project, null, null);
    AtomicBoolean finished = new AtomicBoolean();
    AtomicLong lastCheckCanceled = new AtomicLong(System.nanoTime());
    Map<String, TraceData> missingCheckCanceled = new HashMap<>();
    AppExecutorUtil.getAppExecutorService().execute(() -> {
      long maxDiff = MIN_REPORTED_NO_CHECK_CANCELED_MILLIS;
      while (!finished.get()) {
        long last = lastCheckCanceled.get();
        long curDiff = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - last);
        String text = activityName.get();
        if (last != 0 && text != null && curDiff > maxDiff) {
          StackTraceElement[] trace = EDT.getEventDispatchThread().getStackTrace();
          if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - last) > maxDiff &&
              text.equals(activityName.get())) {
            missingCheckCanceled.put(text, new TraceData(curDiff, trace, missingCheckCanceled.get(text)));
          }
        }
        TimeoutUtil.sleep(maxDiff / 3 + 1);
      }
    });
    progress.addStateDelegate(new DummyProgressIndicator() {
      @Override
      public void checkCanceled() throws ProcessCanceledException {
        lastCheckCanceled.set(System.nanoTime());
      }
    });
    try {
      progress.runInSwingThread(() -> {
        updateAllActionsInner(project, component, progress, name -> {
          lastCheckCanceled.set(System.nanoTime());
          activityName.set(name);
        });
      });
    }
    finally {
      finished.set(true);
    }
    if (!missingCheckCanceled.isEmpty()) {
      LOG.info("---- " + missingCheckCanceled.size() + " no-checkCanceled places detected ----");
      String[] keys = ArrayUtil.toStringArray(missingCheckCanceled.keySet());
      Arrays.sort(keys, Comparator.comparing(o -> -missingCheckCanceled.get(o).delta));
      for (int i = 0; i < keys.length; i++) {
        TraceData last = Objects.requireNonNull(missingCheckCanceled.get(keys[i]));
        int traceCount = 0;
        for (TraceData cur = last; cur != null; cur = cur.next) traceCount++;
        LOG.info("no checkCanceled (" + i + ") (" + traceCount + " hits) in " + last.delta + " ms - " + keys[i]);
      }
      int min = Math.min(missingCheckCanceled.size(), 5);
      LOG.info("---- top " + min + " of " + missingCheckCanceled.size() + " no-checkCanceled places ----");
      for (int i = 0; i < keys.length && i < min; i++) {
        TraceData last = missingCheckCanceled.get(keys[i]);
        int traceCount = 0, traceIdx = 0;
        for (TraceData cur = last; cur != null; cur = cur.next) traceCount++;
        for (TraceData cur = last; cur != null && traceIdx < 3; cur = cur.next, traceIdx++) {
          Throwable throwable = new Throwable("no checkCanceled (" + i + ") (" + (traceIdx + 1) + " of " + traceCount + " hits)" +
                                              " in " + cur.delta + " ms - " + keys[i]);
          throwable.setStackTrace(cur.trace);
          LOG.info(ExceptionUtil.getThrowableText(throwable, ActionsUpdateBenchmarkAction.class.getName()));
        }
      }
    }
  }

  private static void updateAllActionsInner(@NotNull Project project,
                                            @NotNull Component component,
                                            @NotNull ProgressIndicator progress,
                                            @NotNull Consumer<String> activityConsumer) {
    ActionManagerImpl actionManager = (ActionManagerImpl)ActionManager.getInstance();
    List<Pair<Integer, String>> results = new ArrayList<>();
    List<Pair<String, String>> results2 = new ArrayList<>();

    DataContext rawContext = DataManager.getInstance().getDataContext(component);

    progress.setText("Dropping resolve caches");
    PsiManager.getInstance(project).dropPsiCaches();
    PsiManager.getInstance(project).dropResolveCaches();
    ActionToolbarImpl.resetAllToolbars();

    progress.setText("Preparing the data context");
    LOG.info("Benchmarking actions update for component: " + component.getClass().getName());

    long startContext = System.nanoTime();
    DataContext wrappedContext = Utils.wrapToAsyncDataContext(rawContext);
    LOG.info(TimeoutUtil.getDurationMillis(startContext) + " ms to create data-context");

    long startPrecache = System.nanoTime();
    ReadAction.run(() -> {
      for (DataKey<?> key : DataKey.allKeys()) {
        progress.setText("Caching '" + key.getName() + "'");
        try {
          activityConsumer.accept("DataContext(\"" + key.getName() + "\")");
          wrappedContext.getData(key);
        }
        finally {
          activityConsumer.accept(null);
        }
      }
    });
    LOG.info(TimeoutUtil.getDurationMillis(startPrecache) + " ms to pre-cache data-context");

    int count = 0;
    long startActions = System.nanoTime();
    for (String id : actionManager.getActionIds()) {
      AnAction action = actionManager.getAction(id);
      if (action == null) continue;
      if (action.getClass() == DefaultActionGroup.class) continue;
      ProgressManager.checkCanceled();
      progress.setText("Checking '" + id + "'");
      AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.MAIN_MENU, wrappedContext);
      ReadAction.run(() -> {
        HashSet<String> ruleKeys = new HashSet<String>();
        long elapsed;
        String actionName = action.getClass().getName();
        try {
          ourMissingKeysConsumer = ruleKeys::add;
          activityConsumer.accept(actionName);
          long start = System.nanoTime();
          ActionUtil.performDumbAwareUpdate(action, event, true);
          elapsed = TimeoutUtil.getDurationMillis(start);
        }
        finally {
          activityConsumer.accept(null);
          ourMissingKeysConsumer = null;
        }
        if (elapsed > 0) {
          results.add(Pair.create((int)elapsed, actionName));
        }
        if (!(action instanceof UpdateInBackground)) {
          if (ruleKeys.isEmpty()) {
            if (event.getPresentation().isEnabled()) {
              results2.add(Pair.create("UI only?", actionName));
            }
          }
          else {
            results2.add(Pair.create(ContainerUtil.sorted(ruleKeys).toString(), actionName));
          }
        }
      });
      count++;
    }
    long elapsedActions = TimeoutUtil.getDurationMillis(startActions);
    LOG.info(elapsedActions + " ms total to update " + count + " actions");
    results.sort(Comparator.comparingInt(o -> -o.first));
    for (Pair<Integer, String> result : results) {
      if (result.first < MIN_REPORTED_UPDATE_MILLIS) break;
      LOG.info(result.first + " ms - " + result.second);
    }
    LOG.info("---- slow data usage for " + results2.size() + " actions ---- ");
    results2.sort(Pair.comparingByFirst());
    for (Pair<String, String> pair : results2) {
      LOG.info(pair.first + " - " + pair.second);
    }
  }

  private static class TraceData {
    final long delta;
    final StackTraceElement[] trace;
    final TraceData next;

    TraceData(long delta, StackTraceElement @NotNull [] trace, @Nullable TraceData next) {
      this.delta = delta;
      this.trace = trace;
      this.next = next;
    }
  }

  static class DummyProgressIndicator implements ProgressIndicatorEx {

    @Override
    public void start() { }

    @Override
    public void stop() { }

    @Override
    public boolean isRunning() { return false; }

    @Override
    public void cancel() { }

    @Override
    public boolean isCanceled() { return false; }

    @Override
    public void setText(String text) { }

    @Override
    public String getText() { return null; }

    @Override
    public void setText2(String text) { }

    @Override
    public String getText2() { return null; }

    @Override
    public double getFraction() { return 0; }

    @Override
    public void setFraction(double fraction) { }

    @Override
    public void pushState() { }

    @Override
    public void popState() { }

    @Override
    public boolean isModal() { return false; }

    @Override
    public @NotNull ModalityState getModalityState() { return ModalityState.NON_MODAL; }

    @Override
    public void setModalityProgress(@Nullable ProgressIndicator modalityProgress) { }

    @Override
    public boolean isIndeterminate() { return false; }

    @Override
    public void setIndeterminate(boolean indeterminate) { }

    @Override
    public void checkCanceled() throws ProcessCanceledException { }

    @Override
    public boolean isPopupWasShown() { return false; }

    @Override
    public boolean isShowing() { return false; }

    @Override
    public void addStateDelegate(@NotNull ProgressIndicatorEx delegate) { }

    @Override
    public void finish(@NotNull TaskInfo task) { }

    @Override
    public boolean isFinished(@NotNull TaskInfo task) { return false; }

    @Override
    public boolean wasStarted() { return false; }

    @Override
    public void processFinish() { }

    @Override
    public void initStateFrom(@NotNull ProgressIndicator indicator) { }
  }
}