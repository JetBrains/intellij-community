// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ProhibitAWTEvents;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.containers.TreeTraversal;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.CancellablePromise;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.PaintEvent;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class ActionUpdater {
  private static final Logger LOG = Logger.getInstance(ActionUpdater.class);

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

  private final Map<AnAction, Presentation> myUpdatedPresentations = new ConcurrentHashMap<>();
  private final Map<ActionGroup, List<AnAction>> myGroupChildren = new ConcurrentHashMap<>();
  private final Map<ActionGroup, Boolean> myCanBePerformedCache = new ConcurrentHashMap<>();
  private final UpdateStrategy myRealUpdateStrategy;
  private final UpdateStrategy myCheapStrategy;

  private boolean myAllowPartialExpand = true;
  private boolean myPreCacheSlowDataKeys;
  private boolean myForceAsync;
  private final Function<AnActionEvent, AnActionEvent> myEventTransform;
  private final Consumer<Runnable> myLaterInvocator;
  private final int myTestDelayMillis;

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
    myPreCacheSlowDataKeys = Utils.isAsyncDataContext(dataContext);
    myForceAsync = Registry.is("actionSystem.update.actions.async.unsafe");
    myRealUpdateStrategy = new UpdateStrategy(
      action -> updateActionReal(action, myEventTransform == null ? Op.update : Op.beforeActionPerformedUpdate),
      group -> callAction(group, Op.getChildren, () -> group.getChildren(createActionEvent(group, orDefault(group, myUpdatedPresentations.get(group))))),
      group -> callAction(group, Op.canBePerformed, () -> group.canBePerformed(myDataContext)));
    myCheapStrategy = new UpdateStrategy(myPresentationFactory::getPresentation, group -> group.getChildren(null), group -> true);

    LOG.assertTrue(myEventTransform == null || ActionPlaces.isShortcutPlace(myPlace),
                   "beforeActionPerformed requested in '" + myPlace + "'");

    myTestDelayMillis = ActionPlaces.ACTION_SEARCH.equals(myPlace) || ActionPlaces.isShortcutPlace(myPlace) ?
                        0 : Registry.intValue("actionSystem.update.actions.async.test.delay", 0);
  }

  @Nullable
  private Presentation updateActionReal(@NotNull AnAction action, @NotNull Op operation) {
    if (myPreCacheSlowDataKeys) ReadAction.run(this::ensureSlowDataKeysPreCached);
    // clone the presentation to avoid partially changing the cached one if update is interrupted
    Presentation presentation = myPresentationFactory.getPresentation(action).clone();
    boolean isBeforePerformed = operation == Op.beforeActionPerformedUpdate;
    if (!isBeforePerformed) presentation.setEnabledAndVisible(true); // todo investigate and remove this line
    Supplier<Boolean> doUpdate = () -> doUpdate(myModalContext, action, createActionEvent(action, presentation), isBeforePerformed);
    boolean success = callAction(action, operation, doUpdate);
    return success ? presentation : null;
  }

  void applyPresentationChanges() {
    for (Map.Entry<AnAction, Presentation> entry : myUpdatedPresentations.entrySet()) {
      AnAction action = entry.getKey();
      Presentation orig = myPresentationFactory.getPresentation(action);
      Presentation copy = entry.getValue();
      if (action instanceof CustomComponentAction) {
        // toolbar may have already created a custom component, do not erase it
        JComponent copyC = copy.getClientProperty(CustomComponentAction.COMPONENT_KEY);
        JComponent origC = orig.getClientProperty(CustomComponentAction.COMPONENT_KEY);
        if (copyC == null && origC != null) {
          copy.putClientProperty(CustomComponentAction.COMPONENT_KEY, origC);
        }
      }
      orig.copyFrom(copy);
      reflectSubsequentChangesInOriginalPresentation(orig, copy);
    }
  }

  // some actions remember the presentation passed to "update" and modify it later, in hope that menu will change accordingly
  private static void reflectSubsequentChangesInOriginalPresentation(Presentation original, Presentation cloned) {
    cloned.addPropertyChangeListener(e -> {
      if (SwingUtilities.isEventDispatchThread()) {
        original.copyFrom(cloned);
      }
    });
  }

  private <T> T callAction(@NotNull AnAction action, @NotNull Op operation, @NotNull Supplier<? extends T> call) {
    // `CodeInsightAction.beforeActionUpdate` runs `commitAllDocuments`, allow it
    boolean canAsync = Utils.isAsyncDataContext(myDataContext) && operation != Op.beforeActionPerformedUpdate;
    boolean shallAsync = myForceAsync || canAsync && UpdateInBackground.isUpdateInBackground(action);
    boolean isEDT = EDT.isCurrentThreadEdt();
    if (isEDT && canAsync && shallAsync && !SlowOperations.isInsideActivity(SlowOperations.ACTION_PERFORM)) {
      LOG.error("Calling " + operation + " on EDT on `" + action.getClass().getName() + "` " +
                (myForceAsync ? "(forceAsync=true)" : "(isUpdateInBackground=true)"));
    }
    if (isEDT || canAsync && shallAsync) {
      try (AccessToken ignored = ProhibitAWTEvents.start(operation.name())) {
        return call.get();
      }
    }

    ProgressIndicator progress = Objects.requireNonNull(ProgressIndicatorProvider.getGlobalProgressIndicator());
    return computeOnEdt(() -> {
      long start = System.nanoTime();
      try {
        return ProgressManager.getInstance().runProcess(() -> {
          try (AccessToken ignored = ProhibitAWTEvents.start(operation.name())) {
            return call.get();
          }
        }, ProgressWrapper.wrap(progress));
      }
      finally {
        long elapsed = TimeoutUtil.getDurationMillis(start);
        if (elapsed > 100) {
          LOG.warn("Slow (" + elapsed + " ms) '" + operation + "' on action " + action + " of " + action.getClass() +
                   ". Consider speeding it up and/or implementing UpdateInBackground.");
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

    AsyncPromise<List<AnAction>> promise = new AsyncPromise<>();
    ProgressIndicator indicator = new EmptyProgressIndicator();
    promise.onError(__ -> {
      indicator.cancel();
      ApplicationManager.getApplication().invokeLater(
        this::applyPresentationChanges, ModalityState.any(), disposableParent.getDisposed());
    });

    if (myToolbarAction) {
      cancelOnUserActivity(promise, disposableParent);
    }
    else if (myContextMenuAction) {
      cancelAllUpdates();
    }

    Runnable runnable = () -> {
      indicator.checkCanceled();
      ensureSlowDataKeysPreCached();
      if (myTestDelayMillis > 0) waitTheTestDelay();
      List<AnAction> result = expandActionGroup(group, hideDisabled, myRealUpdateStrategy);
      computeOnEdt(() -> {
        applyPresentationChanges();
        promise.setResult(result);
        return null;
      });
    };
    ourPromises.add(promise);
    ourExecutor.execute(() -> {
      try {
        boolean[] success = {false};
        ApplicationEx applicationEx = ApplicationManagerEx.getApplicationEx();
        BackgroundTaskUtil.runUnderDisposeAwareIndicator(disposableParent, () ->
          success[0] = ProgressIndicatorUtils.runActionAndCancelBeforeWrite(applicationEx, promise::cancel, () ->
            applicationEx.tryRunReadAction(runnable)), indicator);
        if (!success[0] && !promise.isDone()) {
          promise.cancel();
        }
      }
      catch (Throwable e) {
        promise.setError(e);
      }
      finally {
        ourPromises.remove(promise);
      }
    });
    return promise;
  }

  static void cancelAllUpdates() {
    ArrayList<CancellablePromise<?>> copy = new ArrayList<>(ourPromises);
    ourPromises.clear();
    for (CancellablePromise<?> promise : copy) {
      promise.cancel();
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
      myDataContext.getData(key);
    }
    myPreCacheSlowDataKeys = false;
    long time = System.currentTimeMillis() - start;
    if (time > 500) {
      LOG.debug("ensureAsyncDataKeysPreCached() took: " + time + " ms");
    }
  }

  private static void cancelOnUserActivity(@NotNull CancellablePromise<?> promise,
                                           @NotNull Disposable disposableParent) {
    Disposable disposable = Disposer.newDisposable("Action Update");
    Disposer.register(disposableParent, disposable);
    IdeEventQueue.getInstance().addPostprocessor(e -> {
      if (e instanceof ComponentEvent && !(e instanceof PaintEvent) && (e.getID() & AWTEvent.MOUSE_MOTION_EVENT_MASK) == 0) {
        promise.cancel();
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
    List<AnAction> result = ContainerUtil.concat(children, child -> TimeoutUtil.compute(
      () -> expandGroupChild(child, hideDisabled, strategy),
      1000, ms -> LOG.warn(ms + " ms to expand group child " + ActionManager.getInstance().getId(child))));
    myForceAsync = prevForceAsync;
    return group.postProcessVisibleChildren(result, asUpdateSession(strategy));
  }

  private List<AnAction> getGroupChildren(ActionGroup group, UpdateStrategy strategy) {
    return myGroupChildren.computeIfAbsent(group, __ -> {
      AnAction[] children = TimeoutUtil.compute(
        () -> strategy.getChildren.fun(group),
        1000, ms -> LOG.warn(ms + " ms to expand group child " + ActionManager.getInstance().getId(group)));
      int nullIndex = ArrayUtil.indexOf(children, null);
      if (nullIndex < 0) return Arrays.asList(children);

      LOG.error("action is null: i=" + nullIndex + " group=" + group + " group id=" + ActionManager.getInstance().getId(group));
      return ContainerUtil.filter(children, Conditions.notNull());
    });
  }

  private List<AnAction> expandGroupChild(AnAction child, boolean hideDisabled, UpdateStrategy strategy) {
    Presentation presentation = update(child, strategy);
    if (presentation == null) {
      return Collections.emptyList();
    }

    if (!presentation.isVisible() || (!presentation.isEnabled() && hideDisabled)) { // don't create invisible items in the menu
      return Collections.emptyList();
    }
    if (child instanceof ActionGroup) {
      ActionGroup actionGroup = (ActionGroup)child;

      boolean isPopup = actionGroup.isPopup(myPlace);
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
        boolean canBePerformed = canBePerformed(actionGroup, strategy);
        boolean performOnly = canBePerformed && (actionGroup instanceof AlwaysPerformingActionGroup || !hasVisible);
        presentation.putClientProperty("actionGroup.perform.only", performOnly ? true : null);

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

  private AnActionEvent createActionEvent(AnAction action, Presentation presentation) {
    AnActionEvent event = new AnActionEvent(
      null, myDataContext, myPlace, presentation,
      ActionManager.getInstance(), 0, myContextMenuAction, myToolbarAction);
    if (myEventTransform != null) {
      event = myEventTransform.apply(event);
    }
    event.setInjectedContext(action.isInInjectedContext());
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

  @NotNull
  private UpdateSession asUpdateSession(UpdateStrategy strategy) {
    return new UpdateSessionImpl(this, strategy);
  }

  @NotNull
  private JBIterable<AnAction> iterateGroupChildren(@NotNull ActionGroup group, @NotNull UpdateStrategy strategy) {
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
      if ((oo.isPopup(myPlace) || strategy.canBePerformed.test(oo))) {
        return null;
      }
      return getGroupChildren(oo, strategy);
    })
      .withRoots(getGroupChildren(group, strategy))
      .unique()
      .traverse(TreeTraversal.LEAVES_DFS)
      .filter(o -> !isDumb || o.isDumbAware());
  }

  private static void handleUpdateException(AnAction action, Presentation presentation, Throwable exc) {
    String id = ActionManager.getInstance().getId(action);
    if (id != null) {
      LOG.error("update failed for AnAction(" + action.getClass().getName() + ") with ID=" + id, exc);
    }
    else {
      LOG.error("update failed for ActionGroup: " + action + "[" + presentation.getText() + "]", exc);
    }
  }

  private @Nullable Presentation update(AnAction action, UpdateStrategy strategy) {
    Presentation cached = myUpdatedPresentations.get(action);
    if (cached != null) {
      return cached;
    }

    Presentation presentation = strategy.update.fun(action);
    if (presentation != null) {
      myUpdatedPresentations.put(action, presentation);
    }
    return presentation;
  }

  // returns false if exception was thrown and handled
  static boolean doUpdate(boolean isInModalContext,
                          AnAction action,
                          AnActionEvent e,
                          boolean beforeActionPerformed) {
    if (ApplicationManager.getApplication().isDisposed()) return false;

    long startTime = System.currentTimeMillis();
    final boolean result;
    try {
      result = !ActionUtil.performDumbAwareUpdate(isInModalContext, action, e, beforeActionPerformed);
    }
    catch (ProcessCanceledException ex) {
      throw ex;
    }
    catch (Throwable exc) {
      handleUpdateException(action, e.getPresentation(), exc);
      return false;
    }
    long endTime = System.currentTimeMillis();
    if (endTime - startTime > 10 && LOG.isDebugEnabled()) {
      LOG.debug("Action " + action + ": updated in " + (endTime - startTime) + " ms");
    }
    return result;
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

    @NotNull
    @Override
    public Iterable<? extends AnAction> expandedChildren(@NotNull ActionGroup actionGroup) {
      return updater.iterateGroupChildren(actionGroup, strategy);
    }

    @Override
    public @NotNull List<? extends AnAction> children(@NotNull ActionGroup actionGroup) {
      return updater.getGroupChildren(actionGroup, strategy);
    }

    @NotNull
    @Override
    public Presentation presentation(@NotNull AnAction action) {
      return updater.orDefault(action, updater.update(action, strategy));
    }
  }
}
