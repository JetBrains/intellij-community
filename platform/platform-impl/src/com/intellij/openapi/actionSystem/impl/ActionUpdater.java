// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ProhibitAWTEvents;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.NullableFunction;
import com.intellij.util.TimeoutUtil;
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
import org.jetbrains.concurrency.Promise;

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
  private final PresentationFactory myFactory;
  private final DataContext myDataContext;
  private final String myPlace;
  private final boolean myContextMenuAction;
  private final boolean myToolbarAction;
  private final Project myProject;

  private final Map<AnAction, Presentation> myUpdatedPresentations = new ConcurrentHashMap<>();
  private final Map<ActionGroup, List<AnAction>> myGroupChildren = new ConcurrentHashMap<>();
  private final Map<ActionGroup, Boolean> myCanBePerformedCache = new ConcurrentHashMap<>();
  private final UpdateStrategy myRealUpdateStrategy;
  private final UpdateStrategy myCheapStrategy;
  private final Utils.ActionGroupVisitor myVisitor;

  private boolean myAllowPartialExpand = true;
  private boolean myPreCacheSlowDataKeys;
  private final Function<AnActionEvent, AnActionEvent> myEventTransform;
  private final Consumer<Runnable> myLaterInvocator;

  ActionUpdater(boolean isInModalContext,
                PresentationFactory presentationFactory,
                DataContext dataContext,
                String place,
                boolean isContextMenuAction, boolean isToolbarAction) {
    this(isInModalContext, presentationFactory, dataContext, place, isContextMenuAction, isToolbarAction, null);
  }

  ActionUpdater(boolean isInModalContext,
                PresentationFactory presentationFactory,
                DataContext dataContext,
                String place,
                boolean isContextMenuAction,
                boolean isToolbarAction,
                Utils.ActionGroupVisitor visitor) {
    this(isInModalContext, presentationFactory, dataContext, place, isContextMenuAction, isToolbarAction, visitor, null, null);
  }

  ActionUpdater(boolean isInModalContext,
                PresentationFactory presentationFactory,
                DataContext dataContext,
                String place,
                boolean isContextMenuAction,
                boolean isToolbarAction,
                @Nullable Utils.ActionGroupVisitor visitor,
                @Nullable Function<AnActionEvent, AnActionEvent> eventTransform,
                @Nullable Consumer<Runnable> laterInvocator) {
    myProject = CommonDataKeys.PROJECT.getData(dataContext);
    myModalContext = isInModalContext;
    myFactory = presentationFactory;
    myDataContext = dataContext;
    myVisitor = visitor;
    myPlace = place;
    myContextMenuAction = isContextMenuAction;
    myToolbarAction = isToolbarAction;
    myEventTransform = eventTransform;
    myLaterInvocator = laterInvocator;
    myPreCacheSlowDataKeys = Utils.isAsyncDataContext(dataContext);
    myRealUpdateStrategy = new UpdateStrategy(
      action -> updateActionReal(action, Op.update),
      group -> callAction(group, Op.getChildren, () -> group.getChildren(createActionEvent(group, orDefault(group, myUpdatedPresentations.get(group))))),
      group -> callAction(group, Op.canBePerformed, () -> group.canBePerformed(getDataContext(group))));
    myCheapStrategy = new UpdateStrategy(myFactory::getPresentation, group -> group.getChildren(null), group -> true);
  }

  @Nullable
  private Presentation updateActionReal(@NotNull AnAction action, @NotNull Op operation) {
    if (myPreCacheSlowDataKeys) ReadAction.run(this::ensureSlowDataKeysPreCached);
    // clone the presentation to avoid partially changing the cached one if update is interrupted
    Presentation presentation = computeOnEdt(() -> myFactory.getPresentation(action).clone());
    boolean isBeforePerformed = operation == Op.beforeActionPerformedUpdate;
    if (!isBeforePerformed) presentation.setEnabledAndVisible(true); // todo investigate and remove this line
    Supplier<Boolean> doUpdate = () -> doUpdate(myModalContext, action, createActionEvent(action, presentation), myVisitor, isBeforePerformed);
    boolean success = callAction(action, operation, doUpdate);
    return success ? presentation : null;
  }

  void applyPresentationChanges(@NotNull UpdateSession session) {
    ((UpdateSessionImpl)session).updater.applyPresentationChanges();
  }

  private void applyPresentationChanges() {
    for (Map.Entry<AnAction, Presentation> entry : myUpdatedPresentations.entrySet()) {
      AnAction action = entry.getKey();
      Presentation orig = myFactory.getPresentation(action);
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

  private DataContext getDataContext(@NotNull AnAction action) {
    if (myVisitor == null) {
      return myDataContext;
    }
    if (myDataContext instanceof AsyncDataContext) { // it's very expensive to create async-context for each custom component
      return myDataContext;                          // and such actions (with custom components, i.e. buttons from dialogs) updates synchronously now
    }
    if (myDataContext instanceof PreCachedDataContext) {
      return myDataContext;
    }
    final Component component = myVisitor.getCustomComponent(action);
    return component != null ? DataManager.getInstance().getDataContext(component) : myDataContext;
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
    Computable<T> adjustedCall = () -> {
      try (AccessToken ignored = ProhibitAWTEvents.start(operation.name())) {
        return call.get();
      }
    };
    // `CodeInsightAction.beforeActionUpdate` runs `commitAllDocuments`, allow it
    boolean canAsync = Utils.isAsyncDataContext(myDataContext) && operation != Op.beforeActionPerformedUpdate;
    boolean forceAsync = canAsync && Registry.is("actionSystem.update.actions.async.unsafe");
    if (forceAsync ||
        EDT.isCurrentThreadEdt() ||
        canAsync && action instanceof UpdateInBackground && ((UpdateInBackground)action).isUpdateInBackground()) {
      return adjustedCall.get();
    }

    ProgressIndicator progress = Objects.requireNonNull(ProgressManager.getInstance().getProgressIndicator());

    return computeOnEdt(() -> {
      long start = System.currentTimeMillis();
      try {
        return ProgressManager.getInstance().runProcess(adjustedCall, ProgressWrapper.wrap(progress));
      }
      finally {
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > 100) {
          LOG.warn("Slow (" + elapsed + "ms) '" + operation + "' on action " + action + " of " + action.getClass() +
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
    if (myVisitor != null) {
      myVisitor.begin();
    }
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
    AsyncPromise<List<AnAction>> promise = new AsyncPromise<>();
    ProgressIndicator indicator = new EmptyProgressIndicator();
    promise.onError(__ -> {
      indicator.cancel();
      computeOnEdt(() -> {
        applyPresentationChanges();
        return null;
      });
    });

    if (myToolbarAction) {
      cancelAndRestartOnUserActivity(promise);
    }
    else if (myContextMenuAction) {
      cancelAllUpdates();
    }
    ourPromises.add(promise);

    ourExecutor.execute(() -> ProgressManager.getInstance().executeProcessUnderProgress(() -> {
      while (promise.getState() == Promise.State.PENDING) {
        try {
          indicator.checkCanceled();
          boolean success = ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(() -> {
            ensureSlowDataKeysPreCached();
            List<AnAction> result = expandActionGroup(group, hideDisabled, myRealUpdateStrategy);
            computeOnEdt(() -> {
              applyPresentationChanges();
              promise.setResult(result);
              return null;
            });
          }, new SensitiveProgressWrapper(indicator));
          if (!success) {
            ProgressIndicatorUtils.yieldToPendingWriteActions();
          }
        }
        catch (Throwable e) {
          promise.setError(e);
        }
      }
      ourPromises.remove(promise);
    }, indicator));
    return promise;
  }

  static void cancelAllUpdates() {
    ArrayList<CancellablePromise<?>> copy = new ArrayList<>(ourPromises);
    ourPromises.clear();
    for (CancellablePromise<?> promise : copy) {
      promise.cancel();
    }
  }

  private void ensureSlowDataKeysPreCached() {
    if (!myPreCacheSlowDataKeys) return;
    long start = System.currentTimeMillis();
    for (DataKey<?> key : DataKey.allKeys()) {
      myDataContext.getData(key);
    }
    // pre-cache injected data, if injected editor is present
    if (myDataContext.getData(AnActionEvent.injectedId(CommonDataKeys.EDITOR.getName())) instanceof EditorWindow) {
      for (DataKey<?> key : DataKey.allKeys()) {
        myDataContext.getData(AnActionEvent.injectedId(key.getName()));
      }
    }
    myPreCacheSlowDataKeys = false;
    long time = System.currentTimeMillis() - start;
    if (time > 500) {
      LOG.debug("ensureAsyncDataKeysPreCached() took: " + time + " ms");
    }
  }

  private static void cancelAndRestartOnUserActivity(@NotNull CancellablePromise<?> promise) {
    Disposable disposable = Disposer.newDisposable("Action Update");
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
    if (myVisitor != null && !myVisitor.enterNode(group)) {
      return Collections.emptyList();
    }

    try {
      Presentation presentation = update(group, strategy);
      if (presentation == null || !presentation.isVisible()) { // don't process invisible groups
        return Collections.emptyList();
      }

      List<AnAction> children = getGroupChildren(group, strategy);
      List<AnAction> result = ContainerUtil.concat(children, child -> TimeoutUtil.compute(
        () -> expandGroupChild(child, hideDisabled, strategy),
        1000, ms -> LOG.warn(ms + " ms to expand group child " + ActionManager.getInstance().getId(child))));
      return group.afterExpandGroup(result, asUpdateSession(strategy));
    }
    finally {
      if (myVisitor != null) {
        myVisitor.leaveNode();
      }
    }
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
      JBIterable<AnAction> childrenIterable = iterateGroupChildren(actionGroup, strategy);
      if (!presentation.isVisible() || (!presentation.isEnabled() && hideDisabled)) {
        return Collections.emptyList();
      }

      boolean isPopup = actionGroup.isPopup(myPlace);
      boolean hasEnabled = false, hasVisible = false;
      if (hideDisabled || isPopup) {
        for (AnAction action : childrenIterable) {
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

        if (myVisitor != null) {
          myVisitor.visitLeaf(child);
        }
        if (hideDisabled && !(child instanceof CompactActionGroup)) {
          return Collections.singletonList(new EmptyAction.DelegatingCompactActionGroup((ActionGroup)child));
        }
        return Collections.singletonList(child);
      }

      return doExpandActionGroup((ActionGroup)child, hideDisabled || actionGroup instanceof CompactActionGroup, strategy);
    }

    if (myVisitor != null) {
      myVisitor.visitLeaf(child);
    }
    return Collections.singletonList(child);
  }

  private boolean canBePerformed(ActionGroup group, UpdateStrategy strategy) {
    return myCanBePerformedCache.computeIfAbsent(group, __ -> strategy.canBePerformed.test(group));
  }

  private Presentation orDefault(AnAction action, Presentation presentation) {
    return presentation != null ? presentation : computeOnEdt(() -> myFactory.getPresentation(action));
  }

  private static List<AnAction> removeUnnecessarySeparators(List<? extends AnAction> visible) {
    List<AnAction> result = new ArrayList<>();
    for (AnAction child : visible) {
      if (child instanceof Separator) {
        if (!StringUtil.isEmpty(((Separator)child).getText()) || (!result.isEmpty() && !(result.get(result.size() - 1) instanceof Separator))) {
          result.add(child);
        }
      } else {
        result.add(child);
      }
    }
    return result;
  }

  private AnActionEvent createActionEvent(AnAction action, Presentation presentation) {
    AnActionEvent event = new AnActionEvent(
      null, getDataContext(action), myPlace, presentation,
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
  UpdateSession asBeforeActionPerformedUpdateSession(@Nullable Consumer<? super String> missedKeysFast) {
    DataContext frozenContext = missedKeysFast != null ? Utils.freezeDataContext(myDataContext, missedKeysFast) : null;
    ActionUpdater updater;
    if (frozenContext == null || frozenContext == myDataContext) {
      updater = this;
    }
    else {
      updater = new ActionUpdater(myModalContext, myFactory, frozenContext, myPlace, myContextMenuAction, myToolbarAction,
                                  myVisitor, myEventTransform, myLaterInvocator);
      updater.myPreCacheSlowDataKeys = false;
    }
    return updater.asUpdateSession(new UpdateStrategy(
      action -> updater.updateActionReal(action, Op.beforeActionPerformedUpdate),
      updater.myRealUpdateStrategy.getChildren,
      updater.myRealUpdateStrategy.canBePerformed));
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
      if (o instanceof AlwaysVisibleActionGroup) return null;
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
      .filter(o -> !(o instanceof Separator) && !(isDumb && !o.isDumbAware()))
      .take(1000);
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
                          Utils.ActionGroupVisitor visitor,
                          boolean beforeActionPerformed) {
    if (ApplicationManager.getApplication().isDisposed()) return false;

    if (visitor != null && !visitor.beginUpdate(action, e)) {
      return true;
    }

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
    finally {
      if (visitor != null) {
        visitor.endUpdate(action);
      }
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
