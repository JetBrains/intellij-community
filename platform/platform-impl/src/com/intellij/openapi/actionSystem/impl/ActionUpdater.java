// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.NullableFunction;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

class ActionUpdater {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.actionSystem.impl.ActionUpdater");

  private final boolean myModalContext;
  private final PresentationFactory myFactory;
  private final DataContext myDataContext;
  private final String myPlace;
  private final boolean myContextMenuAction;
  private final boolean myToolbarAction;
  private final boolean myTransparentOnly;

  private final Map<AnAction, Presentation> myUpdatedPresentations = ContainerUtil.newIdentityTroveMap();
  private final Map<ActionGroup, List<AnAction>> myGroupChildren = ContainerUtil.newIdentityTroveMap();
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
        Presentation presentation = myFactory.getPresentation(action).clone();
        return doUpdate(myModalContext, action, createActionEvent(action, presentation)) ? presentation : null;
      },
      group -> group.getChildren(createActionEvent(group, orDefault(group, myUpdatedPresentations.get(group)))),
      group -> group.canBePerformed(myDataContext));
    myCheapStrategy = new UpdateStrategy(myFactory::getPresentation, group -> group.getChildren(null), group -> true);
  }

  /**
   * @return actions from the given and nested non-popup groups that are visible after updating
   */
  List<AnAction> expandActionGroup(ActionGroup group, boolean hideDisabled) {
    return expandActionGroup(group, hideDisabled, myRealUpdateStrategy);
  }

  private List<AnAction> expandActionGroup(ActionGroup group, boolean hideDisabled, UpdateStrategy strategy) {
    return removeUnnecessarySeparators(doExpandActionGroup(group, hideDisabled, strategy));
  }

  /**
   * @return actions from the given and nested non-popup groups that are visible after updating
   */
  @NotNull
  List<AnAction> expandActionGroupWithTimeout(ActionGroup group, boolean hideDisabled) {
    List<AnAction> result = withTimeout(Registry.intValue("actionSystem.update.timeout.ms"),
                                        () -> expandActionGroup(group, hideDisabled));
    return result != null ? result : expandActionGroup(group, hideDisabled, myCheapStrategy);
  }

  @Nullable
  private static <T> T withTimeout(int timeoutMs, Computable<T> computable) {
    ProgressManager.checkCanceled();
    ProgressIndicatorBase progress = new ProgressIndicatorBase();
    ScheduledFuture<?> cancelProgress = AppExecutorUtil.getAppScheduledExecutorService().schedule(progress::cancel, timeoutMs, TimeUnit.MILLISECONDS);
    try {
      return ProgressManager.getInstance().runProcess(computable, progress);
    }
    catch (ProcessCanceledException e) {
      return null;
    }
    finally {
      cancelProgress.cancel(false);
    }
  }

  private List<AnAction> doExpandActionGroup(ActionGroup group, boolean hideDisabled, UpdateStrategy strategy) {
    ProgressManager.checkCanceled();
    Presentation presentation = update(group, strategy);
    if (presentation == null || !presentation.isVisible()) { // don't process invisible groups
      return Collections.emptyList();
    }

    List<AnAction> children = getGroupChildren(group, strategy);
    List<List<AnAction>> expansions = ContainerUtil.map(children, child -> expandIfCheap(child, hideDisabled, strategy));
    expandMoreExpensiveActions(children, expansions, hideDisabled, strategy);
    return ContainerUtil.concat(expansions);
  }

  /**
   * We try to update as many actions as possible first and cache their presentation so that even if we're interrupted by timeout,
   * we show them all correctly
   */
  @Nullable
  private List<AnAction> expandIfCheap(AnAction action, boolean hideDisabled, UpdateStrategy strategy) {
    return strategy == myCheapStrategy ? null : withTimeout(1, () -> expandGroupChild(action, hideDisabled, strategy));
  }

  private void expandMoreExpensiveActions(List<AnAction> children,
                                          List<List<AnAction>> expansions,
                                          boolean hideDisabled,
                                          UpdateStrategy strategy) {
    for (int i = 0; i < children.size(); i++) {
      if (expansions.get(i) == null) {
        expansions.set(i, expandGroupChild(children.get(i), hideDisabled, strategy));
      }
    }
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
          presentation.setEnabled(visibleChildren || strategy.canBePerformed.test(actionGroup));
        }

        return Collections.singletonList(child);
      }

      return doExpandActionGroup((ActionGroup)child, hideDisabled || actionGroup instanceof CompactActionGroup, strategy);
    }

    return Collections.singletonList(child);
  }

  private Presentation orDefault(AnAction action, Presentation presentation) {
    return presentation != null ? presentation : myFactory.getPresentation(action);
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
    return hasVisibleChildren(group, myCheapStrategy);
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
    if (myUpdatedPresentations.containsKey(action)) {
      return myUpdatedPresentations.get(action);
    }

    Presentation presentation = strategy.update.fun(action);
    myUpdatedPresentations.put(action, presentation);
    if (presentation != null) {
      myFactory.getPresentation(action).copyFrom(presentation);
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
