// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
  }

  /**
   * @return actions from the given and nested non-popup groups that are visible after updating
   */
  List<AnAction> expandActionGroup(ActionGroup group, boolean hideDisabled) {
    return removeUnnecessarySeparators(doExpandActionGroup(group, hideDisabled));
  }

  private List<AnAction> doExpandActionGroup(ActionGroup group, boolean hideDisabled) {
    Presentation presentation = update(group);
    if (presentation == null || !presentation.isVisible()) { // don't process invisible groups
      return Collections.emptyList();
    }

    return ContainerUtil.concat(getGroupChildren(group), child -> expandGroupChild(child, hideDisabled));
  }

  private List<AnAction> getGroupChildren(ActionGroup group) {
    return myGroupChildren.computeIfAbsent(group, __ -> {
      AnAction[] children = group.getChildren(createActionEvent(group));
      int nullIndex = ArrayUtil.indexOf(children, null);
      if (nullIndex < 0) return Arrays.asList(children);

      LOG.error("action is null: i=" + nullIndex + " group=" + group + " group id=" + ActionManager.getInstance().getId(group));
      return ContainerUtil.filter(children, Conditions.notNull());
    });
  }

  private List<AnAction> expandGroupChild(AnAction child, boolean hideDisabled) {
    if (!myTransparentOnly || child.isTransparentUpdate()) {
      if (update(child) == null) return Collections.emptyList();
    }
    Presentation presentation = orDefault(child, myUpdatedPresentations.get(child));

    if (!presentation.isVisible() || (!presentation.isEnabled() && hideDisabled)) { // don't create invisible items in the menu
      return Collections.emptyList();
    }
    if (child instanceof ActionGroup) {
      ActionGroup actionGroup = (ActionGroup)child;
      if (hideDisabled && !hasEnabledChildren(actionGroup)) {
        return Collections.emptyList();
      }
      if (actionGroup.isPopup()) { // popup menu has its own presentation
        if (actionGroup.disableIfNoVisibleChildren()) {
          boolean visibleChildren = hasVisibleChildren(actionGroup);
          if (actionGroup.hideIfNoVisibleChildren() && !visibleChildren) {
            return Collections.emptyList();
          }
          presentation.setEnabled(actionGroup.canBePerformed(myDataContext) || visibleChildren);
        }

        return Collections.singletonList(child);
      }

      return doExpandActionGroup((ActionGroup)child, hideDisabled || actionGroup instanceof CompactActionGroup);
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

  private AnActionEvent createActionEvent(AnAction action) {
    AnActionEvent event = new AnActionEvent(null, myDataContext, myPlace, orDefault(action, myUpdatedPresentations.get(action)),
                                            ActionManager.getInstance(), 0, myContextMenuAction, myToolbarAction);
    event.setInjectedContext(action.isInInjectedContext());
    return event;
  }

  private boolean hasEnabledChildren(ActionGroup group) {
    return hasChildrenWithState(group, false, true);
  }

  boolean hasVisibleChildren(ActionGroup group) {
    return hasChildrenWithState(group, true, false);
  }

  private boolean hasChildrenWithState(ActionGroup group, boolean checkVisible, boolean checkEnabled) {
    if (group instanceof AlwaysVisibleActionGroup) {
      return true;
    }

    for (AnAction anAction : getGroupChildren(group)) {
      if (anAction instanceof Separator) {
        continue;
      }
      final Project project = CommonDataKeys.PROJECT.getData(myDataContext);
      if (project != null && DumbService.getInstance(project).isDumb() && !anAction.isDumbAware()) {
        continue;
      }

      Presentation presentation = orDefault(anAction, update(anAction));
      if (anAction instanceof ActionGroup) {
        ActionGroup childGroup = (ActionGroup)anAction;

        // popup menu must be visible itself
        if (childGroup.isPopup()) {
          if ((checkVisible && !presentation.isVisible()) || (checkEnabled && !presentation.isEnabled())) {
            continue;
          }
        }

        if (hasChildrenWithState(childGroup, checkVisible, checkEnabled)) {
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
  private Presentation update(AnAction action) {
    if (myUpdatedPresentations.containsKey(action)) {
      return myUpdatedPresentations.get(action);
    }

    AnActionEvent event = createActionEvent(action);
    Presentation presentation = doUpdate(myModalContext, action, event) ? event.getPresentation(): null;
    myUpdatedPresentations.put(action, presentation);
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
}
