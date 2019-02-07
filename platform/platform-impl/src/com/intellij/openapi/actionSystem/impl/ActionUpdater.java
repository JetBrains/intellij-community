// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.NullableFunction;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
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
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.function.Supplier;

class ActionUpdater {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.actionSystem.impl.ActionUpdater");
  private static final ExecutorService ourExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Action Updater", 2);

  private final boolean myModalContext;
  private final PresentationFactory myFactory;
  private final DataContext myDataContext;
  private final String myPlace;
  private final boolean myContextMenuAction;
  private final boolean myToolbarAction;
  private final boolean myTransparentOnly;

  private final Map<AnAction, Presentation> myUpdatedPresentations = ContainerUtil.newConcurrentMap();
  private final Map<ActionGroup, List<AnAction>> myGroupChildren = ContainerUtil.newConcurrentMap();
  private final Map<ActionGroup, Boolean> myCanBePerformedCache = ContainerUtil.newConcurrentMap();
  private final UpdateStrategy myRealUpdateStrategy;
  private final UpdateStrategy myCheapStrategy;

  ActionUpdater(boolean isInModalContext,
                PresentationFactory presentationFactory,
                DataContext dataContext,
                String place,
                boolean isContextMenuAction, boolean isToolbarAction, boolean transparentOnly) {
    myModalContext = isInModalContext;
    myFactory = presentationFactory;
    myDataContext = dataContext;
    myPlace = place;
    myContextMenuAction = isContextMenuAction;
    myToolbarAction = isToolbarAction;
    myTransparentOnly = transparentOnly;
    myRealUpdateStrategy = new UpdateStrategy(
      action -> {
        // clone the presentation to avoid partially changing the cached one if update is interrupted
        Presentation presentation = ActionUpdateEdtExecutor.computeOnEdt(() -> myFactory.getPresentation(action).clone());
        Supplier<Boolean> doUpdate = () -> doUpdate(myModalContext, action, createActionEvent(action, presentation));
        boolean success = callAction(action, "update", doUpdate);
        return success ? presentation : null;
      },
      group -> callAction(group, "getChildren", () -> group.getChildren(createActionEvent(group, orDefault(group, myUpdatedPresentations.get(group))))),
      group -> callAction(group, "canBePerformed", () -> group.canBePerformed(myDataContext)));
    myCheapStrategy = new UpdateStrategy(myFactory::getPresentation, group -> group.getChildren(null), group -> true);
  }

  private void applyPresentationChanges() {
    for (Map.Entry<AnAction, Presentation> entry : myUpdatedPresentations.entrySet()) {
      Presentation original = myFactory.getPresentation(entry.getKey());
      Presentation cloned = entry.getValue();
      original.copyFrom(cloned);
      reflectSubsequentChangesInOriginalPresentation(original, cloned);
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

  private static <T> T callAction(AnAction action, String operation, Supplier<T> call) {
    if (action instanceof UpdateInBackground || ApplicationManager.getApplication().isDispatchThread()) return call.get();

    ProgressIndicator progress = Objects.requireNonNull(ProgressManager.getInstance().getProgressIndicator());

    return ActionUpdateEdtExecutor.computeOnEdt(() -> {
      long start = System.currentTimeMillis();
      try {
        return ProgressManager.getInstance().runProcess(call::get, ProgressWrapper.wrap(progress));
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

  private List<AnAction> expandActionGroup(ActionGroup group, boolean hideDisabled, UpdateStrategy strategy) {
    return removeUnnecessarySeparators(doExpandActionGroup(group, hideDisabled, strategy));
  }

  /**
   * @return actions from the given and nested non-popup groups that are visible after updating
   */
  @NotNull
  List<AnAction> expandActionGroupWithTimeout(ActionGroup group, boolean hideDisabled) {
    List<AnAction> result = ProgressIndicatorUtils.withTimeout(Registry.intValue("actionSystem.update.timeout.ms"),
                                                               () -> expandActionGroup(group, hideDisabled));
    try {
      return result != null ? result : expandActionGroup(group, hideDisabled, myCheapStrategy);
    }
    finally {
      applyPresentationChanges();
    }
  }

  CancellablePromise<List<AnAction>> expandActionGroupAsync(ActionGroup group, boolean hideDisabled) {
    AsyncPromise<List<AnAction>> promise = new AsyncPromise<>();
    ProgressIndicator indicator = new EmptyProgressIndicator();
    promise.onError(__ -> {
      indicator.cancel();
      ActionUpdateEdtExecutor.computeOnEdt(() -> {
        applyPresentationChanges();
        return null;
      });
    });

    cancelAndRestartOnUserActivity(promise, indicator);

    ourExecutor.submit(() -> {
      while (promise.getState() == Promise.State.PENDING) {
        try {
          boolean success = ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(() -> {
            List<AnAction> result = expandActionGroup(group, hideDisabled, myRealUpdateStrategy);
            ActionUpdateEdtExecutor.computeOnEdt(() -> {
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
    });
    return promise;
  }

  private static void cancelAndRestartOnUserActivity(Promise<?> promise, ProgressIndicator indicator) {
    Disposable disposable = Disposer.newDisposable("Action Update");
    IdeEventQueue.getInstance().addPostprocessor(e -> {
      if (e instanceof ComponentEvent && !(e instanceof PaintEvent) && (e.getID() & AWTEvent.MOUSE_MOTION_EVENT_MASK) == 0) {
        indicator.cancel();
      }
      return false;
    }, disposable);
    promise.onProcessed(__ -> Disposer.dispose(disposable));
  }

  private List<AnAction> doExpandActionGroup(ActionGroup group, boolean hideDisabled, UpdateStrategy strategy) {
    ProgressManager.checkCanceled();
    Presentation presentation = update(group, strategy);
    if (presentation == null || !presentation.isVisible()) { // don't process invisible groups
      return Collections.emptyList();
    }

    List<AnAction> children = getGroupChildren(group, strategy);
    return ContainerUtil.concat(children, child -> expandGroupChild(child, hideDisabled, strategy));
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
    if (!myTransparentOnly || child.isTransparentUpdate()) {
      if (update(child, strategy) == null) return Collections.emptyList();
    }
    Presentation presentation = orDefault(child, myUpdatedPresentations.get(child));

    if (!presentation.isVisible() || (!presentation.isEnabled() && hideDisabled)) { // don't create invisible items in the menu
      return Collections.emptyList();
    }
    if (child instanceof ActionGroup) {
      ActionGroup actionGroup = (ActionGroup)child;
      if (hideDisabled && !hasEnabledChildren(actionGroup, strategy)) {
        return Collections.emptyList();
      }
      if (actionGroup.isPopup()) { // popup menu has its own presentation
        if (actionGroup.disableIfNoVisibleChildren()) {
          boolean visibleChildren = hasVisibleChildren(actionGroup, strategy);
          if (actionGroup.hideIfNoVisibleChildren() && !visibleChildren) {
            return Collections.emptyList();
          }
          presentation.setEnabled(visibleChildren || canBePerformed(actionGroup, strategy));
        }

        return Collections.singletonList(child);
      }

      return doExpandActionGroup((ActionGroup)child, hideDisabled || actionGroup instanceof CompactActionGroup, strategy);
    }

    return Collections.singletonList(child);
  }

  boolean canBePerformedCached(ActionGroup group) {
    return !Boolean.FALSE.equals(myCanBePerformedCache.get(group));
  }

  private boolean canBePerformed(ActionGroup group, UpdateStrategy strategy) {
    return myCanBePerformedCache.computeIfAbsent(group, __ -> strategy.canBePerformed.test(group));
  }

  private Presentation orDefault(AnAction action, Presentation presentation) {
    return presentation != null ? presentation : ActionUpdateEdtExecutor.computeOnEdt(() -> myFactory.getPresentation(action));
  }

  private static List<AnAction> removeUnnecessarySeparators(List<AnAction> visible) {
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
    AnActionEvent event = new AnActionEvent(null, myDataContext, myPlace, presentation,
                                            ActionManager.getInstance(), 0, myContextMenuAction, myToolbarAction);
    event.setInjectedContext(action.isInInjectedContext());
    return event;
  }

  private boolean hasEnabledChildren(ActionGroup group, UpdateStrategy strategy) {
    return hasChildrenWithState(group, false, true, strategy);
  }

  boolean hasVisibleChildren(ActionGroup group) {
    return hasVisibleChildren(group, myRealUpdateStrategy);
  }

  private boolean hasVisibleChildren(ActionGroup group, UpdateStrategy strategy) {
    return hasChildrenWithState(group, true, false, strategy);
  }

  private boolean hasChildrenWithState(ActionGroup group, boolean checkVisible, boolean checkEnabled, UpdateStrategy strategy) {
    if (group instanceof AlwaysVisibleActionGroup) {
      return true;
    }

    for (AnAction anAction : getGroupChildren(group, strategy)) {
      ProgressManager.checkCanceled();
      if (anAction instanceof Separator) {
        continue;
      }
      final Project project = CommonDataKeys.PROJECT.getData(myDataContext);
      if (project != null && DumbService.getInstance(project).isDumb() && !anAction.isDumbAware()) {
        continue;
      }

      Presentation presentation = orDefault(anAction, update(anAction, strategy));
      if (anAction instanceof ActionGroup) {
        ActionGroup childGroup = (ActionGroup)anAction;

        // popup menu must be visible itself
        if (childGroup.isPopup()) {
          if ((checkVisible && !presentation.isVisible()) || (checkEnabled && !presentation.isEnabled())) {
            continue;
          }
        }

        if (hasChildrenWithState(childGroup, checkVisible, checkEnabled, strategy)) {
          return true;
        }
      }
      else if ((checkVisible && presentation.isVisible()) || (checkEnabled && presentation.isEnabled())) {
        return true;
      }
    }

    return false;
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

  @Nullable
  private Presentation update(AnAction action, UpdateStrategy strategy) {
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
  static boolean doUpdate(boolean isInModalContext, AnAction action, AnActionEvent e) {
    if (ApplicationManager.getApplication().isDisposed()) return false;

    long startTime = System.currentTimeMillis();
    final boolean result;
    try {
      result = !ActionUtil.performDumbAwareUpdate(isInModalContext, action, e, false);
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

  private static class UpdateStrategy {
    final NullableFunction<AnAction, Presentation> update;
    final NotNullFunction<ActionGroup, AnAction[]> getChildren;
    final Predicate<ActionGroup> canBePerformed;

    UpdateStrategy(NullableFunction<AnAction, Presentation> update,
                   NotNullFunction<ActionGroup, AnAction[]> getChildren,
                   Predicate<ActionGroup> canBePerformed) {
      this.update = update;
      this.getChildren = getChildren;
      this.canBePerformed = canBePerformed;
    }
  }

}
