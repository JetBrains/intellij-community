// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.codeWithMe.ClientId;
import com.intellij.concurrency.SensitiveProgressWrapper;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.CancellablePromise;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class ActionUpdater {
  private static final Logger LOG = Logger.getInstance(ActionUpdater.class);

  static final Key<Boolean> SUPPRESS_SUBMENU_IMPL = Key.create("SUPPRESS_SUBMENU_IMPL");

  static final Executor ourBeforePerformedExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Action Updater (Exclusive)", 1);
  private static final Executor ourExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Action Updater (Common)", 2);
  private static final List<CancellablePromise<?>> ourPromises = new CopyOnWriteArrayList<>();

  private final boolean myModalContext;
  private final PresentationFactory myPresentationFactory;
  private final DataContext myDataContext;
  private final String myPlace;
  private final boolean myContextMenuAction;
  private final boolean myToolbarAction;
  private final @Nullable Project myProject;

  private final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();
  private final Map<AnAction, Presentation> myUpdatedPresentations = new ConcurrentHashMap<>();
  private final Map<ActionGroup, List<AnAction>> myGroupChildren = new ConcurrentHashMap<>();
  private final Map<ActionGroup, Boolean> myCanBePerformedCache = new ConcurrentHashMap<>();
  private final UpdateStrategy myRealUpdateStrategy;
  private final UpdateStrategy myCheapStrategy;

  private boolean myAllowPartialExpand = true;
  private boolean myPreCacheSlowDataKeys;
  private boolean myForceAsync;
  private String myInEDTActionOperation;
  private final Function<AnActionEvent, AnActionEvent> myEventTransform;
  private final Consumer<Runnable> myLaterInvocator;
  private final int myTestDelayMillis;

  private int myEDTCallsCount;
  private long myEDTWaitNanos;

  ActionUpdater(boolean isInModalContext,
                @NotNull PresentationFactory presentationFactory,
                @NotNull DataContext dataContext,
                @NotNull String place,
                boolean isContextMenuAction,
                boolean isToolbarAction) {
    this(isInModalContext, presentationFactory, dataContext, place, isContextMenuAction, isToolbarAction, null, null);
  }

  ActionUpdater(boolean isInModalContext,
                @NotNull PresentationFactory presentationFactory,
                @NotNull DataContext dataContext,
                @NotNull String place,
                boolean isContextMenuAction,
                boolean isToolbarAction,
                @Nullable Function<AnActionEvent, AnActionEvent> eventTransform,
                @Nullable Consumer<Runnable> laterInvocator) {
    myProject = CommonDataKeys.PROJECT.getData(dataContext);
    myModalContext = isInModalContext;
    myPresentationFactory = presentationFactory;
    myDataContext = dataContext;
    myPlace = place;
    myContextMenuAction = isContextMenuAction;
    myToolbarAction = isToolbarAction;
    myEventTransform = eventTransform;
    myLaterInvocator = laterInvocator;
    myPreCacheSlowDataKeys = Utils.isAsyncDataContext(dataContext) && !Registry.is("actionSystem.update.actions.suppress.dataRules.on.edt");
    myForceAsync = Registry.is("actionSystem.update.actions.async.unsafe");
    myRealUpdateStrategy = new UpdateStrategy(
      action -> updateActionReal(action),
      group -> callAction(group, Op.getChildren, () -> doGetChildren(group, createActionEvent(orDefault(group, myUpdatedPresentations.get(group))))),
      group -> callAction(group, Op.canBePerformed, () -> doCanBePerformed(group, myDataContext)));
    myCheapStrategy = new UpdateStrategy(myPresentationFactory::getPresentation, group -> doGetChildren(group, null), group -> true);

    myTestDelayMillis = ActionPlaces.ACTION_SEARCH.equals(myPlace) || ActionPlaces.isShortcutPlace(myPlace) ?
                        0 : Registry.intValue("actionSystem.update.actions.async.test.delay", 0);
  }

  private @Nullable Presentation updateActionReal(@NotNull AnAction action) {
    // clone the presentation to avoid partially changing the cached one if update is interrupted
    Presentation presentation = myPresentationFactory.getPresentation(action).clone();
    if (!ActionPlaces.isShortcutPlace(myPlace)) presentation.setEnabledAndVisible(true);
    boolean wasPopup = action instanceof ActionGroup && ((ActionGroup)action).isPopup(myPlace);
    presentation.setPopupGroup(action instanceof ActionGroup && (presentation.isPopupGroup() || wasPopup));
    Supplier<Boolean> doUpdate = () -> doUpdate(myModalContext, action, createActionEvent(presentation));
    boolean success = callAction(action, Op.update, doUpdate);
    if (success) assertActionGroupPopupStateIsNotChanged(action, myPlace, wasPopup, presentation);
    return success ? presentation : null;
  }

  static void assertActionGroupPopupStateIsNotChanged(@NotNull AnAction action, @NotNull String place,
                                                      boolean wasPopup, @NotNull Presentation presentation) {
    if (action instanceof ActionGroup && wasPopup != ((ActionGroup)action).isPopup(place)) {
      presentation.setPopupGroup(!wasPopup); // keep the old logic for a while
      String operationName = action.getClass().getSimpleName() + "#" + Op.update + " (" + action.getClass().getName() + ")";
      LOG.warn("Calling `setPopup()` in " + operationName + ". " +
               "Please use `event.getPresentation().setPopupGroup()` instead.");
    }
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
    String operationName = action.getClass().getSimpleName() + "#" + operation + " (" + action.getClass().getName() + ")";
    // `CodeInsightAction.beforeActionUpdate` runs `commitAllDocuments`, allow it
    boolean canAsync = Utils.isAsyncDataContext(myDataContext) && operation != Op.beforeActionPerformedUpdate;
    boolean shallAsync = myForceAsync || canAsync && UpdateInBackground.isUpdateInBackground(action);
    boolean isEDT = EDT.isCurrentThreadEdt();
    boolean shallEDT = !(canAsync && shallAsync);
    if (isEDT && !shallEDT && !SlowOperations.isInsideActivity(SlowOperations.ACTION_PERFORM)) {
      LOG.error("Calling on EDT " + operationName + (myForceAsync ? "(forceAsync=true)" : "(isUpdateInBackground=true)"));
    }
    if (myPreCacheSlowDataKeys && !isEDT &&
        (shallEDT || Registry.is("actionSystem.update.actions.call.preCacheSlowData.always", false))) {
      ApplicationManagerEx.getApplicationEx().tryRunReadAction(this::ensureSlowDataKeysPreCached);
    }
    if (myAllowPartialExpand) {
      ProgressManager.checkCanceled();
    }

    long start0 = System.nanoTime();
    if (isEDT || !shallEDT) {
      try (AccessToken ignored = ProhibitAWTEvents.start(operation.name())) {
        return call.get();
      }
      finally {
        long elapsed = TimeoutUtil.getDurationMillis(start0);
        if (elapsed > 1000) {
          LOG.warn(elapsed + " ms to call on BGT " + operationName);
        }
      }
    }

    ProgressIndicator progress = Objects.requireNonNull(ProgressIndicatorProvider.getGlobalProgressIndicator());
    return computeOnEdt(() -> {
      {
        long curNanos = System.nanoTime();
        myEDTCallsCount++;
        myEDTWaitNanos += curNanos - start0;
        long elapsed = TimeUnit.NANOSECONDS.toMillis(curNanos - start0);
        if (elapsed > 200) {
          LOG.warn(elapsed + " ms to grab EDT for " + operationName);
        }
      }
      long start = System.nanoTime();
      myInEDTActionOperation = operationName;
      try {
        return ProgressManager.getInstance().runProcess(() -> {
          try (AccessToken ignored = ProhibitAWTEvents.start(operation.name())) {
            return call.get();
          }
        }, ProgressWrapper.wrap(progress));
      }
      finally {
        myInEDTActionOperation = null;
        long elapsed = TimeoutUtil.getDurationMillis(start);
        if (elapsed > 100) {
          LOG.warn(elapsed + " ms to call on EDT " + operationName + " - speed it up and/or implement UpdateInBackground.");
        }
      }
    });
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
    ComponentManager disposableParent = myProject != null ? myProject : ApplicationManager.getApplication();
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
        boolean noFqn = group.getClass() == DefaultActionGroup.class;
        LOG.warn(edtWaitMillis + " ms total to grab EDT " + myEDTCallsCount + " times at '" + myPlace + "' to expand " +
                 group.getClass().getSimpleName() + (noFqn ? "" : " (" + group.getClass().getName() + ")") + " - use UpdateInBackground.");
      }
    });

    if (myLaterInvocator != null && SlowOperations.isInsideActivity(SlowOperations.FAST_TRACK)) {
      cancelAllUpdates("fast-track requested by '" + myPlace + "'");
    }
    if (myToolbarAction) {
      cancelOnUserActivity(promise, disposableParent);
    }
    else if (myContextMenuAction) {
      cancelAllUpdates("context menu requested");
    }

    Computable<Computable<Void>> computable = () -> {
      indicator.checkCanceled();
      if (Registry.is("actionSystem.update.actions.call.preCacheSlowData.always", false)) {
        ensureSlowDataKeysPreCached();
      }
      if (myTestDelayMillis > 0) waitTheTestDelay();
      List<AnAction> result = expandActionGroup(group, hideDisabled, myRealUpdateStrategy);
      return () -> { // invoked outside the read-action
        try {
          applyPresentationChanges();
          promise.setResult(result);
        }
        catch (Throwable e) {
          promise.setError(e);
        }
        return null;
      };
    };
    ourPromises.add(promise);
    ClientId clientId = ClientId.getCurrent();
    ourExecutor.execute(() -> {
      Ref<Computable<Void>> applyRunnableRef = Ref.create();
      try (AccessToken ignored = ClientId.withClientId(clientId)) {
        ApplicationEx applicationEx = ApplicationManagerEx.getApplicationEx();
        BackgroundTaskUtil.runUnderDisposeAwareIndicator(disposableParent, () -> {
          if (ProgressIndicatorUtils.runActionAndCancelBeforeWrite(
            applicationEx,
            () -> cancelPromise(promise, myInEDTActionOperation == null ? "write-action requested" :
                                         "nested write-action requested by " + myInEDTActionOperation),
            () -> applicationEx.tryRunReadAction(() -> applyRunnableRef.set(computable.compute()))) &&
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
          promise.setError(e);
        }
      }
      finally {
        ourPromises.remove(promise);
        if (!promise.isDone()) {
          cancelPromise(promise, "unknown reason");
          LOG.error(new Throwable("'" + myPlace + "' update exited incorrectly (" + !applyRunnableRef.isNull() + ")"));
        }
      }
    });
    return promise;
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
      ((BoundedTaskExecutor)ourExecutor).waitAllTasksExecuted(1, TimeUnit.MINUTES);
    }
    catch (Exception e) {
      ExceptionUtil.rethrow(e);
    }
  }

  private void waitTheTestDelay() {
    if (myTestDelayMillis <= 0) return;
    ProgressIndicator progress = Objects.requireNonNull(ProgressIndicatorProvider.getGlobalProgressIndicator());
    long start = System.currentTimeMillis();
    while (true) {
      progress.checkCanceled();
      if (System.currentTimeMillis() - start > myTestDelayMillis) break;
      TimeoutUtil.sleep(1);
    }
  }

  private void ensureSlowDataKeysPreCached() {
    if (!myPreCacheSlowDataKeys) return;
    long start = System.currentTimeMillis();
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
    long time = System.currentTimeMillis() - start;
    if (time > 500) {
      LOG.debug("ensureSlowDataKeysPreCached() took: " + time + " ms");
    }
  }

  private static void cancelOnUserActivity(@NotNull CancellablePromise<?> promise,
                                           @NotNull Disposable disposableParent) {
    Disposable disposable = Disposer.newDisposable("Action Update");
    Disposer.register(disposableParent, disposable);
    IdeEventQueue.getInstance().addPostprocessor(event -> {
      if (event instanceof KeyEvent && event.getID() == KeyEvent.KEY_PRESSED ||
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
    boolean prevForceAsync = myForceAsync;
    myForceAsync |= group instanceof UpdateInBackground.Recursive;
    Presentation presentation = update(group, strategy);
    if (presentation == null || !presentation.isVisible()) { // don't process invisible groups
      return Collections.emptyList();
    }

    List<AnAction> children = getGroupChildren(group, strategy);
    List<AnAction> result = ContainerUtil.concat(children, child -> expandGroupChild(child, hideDisabled, strategy));
    myForceAsync = prevForceAsync;
    return group.postProcessVisibleChildren(result, asUpdateSession(strategy));
  }

  private List<AnAction> getGroupChildren(ActionGroup group, UpdateStrategy strategy) {
    return myGroupChildren.computeIfAbsent(group, __ -> {
      AnAction[] children = strategy.getChildren.fun(group);
      int nullIndex = ArrayUtil.indexOf(children, null);
      if (nullIndex < 0) return Arrays.asList(children);

      LOG.error("action is null: i=" + nullIndex + " group=" + group + " group id=" + ActionManager.getInstance().getId(group));
      return ContainerUtil.filter(children, Conditions.notNull());
    });
  }

  private List<AnAction> expandGroupChild(AnAction child, boolean hideDisabled, UpdateStrategy strategy) {
    Application application = ApplicationManager.getApplication();
    if (application == null || application.isDisposed()) {
      return Collections.emptyList();
    }
    Presentation presentation = update(child, strategy);
    if (presentation == null) {
      return Collections.emptyList();
    }

    if (!presentation.isVisible() || (!presentation.isEnabled() && hideDisabled)) { // don't create invisible items in the menu
      return Collections.emptyList();
    }
    if (child instanceof ActionGroup) {
      ActionGroup actionGroup = (ActionGroup)child;

      boolean isPopup = presentation.isPopupGroup();
      boolean hasEnabled = false, hasVisible = false;

      if (child instanceof AlwaysVisibleActionGroup) {
        hasEnabled = hasVisible = true;
      }
      else if (hideDisabled || isPopup) {
        JBIterable<AnAction> childrenIterable = iterateGroupChildren(actionGroup, strategy);
        for (AnAction action : childrenIterable.take(100)) {
          if (action instanceof Separator) continue;
          Presentation p = update(action, strategy);
          if (p == null) continue;
          hasVisible |= p.isVisible();
          hasEnabled |= p.isEnabled();
          // stop early if all the required flags are collected
          if (hasEnabled && hasVisible) break;
          if (hideDisabled && hasEnabled && !isPopup) break;
          if (isPopup && hasVisible && !hideDisabled) break;
        }
      }

      if (hideDisabled && !hasEnabled) {
        return Collections.emptyList();
      }
      if (isPopup) {
        boolean canBePerformed = presentation.isPerformGroup();
        boolean performOnly = canBePerformed && (
          !hasVisible || Boolean.TRUE.equals(presentation.getClientProperty(ActionMenu.SUPPRESS_SUBMENU)) ||
          actionGroup instanceof AlwaysPerformingActionGroup);
        presentation.putClientProperty(SUPPRESS_SUBMENU_IMPL, performOnly ? true : null);

        if (!hasVisible && actionGroup.disableIfNoVisibleChildren()) {
          if (actionGroup.hideIfNoVisibleChildren()) {
            return Collections.emptyList();
          }
          if (!canBePerformed) {
            presentation.setEnabled(false);
          }
        }

        if (hideDisabled && !(child instanceof CompactActionGroup)) {
          return Collections.singletonList(new EmptyAction.DelegatingCompactActionGroup((ActionGroup)child));
        }
        return Collections.singletonList(child);
      }

      return doExpandActionGroup((ActionGroup)child, hideDisabled || actionGroup instanceof CompactActionGroup, strategy);
    }

    return Collections.singletonList(child);
  }

  private boolean canBePerformed(ActionGroup group, UpdateStrategy strategy) {
    return myCanBePerformedCache.computeIfAbsent(group, __ -> strategy.canBePerformed.test(group));
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
                                    @Nullable Consumer<Runnable> laterInvocator) {
    DataContext frozenContext = Utils.freezeDataContext(myDataContext, missedKeys);
    ActionUpdater updater = new ActionUpdater(myModalContext, myPresentationFactory, frozenContext, myPlace, myContextMenuAction, myToolbarAction,
                                              myEventTransform, Objects.requireNonNull(ObjectUtils.coalesce(laterInvocator, myLaterInvocator)));
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
      if (presentation.isPopupGroup() || presentation.isPerformGroup() ||
          strategy.canBePerformed.test(oo)) {
        return null;
      }
      return getGroupChildren(oo, strategy);
    })
      .withRoots(getGroupChildren(group, strategy))
      .unique()
      .traverse(TreeTraversal.LEAVES_DFS)
      .filter(o -> !isDumb || o.isDumbAware());
  }

  private static void handleException(@NotNull Op op, @NotNull AnAction action, @Nullable AnActionEvent event, @NotNull Throwable ex) {
    if (ex instanceof ProcessCanceledException) throw (ProcessCanceledException)ex;
    String id = ActionManager.getInstance().getId(action);
    String text = event == null ? null : event.getPresentation().getText();
    String message = op.name() + " failed for " + (action instanceof ActionGroup ? "ActionGroup" : "AnAction") +
                     "(" + action.getClass().getName() + (id != null ? ", id=" + id : "") + ")" +
                     (StringUtil.isNotEmpty(text) ? " with text=" + event.getPresentation().getText() : "");
    LOG.error(message, ex);
  }

  private @Nullable Presentation update(AnAction action, UpdateStrategy strategy) {
    Presentation cached = myUpdatedPresentations.get(action);
    if (cached != null) {
      return cached;
    }

    Presentation presentation = strategy.update.fun(action);
    if (presentation != null) {
      presentation.setPerformGroup(
        action instanceof ActionGroup && presentation.isPopupGroup() &&
        (presentation.isPerformGroup() || canBePerformed((ActionGroup)action, strategy)));
      myUpdatedPresentations.put(action, presentation);
    }
    return presentation;
  }

  // returns false if exception was thrown and handled
  static boolean doUpdate(boolean isInModalContext, @NotNull AnAction action, @NotNull AnActionEvent e) {
    if (ApplicationManager.getApplication().isDisposed()) return false;
    try {
      return !ActionUtil.performDumbAwareUpdate(isInModalContext, action, e, false);
    }
    catch (Throwable ex) {
      handleException(Op.update, action, e, ex);
      return false;
    }
  }

  private static AnAction @NotNull [] doGetChildren(@NotNull ActionGroup group, @Nullable AnActionEvent e) {
    try {
      return group.getChildren(e);
    }
    catch (Throwable ex) {
      handleException(Op.getChildren, group, e, ex);
      return AnAction.EMPTY_ARRAY;
    }
  }

  private static boolean doCanBePerformed(@NotNull ActionGroup group, @NotNull DataContext context) {
    try {
      return group.canBePerformed(context);
    }
    catch (Throwable ex) {
      handleException(Op.canBePerformed, group, null, ex);
      return true;
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
      LOG.debug(message, message.contains("fast-track") || message.contains("all updates") ? null : new ProcessCanceledException());
    }
    boolean nestedWA = reason instanceof String && ((String)reason).startsWith("nested write-action");
    if (nestedWA) {
      LOG.error(new AssertionError("An action must not request write-action during actions update. " +
                                   "See CustomComponentAction.createCustomComponent javadoc, if caused by a custom component."));
    }
    if (!nestedWA && promise instanceof AsyncPromise) {
      ((AsyncPromise<?>)promise).setError(new Utils.ProcessCanceledWithReasonException(reason));
    }
    else {
      promise.cancel();
    }
  }

  private enum Op { update, beforeActionPerformedUpdate, getChildren, canBePerformed }

  private static class UpdateStrategy {
    final NullableFunction<? super AnAction, Presentation> update;
    final NotNullFunction<? super ActionGroup, ? extends AnAction[]> getChildren;
    final Predicate<? super ActionGroup> canBePerformed;

    UpdateStrategy(NullableFunction<? super AnAction, Presentation> update,
                   NotNullFunction<? super ActionGroup, ? extends AnAction[]> getChildren,
                   Predicate<? super ActionGroup> canBePerformed) {
      this.update = update;
      this.getChildren = getChildren;
      this.canBePerformed = canBePerformed;
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
  }
}
