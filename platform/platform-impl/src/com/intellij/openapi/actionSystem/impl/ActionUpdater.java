// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.codeWithMe.ClientId;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ProhibitAWTEvents;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.client.ClientSessionsManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import com.intellij.util.containers.*;
import com.intellij.util.ui.EDT;
import io.opentelemetry.context.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.CancellablePromise;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.intellij.diagnostic.telemetry.TraceKt.computeWithSpan;
import static com.intellij.diagnostic.telemetry.TraceKt.runWithSpan;

final class ActionUpdater {
  private static final Logger LOG = Logger.getInstance(ActionUpdater.class);

  static final Key<Boolean> SUPPRESS_SUBMENU_IMPL = Key.create("SUPPRESS_SUBMENU_IMPL");
  private static final String NESTED_WA_REASON_PREFIX = "nested write-action requested by ";
  private static final String OLD_EDT_MSG_SUFFIX = ". Revise AnAction.getActionUpdateThread property";

  static final Executor ourBeforePerformedExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Action Updater (Exclusive)", 1);
  private static final Executor ourCommonExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Action Updater (Common)", 2);
  private static final Executor ourFastTrackExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Action Updater (Fast)", 1);

  private static final List<CancellablePromise<?>> ourPromises = new CopyOnWriteArrayList<>();
  private static FList<String> ourInEDTActionOperationStack = FList.emptyList();
  private static boolean ourNoRulesInEDTSection;

  private final PresentationFactory myPresentationFactory;
  private final DataContext myDataContext;
  private final String myPlace;
  private final boolean myContextMenuAction;
  private final boolean myToolbarAction;
  private final @Nullable Project myProject;

  private final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();
  private final Map<AnAction, Presentation> myUpdatedPresentations = new ConcurrentHashMap<>();
  private final Map<ActionGroup, List<AnAction>> myGroupChildren = new ConcurrentHashMap<>();
  private final UpdateStrategy myRealUpdateStrategy;
  private final UpdateStrategy myCheapStrategy;

  private boolean myAllowPartialExpand = true;
  private boolean myPreCacheSlowDataKeys;
  private ActionUpdateThread myForcedUpdateThread;
  private final Function<? super AnActionEvent, ? extends AnActionEvent> myEventTransform;
  private final Consumer<? super Runnable> myLaterInvocator;
  private final int myTestDelayMillis;

  private int myEDTCallsCount;
  private long myEDTWaitNanos;
  private volatile long myCurEDTWaitMillis;
  private volatile long myCurEDTPerformMillis;

  ActionUpdater(@NotNull PresentationFactory presentationFactory,
                @NotNull DataContext dataContext,
                @NotNull String place,
                boolean isContextMenuAction,
                boolean isToolbarAction) {
    this(presentationFactory, dataContext, place, isContextMenuAction, isToolbarAction, null, null);
  }

  ActionUpdater(@NotNull PresentationFactory presentationFactory,
                @NotNull DataContext dataContext,
                @NotNull String place,
                boolean isContextMenuAction,
                boolean isToolbarAction,
                @Nullable Function<? super AnActionEvent, ? extends AnActionEvent> eventTransform,
                @Nullable Consumer<? super Runnable> laterInvocator) {
    myProject = CommonDataKeys.PROJECT.getData(dataContext);
    myPresentationFactory = presentationFactory;
    myDataContext = dataContext;
    myPlace = place;
    myContextMenuAction = isContextMenuAction;
    myToolbarAction = isToolbarAction;
    myEventTransform = eventTransform;
    myLaterInvocator = laterInvocator;
    myPreCacheSlowDataKeys = Utils.isAsyncDataContext(dataContext) && !Registry.is("actionSystem.update.actions.suppress.dataRules.on.edt");
    myRealUpdateStrategy = new UpdateStrategy(
      action -> updateActionReal(action),
      group -> callAction(group, Op.getChildren, () -> doGetChildren(group, createActionEvent(orDefault(group, myUpdatedPresentations.get(group))))));
    myCheapStrategy = new UpdateStrategy(myPresentationFactory::getPresentation, group -> doGetChildren(group, null));

    myTestDelayMillis = ActionPlaces.ACTION_SEARCH.equals(myPlace) || ActionPlaces.isShortcutPlace(myPlace) ?
                        0 : Registry.intValue("actionSystem.update.actions.async.test.delay", 0);
  }

  private @Nullable Presentation updateActionReal(@NotNull AnAction action) {
    // clone the presentation to avoid partially changing the cached one if update is interrupted
    Presentation presentation = myPresentationFactory.getPresentation(action).clone();
    if (!ActionPlaces.isShortcutPlace(myPlace)) presentation.setEnabledAndVisible(true);
    Supplier<Boolean> doUpdate = () -> doUpdate(action, createActionEvent(presentation));
    boolean success = callAction(action, Op.update, doUpdate);
    return success ? presentation : null;
  }

  void applyPresentationChanges() {
    for (Map.Entry<AnAction, Presentation> entry : myUpdatedPresentations.entrySet()) {
      AnAction action = entry.getKey();
      Presentation orig = myPresentationFactory.getPresentation(action);
      Presentation copy = entry.getValue();
      JComponent customComponent = null;
      if (action instanceof CustomComponentAction) {
        // 1. toolbar may have already created a custom component, do not erase it
        // 2. presentation factory may be just reset, do not reuse component from a copy
        customComponent = orig.getClientProperty(CustomComponentAction.COMPONENT_KEY);
      }
      orig.copyFrom(copy, customComponent, true);
      if (customComponent != null && orig.isVisible()) {
        ((CustomComponentAction)action).updateCustomComponent(customComponent, orig);
      }
    }
  }

  private <T> T callAction(@NotNull AnAction action, @NotNull Op operation, @NotNull Supplier<? extends T> call) {
    String operationName = Utils.operationName(action, operation.name(), myPlace);
    return callAction(action, operationName, action.getActionUpdateThread(), call);
  }

  private <T> T callAction(@NotNull Object action,
                           @NotNull String operationName,
                           @NotNull ActionUpdateThread updateThreadOrig,
                           @NotNull Supplier<? extends T> call) {
    ActionUpdateThread updateThread = myForcedUpdateThread != null ? myForcedUpdateThread : updateThreadOrig;
    boolean canAsync = Utils.isAsyncDataContext(myDataContext);
    boolean shallAsync = updateThread == ActionUpdateThread.BGT;
    boolean isEDT = EDT.isCurrentThreadEdt();
    boolean shallEDT = !(canAsync && shallAsync);
    if (isEDT && !shallEDT && !SlowOperations.isInsideActivity(SlowOperations.ACTION_PERFORM) &&
        !ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.error("Calling on EDT " + operationName + " that requires " + updateThread +
                (myForcedUpdateThread != null ? " (forced)" : ""));
    }
    if (myAllowPartialExpand) {
      ProgressManager.checkCanceled();
    }
    if (isEDT || !shallEDT) {
      return computeWithSpan(Utils.getTracer(true), isEDT ? "edt-op" : "bgt-op", span -> {
        span.setAttribute(Utils.OT_OP_KEY, operationName);
        long start = System.nanoTime();
        try (AccessToken ignored = ProhibitAWTEvents.start(operationName)) {
          return call.get();
        }
        finally {
          long elapsed = TimeoutUtil.getDurationMillis(start);
          span.end();
          if (elapsed > 1000) {
            LOG.warn(elapsedReport(elapsed, isEDT, operationName));
          }
        }
      });
    }
    if (PopupMenuPreloader.isToSkipComputeOnEDT(myPlace)) {
      throw new ComputeOnEDTSkipped();
    }
    if (myPreCacheSlowDataKeys && updateThread == ActionUpdateThread.OLD_EDT) {
      ApplicationManagerEx.getApplicationEx().tryRunReadAction(() -> ensureSlowDataKeysPreCached(action, operationName));
    }
    return computeOnEdt(action, operationName, call, updateThread == ActionUpdateThread.EDT);
  }

  /** @noinspection AssignmentToStaticFieldFromInstanceMethod*/
  private <T> T computeOnEdt(@NotNull Object action,
                             @NotNull String operationName,
                             @NotNull Supplier<? extends T> call,
                             boolean noRulesInEDT) {
    myCurEDTWaitMillis = myCurEDTPerformMillis = 0L;
    ProgressIndicator progress = Objects.requireNonNull(ProgressIndicatorProvider.getGlobalProgressIndicator());
    AtomicReference<FList<Throwable>> edtTracesRef = new AtomicReference<>();
    long start0 = System.nanoTime();
    Supplier<? extends T> supplier = () -> {
      {
        long curNanos = System.nanoTime();
        myEDTCallsCount++;
        myEDTWaitNanos += curNanos - start0;
        myCurEDTWaitMillis = TimeUnit.NANOSECONDS.toMillis(curNanos - start0);
      }
      long start = System.nanoTime();
      FList<String> prevStack = ourInEDTActionOperationStack;
      ourInEDTActionOperationStack = prevStack.prepend(operationName);
      return computeWithSpan(Utils.getTracer(true), "edt-op", span -> {
        span.setAttribute(Utils.OT_OP_KEY, operationName);

        try {
          return ProgressManager.getInstance().runProcess(() -> {
            boolean prevNoRules = ourNoRulesInEDTSection;
            try (AccessToken ignored = ProhibitAWTEvents.start(operationName)) {
              ourNoRulesInEDTSection = noRulesInEDT;
              return call.get();
            }
            finally {
              ourNoRulesInEDTSection = prevNoRules;
            }
          }, ProgressWrapper.wrap(progress));
        }
        finally {
          ourInEDTActionOperationStack = prevStack;
          myCurEDTPerformMillis = TimeoutUtil.getDurationMillis(start);
          edtTracesRef.set(ActionUpdateEdtExecutor.ourEDTExecTraces.get());
        }
      });
    };
    try {
      return computeOnEdt(Context.current().wrapSupplier(supplier));
    }
    finally {
      if (myCurEDTWaitMillis > 300) {
        LOG.warn(myCurEDTWaitMillis + " ms to grab EDT for " + operationName);
      }
      if (myCurEDTPerformMillis > 300) {
        Throwable throwable = PluginException.createByClass(
          elapsedReport(myCurEDTPerformMillis, true, operationName) + OLD_EDT_MSG_SUFFIX, null, action.getClass());
        FList<Throwable> edtTraces = edtTracesRef.get();
        // do not report pauses without EDT traces (e.g. due to debugging)
        if (edtTraces != null && edtTraces.size() > 0 && edtTraces.get(0).getStackTrace().length > 0) {
          for (Throwable trace : edtTraces) {
            throwable.addSuppressed(trace);
          }
          LOG.error(throwable);
        }
        else {
          LOG.warn(throwable);
        }
      }
      myCurEDTWaitMillis = myCurEDTPerformMillis = 0L;
    }
  }

  static @Nullable String currentInEDTOperationName() {
    return ourInEDTActionOperationStack.getHead();
  }

  static boolean isNoRulesInEDTSection() {
    return ourNoRulesInEDTSection;
  }

  /**
   * @return actions from the given and nested non-popup groups that are visible after updating
   */
  List<AnAction> expandActionGroup(ActionGroup group, boolean hideDisabled) {
    try {
      return expandActionGroup(group, hideDisabled, myRealUpdateStrategy);
    }
    finally {
      applyPresentationChanges();
    }
  }

  /**
   * @return actions from the given and nested non-popup groups that are visible after updating
   * don't check progress.isCanceled (to obtain full list of actions)
   */
  List<AnAction> expandActionGroupFull(ActionGroup group, boolean hideDisabled) {
    try {
      myAllowPartialExpand = false;
      return expandActionGroup(group, hideDisabled, myRealUpdateStrategy);
    }
    finally {
      myAllowPartialExpand = true;
      applyPresentationChanges();
    }
  }

  private List<AnAction> expandActionGroup(ActionGroup group, boolean hideDisabled, UpdateStrategy strategy) {
    return removeUnnecessarySeparators(doExpandActionGroup(group, hideDisabled, strategy));
  }

  /**
   * @return actions from the given and nested non-popup groups that are visible after updating
   */
  @NotNull
  List<AnAction> expandActionGroupWithTimeout(ActionGroup group, boolean hideDisabled) {
    return expandActionGroupWithTimeout(group, hideDisabled, Registry.intValue("actionSystem.update.timeout.ms"));
  }

  /**
   * @return actions from the given and nested non-popup groups that are visible after updating
   */
  @NotNull
  List<AnAction> expandActionGroupWithTimeout(ActionGroup group, boolean hideDisabled, int timeoutMs) {
    List<AnAction> result = ProgressIndicatorUtils.withTimeout(timeoutMs, () -> expandActionGroup(group, hideDisabled));
    try {
      return result != null ? result : expandActionGroup(group, hideDisabled, myCheapStrategy);
    }
    finally {
      applyPresentationChanges();
    }
  }

  @NotNull
  CancellablePromise<List<AnAction>> expandActionGroupAsync(ActionGroup group, boolean hideDisabled) {
    return ActionGroupExpander.getInstance().expandActionGroupAsync(
      myPresentationFactory, myDataContext, myPlace, group, myToolbarAction, hideDisabled, this::doExpandActionGroupAsync);
  }

  @NotNull
  private CancellablePromise<List<AnAction>> doExpandActionGroupAsync(ActionGroup group, boolean hideDisabled) {
    ClientId clientId = ClientId.getCurrent();
    ComponentManager disposableParent = Objects.requireNonNull(
      myProject != null
      ? myProject.isDefault()
        ? myProject
        : ClientSessionsManager.getProjectSession(myProject, clientId)
      : ClientSessionsManager.getAppSession(clientId));


    ProgressIndicator parentIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    ProgressIndicator indicator = parentIndicator == null ? new ProgressIndicatorBase() : new SensitiveProgressWrapper(parentIndicator);

    AsyncPromise<List<AnAction>> promise = newPromise(myPlace);
    promise.onError(__ -> {
      indicator.cancel();
      ApplicationManager.getApplication().invokeLater(
        this::applyPresentationChanges, ModalityState.any(), disposableParent.getDisposed());
    });
    myEDTCallsCount = 0;
    myEDTWaitNanos = 0;
    promise.onProcessed(__ -> {
      long edtWaitMillis = TimeUnit.NANOSECONDS.toMillis(myEDTWaitNanos);
      if (myLaterInvocator == null && (myEDTCallsCount > 500 || edtWaitMillis > 3000)) {
        LOG.warn(edtWaitMillis + " ms total to grab EDT " + myEDTCallsCount + " times to expand " +
                 Utils.operationName(group, null, myPlace) + ". Use `ActionUpdateThread.BGT`.");
      }
    });

    if (myToolbarAction) {
      cancelOnUserActivity(promise, disposableParent);
    }

    Computable<Computable<Void>> computable = () -> {
      indicator.checkCanceled();
      if (myTestDelayMillis > 0) waitTheTestDelay();
      List<AnAction> result = expandActionGroup(group, hideDisabled, myRealUpdateStrategy);
      return () -> { // invoked outside the read-action
        try {
          applyPresentationChanges();
          promise.setResult(result);
        }
        catch (Throwable e) {
          cancelPromise(promise, e);
        }
        return null;
      };
    };
    ourPromises.add(promise);
    boolean isFastTrack = myLaterInvocator != null && SlowOperations.isInsideActivity(SlowOperations.FAST_TRACK);
    Executor executor = isFastTrack ? ourFastTrackExecutor : ourCommonExecutor;
    executor.execute(Context.current().wrap(() -> {
      Ref<Computable<Void>> applyRunnableRef = Ref.create();
      try (AccessToken ignored = ClientId.withClientId(clientId)) {
        BackgroundTaskUtil.runUnderDisposeAwareIndicator(disposableParent, () -> {
          if (tryRunReadActionAndCancelBeforeWrite(promise, () -> applyRunnableRef.set(computable.compute())) &&
              !applyRunnableRef.isNull() && !promise.isDone()) {
            computeOnEdt(applyRunnableRef.get());
          }
          else if (!promise.isDone()) {
            cancelPromise(promise, "read-action unavailable");
          }
        }, indicator);
      }
      catch (Throwable e) {
        if (!promise.isDone()) {
          cancelPromise(promise, e);
        }
      }
      finally {
        ourPromises.remove(promise);
        if (!promise.isDone()) {
          cancelPromise(promise, "unknown reason");
          LOG.error(new Throwable("'" + myPlace + "' update exited incorrectly (" + !applyRunnableRef.isNull() + ")"));
        }
      }
    }));
    return promise;
  }

  boolean tryRunReadActionAndCancelBeforeWrite(@NotNull CancellablePromise<?> promise, @NotNull Runnable runnable) {
    if (promise.isDone()) return false;
    ApplicationEx applicationEx = ApplicationManagerEx.getApplicationEx();
    return ProgressIndicatorUtils.runActionAndCancelBeforeWrite(
      applicationEx,
      () -> cancelPromise(promise, currentInEDTOperationName() == null ? "write-action requested" :
                                   NESTED_WA_REASON_PREFIX + currentInEDTOperationName()),
      () -> applicationEx.tryRunReadAction(runnable));
  }

  static void cancelAllUpdates(@NotNull String reason) {
    if (ourPromises.isEmpty()) return;
    CancellablePromise<?>[] copy = ourPromises.toArray(new CancellablePromise[0]);
    ourPromises.clear();
    for (CancellablePromise<?> promise : copy) {
      cancelPromise(promise, reason + " (cancelling all updates)");
    }
  }

  static void waitForAllUpdatesToFinish() {
    try {
      ((BoundedTaskExecutor)ourCommonExecutor).waitAllTasksExecuted(1, TimeUnit.MINUTES);
    }
    catch (Exception e) {
      ExceptionUtil.rethrow(e);
    }
  }

  private void waitTheTestDelay() {
    if (myTestDelayMillis <= 0) return;
    ProgressIndicator progress = Objects.requireNonNull(ProgressIndicatorProvider.getGlobalProgressIndicator());
    long start = System.nanoTime();
    while (true) {
      progress.checkCanceled();
      if (TimeoutUtil.getDurationMillis(start) > myTestDelayMillis) break;
      TimeoutUtil.sleep(1);
    }
  }

  private void ensureSlowDataKeysPreCached(@NotNull Object action, @NotNull String targetOperationName) {
    if (!myPreCacheSlowDataKeys) return;
    String operationName = "precache-slow-data@" + targetOperationName;

    long start = System.nanoTime();
    try {
      runWithSpan(Utils.getTracer(true), "precache-slow-data", span -> {
        span.setAttribute(Utils.OT_OP_KEY, operationName);

        for (DataKey<?> key : DataKey.allKeys()) {
          try {
            myDataContext.getData(key);
          }
          catch (ProcessCanceledException ex) {
            throw ex;
          }
          catch (Throwable ex) {
            LOG.error(ex);
          }
        }
        myPreCacheSlowDataKeys = false;
      });
    }
    finally {
      logTimeProblemForPreCached(action, operationName, TimeoutUtil.getDurationMillis(start));
    }
  }

  private void logTimeProblemForPreCached(@NotNull Object action, String operationName, long elapsed) {
    if (elapsed > 300 && ActionPlaces.isShortcutPlace(myPlace)) {
      LOG.error(PluginException.createByClass(elapsedReport(elapsed, false, operationName) + OLD_EDT_MSG_SUFFIX, null, action.getClass()));
    }
    else if (elapsed > 3000) {
      LOG.warn(elapsedReport(elapsed, false, operationName));
    }
    else if (elapsed > 500 && LOG.isDebugEnabled()) {
      LOG.debug(elapsedReport(elapsed, false, operationName));
    }
  }

  private static void cancelOnUserActivity(@NotNull CancellablePromise<?> promise,
                                           @NotNull Disposable disposableParent) {
    Disposable disposable = Disposer.newDisposable("Action Update");
    Disposer.register(disposableParent, disposable);
    IdeEventQueue.getInstance().addPreprocessor(event -> {
      if (event instanceof KeyEvent && ((KeyEvent)event).getKeyCode() != 0 ||
          event instanceof MouseEvent && event.getID() == MouseEvent.MOUSE_PRESSED) {
        cancelPromise(promise, event);
      }
      return false;
    }, disposable);
    promise.onProcessed(__ -> Disposer.dispose(disposable));
  }

  private List<AnAction> doExpandActionGroup(ActionGroup group, boolean hideDisabled, UpdateStrategy strategy) {
    if (group instanceof ActionGroupStub) {
      throw new IllegalStateException("Trying to expand non-unstubbed group");
    }
    if (myAllowPartialExpand) {
      ProgressManager.checkCanceled();
    }
    ActionUpdateThread prevForceAsync = myForcedUpdateThread;
    myForcedUpdateThread = group instanceof ActionUpdateThreadAware.Recursive ? group.getActionUpdateThread() : prevForceAsync;
    Presentation presentation = update(group, strategy);
    if (presentation == null || !presentation.isVisible()) { // don't process invisible groups
      return Collections.emptyList();
    }

    List<AnAction> children = getGroupChildren(group, strategy);
    List<AnAction> result = ContainerUtil.concat(children, child -> expandGroupChild(child, hideDisabled, strategy));
    myForcedUpdateThread = prevForceAsync;
    return group.postProcessVisibleChildren(result, asUpdateSession(strategy));
  }

  private List<AnAction> getGroupChildren(ActionGroup group, UpdateStrategy strategy) {
    Function<ActionGroup, List<AnAction>> function = __ -> {
      AnAction[] children = strategy.getChildren.fun(group);
      int nullIndex = ArrayUtil.indexOf(children, null);
      if (nullIndex < 0) return Arrays.asList(children);

      LOG.error("action is null: i=" + nullIndex + " group=" + group + " group id=" + ActionManager.getInstance().getId(group));
      return ContainerUtil.filter(children, Conditions.notNull());
    };
    try {
      return myGroupChildren.computeIfAbsent(group, function);
    }
    catch (ComputeOnEDTSkipped ignore) {
    }
    return Collections.emptyList();
  }

  private List<AnAction> expandGroupChild(AnAction child, boolean hideDisabledBase, UpdateStrategy strategy) {
    Application application = ApplicationManager.getApplication();
    if (application == null || application.isDisposed()) {
      return Collections.emptyList();
    }
    Presentation presentation = update(child, strategy);
    if (presentation == null) {
      return Collections.emptyList();
    }
    else if (!presentation.isVisible() || hideDisabledBase && !presentation.isEnabled()) {
      return Collections.emptyList();
    }
    else if (!(child instanceof ActionGroup)) {
      return Collections.singletonList(child);
    }
    ActionGroup group = (ActionGroup)child;

    boolean isPopup = presentation.isPopupGroup();
    boolean canBePerformed = presentation.isPerformGroup();
    boolean performOnly = isPopup && canBePerformed && Boolean.TRUE.equals(presentation.getClientProperty(ActionMenu.SUPPRESS_SUBMENU));
    boolean skipChecks = performOnly || child instanceof AlwaysVisibleActionGroup;
    boolean hideDisabled = isPopup && !skipChecks && hideDisabledBase;
    boolean hideEmpty = isPopup && !skipChecks && (presentation.isHideGroupIfEmpty() || group.hideIfNoVisibleChildren());
    boolean disableEmpty = isPopup && !skipChecks && (presentation.isDisableGroupIfEmpty() && group.disableIfNoVisibleChildren());
    boolean checkChildren = isPopup && !skipChecks && (canBePerformed || hideDisabled || hideEmpty || disableEmpty);

    boolean hasEnabled = false, hasVisible = false;
    if (checkChildren) {
      JBIterable<AnAction> childrenIterable = iterateGroupChildren(group, strategy);
      for (AnAction action : childrenIterable.take(100)) {
        if (action instanceof Separator) continue;
        Presentation p = update(action, strategy);
        if (p == null) continue;
        hasVisible |= p.isVisible();
        hasEnabled |= p.isEnabled();
        // stop early if all the required flags are collected
        if (hasVisible && (hasEnabled || !hideDisabled)) break;
      }
      performOnly = canBePerformed && !hasVisible;
    }
    if (isPopup) {
      presentation.putClientProperty(SUPPRESS_SUBMENU_IMPL, performOnly ? true : null);
      if (!performOnly && !hasVisible && disableEmpty) {
        presentation.setEnabled(false);
      }
    }

    if (!hasEnabled && hideDisabled || !hasVisible && hideEmpty) {
      return canBePerformed ? List.of(group) : Collections.emptyList();
    }
    else if (isPopup) {
      return Collections.singletonList(!hideDisabledBase || child instanceof CompactActionGroup ? group :
                                       new Compact(group));
    }
    else {
      return doExpandActionGroup(group, hideDisabledBase || child instanceof CompactActionGroup, strategy);
    }
  }

  private Presentation orDefault(AnAction action, Presentation presentation) {
    return presentation != null ? presentation : myPresentationFactory.getPresentation(action).clone();
  }

  static @NotNull List<AnAction> removeUnnecessarySeparators(@NotNull List<? extends AnAction> visible) {
    List<AnAction> result = new ArrayList<>();
    for (AnAction child : visible) {
      if (child instanceof Separator &&
          (result.isEmpty() || ContainerUtil.getLastItem(result) instanceof Separator) &&
          StringUtil.isEmpty(((Separator)child).getText())) {
        continue;
      }
      result.add(child);
    }
    return result;
  }

  private AnActionEvent createActionEvent(@NotNull Presentation presentation) {
    AnActionEvent event = new AnActionEvent(
      null, myDataContext, myPlace, presentation,
      ActionManager.getInstance(), 0, myContextMenuAction, myToolbarAction);
    if (myEventTransform != null) {
      event = myEventTransform.apply(event);
    }
    event.setUpdateSession(asUpdateSession());
    return event;
  }

  private <T> T computeOnEdt(@NotNull Supplier<? extends T> supplier) {
    return ActionUpdateEdtExecutor.computeOnEdt(supplier, myLaterInvocator);
  }

  @NotNull
  UpdateSession asUpdateSession() {
    return asUpdateSession(myRealUpdateStrategy);
  }

  @NotNull
  UpdateSession asFastUpdateSession(@Nullable Consumer<? super String> missedKeys,
                                    @Nullable Consumer<? super Runnable> laterInvocator) {
    DataContext frozenContext = Utils.freezeDataContext(myDataContext, missedKeys);
    Consumer<? super Runnable> invocator = Objects.requireNonNull(ObjectUtils.<Consumer<? super Runnable>>coalesce(laterInvocator, myLaterInvocator));
    ActionUpdater updater = new ActionUpdater(myPresentationFactory, frozenContext, myPlace, myContextMenuAction, myToolbarAction,
                                              myEventTransform, invocator);
    updater.myPreCacheSlowDataKeys = false;
    return updater.asUpdateSession();
  }

  private @NotNull UpdateSession asUpdateSession(UpdateStrategy strategy) {
    return new UpdateSessionImpl(this, strategy);
  }

  private @NotNull JBIterable<AnAction> iterateGroupChildren(@NotNull ActionGroup group, @NotNull UpdateStrategy strategy) {
    boolean isDumb = myProject != null && DumbService.getInstance(myProject).isDumb();
    return JBTreeTraverser.<AnAction>from(o -> {
      if (o == group) return null;
      if (isDumb && !o.isDumbAware()) return null;
      if (!(o instanceof ActionGroup)) return null;
      ActionGroup oo = (ActionGroup)o;
      Presentation presentation = update(oo, strategy);
      if (presentation == null || !presentation.isVisible()) {
        return null;
      }
      if (presentation.isPopupGroup() || presentation.isPerformGroup()) {
        return null;
      }
      return getGroupChildren(oo, strategy);
    })
      .withRoots(getGroupChildren(group, strategy))
      .unique()
      .traverse(TreeTraversal.LEAVES_DFS)
      .filter(o -> !isDumb || o.isDumbAware());
  }

  private static @NotNull String elapsedReport(long elapsed, boolean isEDT, @NotNull String operationName) {
    return elapsed + (isEDT ? " ms to call on EDT " : " ms to call on BGT ") + operationName;
  }

  private static void handleException(@NotNull AnAction action, @NotNull Op op, @Nullable AnActionEvent event, @NotNull Throwable ex) {
    if (ex instanceof ProcessCanceledException) throw (ProcessCanceledException)ex;
    String id = ActionManager.getInstance().getId(action);
    String place = event == null ? null : event.getPlace();
    String text = event == null ? null : event.getPresentation().getText();
    String message = Utils.operationName(action, op.name(), place) +
                     (id != null ? ", actionId=" + id : "") +
                     (StringUtil.isNotEmpty(text) ? ", text='" + text + "'" : "");
    LOG.error(message, ex);
  }

  private @Nullable Presentation update(AnAction action, UpdateStrategy strategy) {
    Presentation cached = myUpdatedPresentations.get(action);
    if (cached != null) {
      return cached;
    }
    try {
      Presentation presentation = strategy.update.fun(action);
      if (presentation != null) {
        myUpdatedPresentations.put(action, presentation);
        return presentation;
      }
    }
    catch (ComputeOnEDTSkipped ignore) {
    }
    return null;
  }

  // returns false if exception was thrown and handled
  static boolean doUpdate(@NotNull AnAction action, @NotNull AnActionEvent e) {
    if (ApplicationManager.getApplication().isDisposed()) return false;
    try {
      return !ActionUtil.performDumbAwareUpdate(action, e, false);
    }
    catch (Throwable ex) {
      handleException(action, Op.update, e, ex);
      return false;
    }
  }

  private static AnAction @NotNull [] doGetChildren(@NotNull ActionGroup group, @Nullable AnActionEvent e) {
    if (ApplicationManager.getApplication().isDisposed()) return AnAction.EMPTY_ARRAY;
    try {
      return group.getChildren(e);
    }
    catch (Throwable ex) {
      handleException(group, Op.getChildren, e, ex);
      return AnAction.EMPTY_ARRAY;
    }
  }

  private static final ConcurrentMap<AsyncPromise<?>, String> ourDebugPromisesMap = CollectionFactory.createConcurrentWeakIdentityMap();

  static <T> @NotNull AsyncPromise<T> newPromise(@NotNull String place) {
    AsyncPromise<T> promise = new AsyncPromise<>();
    if (LOG.isDebugEnabled()) {
      ourDebugPromisesMap.put(promise, place);
      promise.onProcessed(__ -> ourDebugPromisesMap.remove(promise));
    }
    return promise;
  }

  static void cancelPromise(@NotNull CancellablePromise<?> promise, @NotNull Object reason) {
    if (LOG.isDebugEnabled()) {
      String place = ourDebugPromisesMap.remove(promise);
      if (place == null && promise.isDone()) return;
      String message = "'" + place + "' update cancelled: " + reason;
      if (reason instanceof String && (message.contains("fast-track") || message.contains("all updates"))) {
        LOG.debug(message);
      }
      else {
        LOG.debug(message, reason instanceof Throwable ? (Throwable)reason : new ProcessCanceledException());
      }
    }
    boolean nestedWA = reason instanceof String && ((String)reason).startsWith(NESTED_WA_REASON_PREFIX);
    if (nestedWA) {
      LOG.error(new AssertionError(((String)reason).substring(NESTED_WA_REASON_PREFIX.length()) + " requests write-action. " +
                                   "An action must not request write-action during actions update. " +
                                   "See CustomComponentAction.createCustomComponent javadoc, if caused by a custom component."));
    }
    if (!nestedWA && promise instanceof AsyncPromise) {
      ((AsyncPromise<?>)promise).setError(reason instanceof Throwable ? (Throwable)reason : new Utils.ProcessCanceledWithReasonException(reason));
    }
    else {
      promise.cancel();
    }
  }

  private enum Op { update, getChildren }

  private static class UpdateStrategy {
    final NullableFunction<? super AnAction, Presentation> update;
    final NotNullFunction<? super ActionGroup, ? extends AnAction[]> getChildren;

    UpdateStrategy(NullableFunction<? super AnAction, Presentation> update,
                   NotNullFunction<? super ActionGroup, ? extends AnAction[]> getChildren) {
      this.update = update;
      this.getChildren = getChildren;
    }
  }

  static @NotNull ActionUpdater getActionUpdater(@NotNull UpdateSession session) {
    return ((UpdateSessionImpl)session).updater;
  }

  private static class UpdateSessionImpl implements UpdateSession {
    final ActionUpdater updater;
    final UpdateStrategy strategy;

    UpdateSessionImpl(ActionUpdater updater, UpdateStrategy strategy) {
      this.updater = updater;
      this.strategy = strategy;
    }

    @Override
    public @NotNull Iterable<? extends AnAction> expandedChildren(@NotNull ActionGroup actionGroup) {
      return updater.iterateGroupChildren(actionGroup, strategy);
    }

    @Override
    public @NotNull List<? extends AnAction> children(@NotNull ActionGroup actionGroup) {
      return updater.getGroupChildren(actionGroup, strategy);
    }

    @Override
    public @NotNull Presentation presentation(@NotNull AnAction action) {
      return updater.orDefault(action, updater.update(action, strategy));
    }

    @Override
    public <T> @NotNull T sharedData(@NotNull Key<T> key, @NotNull Supplier<? extends T> provider) {
      T existing = updater.myUserDataHolder.getUserData(key);
      return existing != null ? existing :
             updater.myUserDataHolder.putUserDataIfAbsent(key, provider.get());
    }

    @Override
    public <T> T compute(@NotNull Object action,
                         @NotNull String operationName,
                         @NotNull ActionUpdateThread updateThread,
                         @NotNull Supplier<? extends T> supplier) {
      String operationNameFull = Utils.operationName(action, operationName, updater.myPlace);
      return updater.callAction(action, operationNameFull, updateThread, supplier);
    }
  }

  private static class Compact extends ActionGroupWrapper implements CompactActionGroup {

    Compact(@NotNull ActionGroup action) {
      super(action);
    }
  }

  private static class ComputeOnEDTSkipped extends ProcessCanceledException {
  }
}
