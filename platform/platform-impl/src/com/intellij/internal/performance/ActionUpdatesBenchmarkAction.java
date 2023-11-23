// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.performance;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.PingProgress;
import com.intellij.openapi.progress.util.PotemkinProgress;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IntellijInternalApi;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.UIUtil;
import kotlin.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

@ApiStatus.Internal
@IntellijInternalApi
public final class ActionUpdatesBenchmarkAction extends DumbAwareAction {


  private static final Logger LOG = Logger.getInstance(ActionUpdatesBenchmarkAction.class);

  private static final long MIN_REPORTED_UPDATE_MILLIS = 5;
  private static final long MIN_REPORTED_NO_CHECK_CANCELED_MILLIS = 20;

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

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
    progress.setText("Warming up...");
    progress.setDelayInMillis(0);
    AtomicLong lastCheckCanceled = new AtomicLong(System.nanoTime());
    Map<String, TraceData> noCheckCanceled = new HashMap<>();
    Runnable noCheckCanceledChecker = () -> {
      long maxDiff = MIN_REPORTED_NO_CHECK_CANCELED_MILLIS;
      while (progress.isRunning()) {
        String text = activityName.get();
        long last = lastCheckCanceled.get();
        long curDiff = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - last);
        if (last != 0 && text != null && curDiff > maxDiff) {
          StackTraceElement[] trace = EDT.getEventDispatchThread().getStackTrace();
          if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - last) > maxDiff &&
              text.equals(activityName.get())) {
            noCheckCanceled.put(text, new TraceData(curDiff, trace, noCheckCanceled.get(text)));
          }
        }
        TimeoutUtil.sleep(maxDiff / 3 + 1);
      }
    };
    MyProgress cancellationChecker = new MyProgress() {
      @Override
      public void interact() {
        lastCheckCanceled.set(System.nanoTime());
      }
    };
    try {
      progress.start(); // show progress but avoid the overhead when measuring
      AppExecutorUtil.getAppExecutorService().execute(noCheckCanceledChecker);
      updateAllActionsInner(project, component, (name, displayName, runnable) -> {
        progress.setText("Checking '" + Objects.requireNonNullElse(displayName, name) + "'");
        progress.interact();
        return ProgressManager.getInstance().runProcess(() -> {
          long start = System.nanoTime();
          try {
            activityName.set(name);
            lastCheckCanceled.set(start);
            runnable.run();
          }
          catch (Throwable th) {
            if (!(th instanceof ControlFlowException)) {
              LOG.error(th); // KotlinStdlibCacheImpl.findStdlibInModuleDependencies PCE
            }
          }
          finally {
            activityName.set(null);
          }
          return TimeoutUtil.getDurationMillis(start);
        }, cancellationChecker);
      });
    }
    finally {
      progress.stop();
    }
    LOG.info("---- " + noCheckCanceled.size() + " no-checkCanceled places detected ----");
    if (!noCheckCanceled.isEmpty()) {
      String[] keys = ArrayUtil.toStringArray(noCheckCanceled.keySet());
      Arrays.sort(keys, Comparator.comparing(o -> -noCheckCanceled.get(o).delta));
      for (int i = 0; i < keys.length; i++) {
        TraceData last = Objects.requireNonNull(noCheckCanceled.get(keys[i]));
        int traceCount = 0;
        for (TraceData cur = last; cur != null; cur = cur.next) traceCount++;
        LOG.info("no checkCanceled (" + i + ") (" + traceCount + " hits) in " + last.delta + " ms - " + keys[i]);
      }
      int min = Math.min(noCheckCanceled.size(), 5);
      LOG.info("---- top " + min + " of " + noCheckCanceled.size() + " no-checkCanceled places ----");
      for (int i = 0; i < keys.length && i < min; i++) {
        TraceData last = noCheckCanceled.get(keys[i]);
        int traceCount = 0, traceIdx = 0;
        for (TraceData cur = last; cur != null; cur = cur.next) traceCount++;
        for (TraceData cur = last; cur != null && traceIdx < 3; cur = cur.next, traceIdx++) {
          Throwable throwable = new Throwable("no checkCanceled (" + i + ") (" + (traceIdx + 1) + " of " + traceCount + " hits)" +
                                              " in " + cur.delta + " ms - " + keys[i]);
          throwable.setStackTrace(cur.trace);
          LOG.info(ExceptionUtil.getThrowableText(throwable, ActionUpdatesBenchmarkAction.class.getName()));
        }
      }
    }
  }

  private static void updateAllActionsInner(@NotNull Project project,
                                            @NotNull Component component,
                                            @NotNull MyRunner activityRunner) {
    ActionManagerImpl actionManager = (ActionManagerImpl)ActionManager.getInstance();
    boolean isDumb = DumbService.isDumb(project);
    List<Pair<Integer, String>> results = new ArrayList<>();

    DataContext rawContext = DataManager.getInstance().getDataContext(component);

    PsiManager.getInstance(project).dropPsiCaches();
    PsiManager.getInstance(project).dropResolveCaches();
    ActionToolbarImpl.resetAllToolbars();

    LOG.info("Benchmarking actions update for component: " + component.getClass().getName());

    long startContext = System.nanoTime();
    DataContext wrappedContext = Utils.createAsyncDataContext(rawContext);
    LOG.info(TimeoutUtil.getDurationMillis(startContext) + " ms to create data-context");

    long startPrecache = System.nanoTime();
    ReadAction.run(() -> {
      for (DataKey<?> key : DataKey.allKeys()) {
        activityRunner.run("DataContext(\"" + key.getName() + "\")", null, () -> wrappedContext.getData(key));
      }
    });
    LOG.info(TimeoutUtil.getDurationMillis(startPrecache) + " ms to pre-cache data-context");
    Set<String> nonUniqueClasses = new HashSet<>();
    {
      Set<String> visited = new HashSet<>();
      for (Iterator<AnAction> it = actionManager.actions(false).iterator(); it.hasNext(); ) {
        AnAction action = it.next();
        if (action.getClass() == DefaultActionGroup.class) {
          continue;
        }
        String className = action.getClass().getName();
        if (!visited.add(className)) {
          nonUniqueClasses.add(className);
        }
      }
    }

    int count = 0;
    long startActions = System.nanoTime();
    for (Iterator<AnAction> it = actionManager.actions(false).iterator(); it.hasNext(); ) {
      AnAction action = it.next();
      if (action.getClass() == DefaultActionGroup.class || isDumb && !DumbService.isDumbAware(action)) {
        continue;
      }

      String id = actionManager.getId(action);

      String className = action.getClass().getName();
      String actionIdIfNeeded = nonUniqueClasses.contains(className) ? " (" + id + ")" : "";
      String actionName = className + actionIdIfNeeded;
      ProgressManager.checkCanceled();
      AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.MAIN_MENU, wrappedContext);
      runAndMeasure(results, actionName,
                    () -> activityRunner.run(actionName, id, () -> ActionUtil.performDumbAwareUpdate(action, event, true)));
      count++;
      if (!(action instanceof ActionGroup)) continue;
      String childrenActionName = className + ".getChildren(" + (event.getPresentation().isEnabled() ? "+" : "-") + ")" + actionIdIfNeeded;
      runAndMeasure(results, childrenActionName,
                    () -> activityRunner.run(actionName, id, () -> ((ActionGroup)action).getChildren(event)));
    }
    long elapsedActions = TimeoutUtil.getDurationMillis(startActions);
    LOG.info(elapsedActions + " ms total to update " + count + " registered actions");
    results.sort(Comparator.comparingInt(o -> -o.first));
    for (Pair<Integer, String> result : results) {
      if (result.first < MIN_REPORTED_UPDATE_MILLIS) break;
      LOG.info(result.first + " ms - " + result.second);
    }
    dumpActionUpdateThreads(nonUniqueClasses);
  }

  private static void dumpActionUpdateThreads(@NotNull Set<String> nonUniqueClasses) {
    ActionManagerImpl actionManager = (ActionManagerImpl)ActionManager.getInstance();
    int[] actionUpdateThreadCounts = new int[ActionUpdateThread.values().length];
    List<String> oldEdtActionNames = new ArrayList<>();
    for (Iterator<AnAction> it = actionManager.actions(false).iterator(); it.hasNext(); ) {
      AnAction action = it.next();
      if (action.getClass() == DefaultActionGroup.class) {
        continue;
      }


      ActionUpdateThread updateThread = action.getActionUpdateThread();
      actionUpdateThreadCounts[updateThread.ordinal()]++;
      if (updateThread == ActionUpdateThread.OLD_EDT) {
        String className = action.getClass().getName();
        String actionIdIfNeeded = nonUniqueClasses.contains(className) ? " (" + actionManager.getId(action) + ")" : "";
        String actionName = className + actionIdIfNeeded;
        oldEdtActionNames.add(actionName);
      }
    }
    List<String> oldEdtExtensions = new ArrayList<>();
    ExtensionsAreaImpl extensionArea = (ExtensionsAreaImpl)ApplicationManager.getApplication().getExtensionArea();
    //noinspection TestOnlyProblems
    extensionArea.processExtensionPoints(ep -> {
      try {
        if (ActionUpdateThreadAware.class.isAssignableFrom(ep.getExtensionClass())) {
          //noinspection unchecked
          List<ActionUpdateThreadAware> extensions = (List<ActionUpdateThreadAware>)ep.getExtensionList();
          for (ActionUpdateThreadAware extension : extensions) {
            ActionUpdateThread updateThread = extension.getActionUpdateThread();
            if (updateThread == ActionUpdateThread.OLD_EDT) {
              oldEdtExtensions.add(extension.getClass().getName());
            }
          }
        }
      }
      catch (Throwable e) {
        LOG.warn(e);
      }
      return Unit.INSTANCE;
    });

    StringBuilder sb = new StringBuilder();
    sb.append("---- action-update-thread stats ----\n");
    sb.append(StringUtil.join(ActionUpdateThread.values(), t -> actionUpdateThreadCounts[t.ordinal()] + ":" + t.name(), ", "));
    if (!oldEdtActionNames.isEmpty()) {
      sb.append("... see the list of registered OLD_EDT actions below:");
      oldEdtActionNames.sort(String::compareTo);
      for (String name : oldEdtActionNames) {
        sb.append("\n").append(name);
      }
      sb.append("\n");
    }
    if (!oldEdtExtensions.isEmpty()) {
      sb.append("\n... and ").append(oldEdtExtensions.size()).append(" OLD_EDT extensions:");
      oldEdtExtensions.sort(String::compareTo);
      for (String name : oldEdtExtensions) {
        sb.append("\n").append(name);
      }
      sb.append("\n");
    }
    LOG.info(sb.toString());
  }

  private static void runAndMeasure(@NotNull List<? super Pair<Integer, String>> results,
                                    @NotNull String actionName,
                                    @NotNull LongSupplier runnable) {
    ReadAction.run(() -> {
      long elapsed = runnable.getAsLong();
      if (elapsed > 0) {
        results.add(Pair.create((int)elapsed, actionName));
      }
    });
  }

  private record TraceData(long delta, StackTraceElement @NotNull [] trace, @Nullable TraceData next) {
  }

  private interface MyRunner {
    long run(String name, String displayName, Runnable runnable);
  }

  private abstract static class MyProgress extends ProgressIndicatorBase implements PingProgress {

  }
}