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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class ActionUpdater {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.actionSystem.impl.ActionUpdater");

  private final boolean myModalContext;
  private final PresentationFactory myFactory;
  private final DataContext myDataContext;
  private final String myPlace;
  private final boolean myContextMenuAction;
  private final boolean myToolbarAction;
  private final boolean myTransparentOnly;

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
    AnActionEvent e = createActionEvent(group);
    if (!doUpdate(myModalContext, group, e)) return Collections.emptyList();

    if (!e.getPresentation().isVisible()) { // don't process invisible groups
      return Collections.emptyList();
    }

    return ContainerUtil.concat(getGroupChildren(group, e), child -> expandGroupChild(child, hideDisabled));
  }

  private static List<AnAction> getGroupChildren(ActionGroup group, AnActionEvent e) {
    AnAction[] children = group.getChildren(e);
    int nullIndex = ArrayUtil.indexOf(children, null);
    if (nullIndex < 0) return Arrays.asList(children);

    LOG.error("action is null: i=" + nullIndex + " group=" + group + " group id=" + ActionManager.getInstance().getId(group));
    return ContainerUtil.filter(children, Conditions.notNull());
  }

  private List<AnAction> expandGroupChild(AnAction child, boolean hideDisabled) {
    AnActionEvent e = createActionEvent(child);

    if (!myTransparentOnly || child.isTransparentUpdate()) {
      if (!doUpdate(myModalContext, child, e)) return Collections.emptyList();
    }

    Presentation childPresentation = e.getPresentation();
    if (!childPresentation.isVisible() || (!childPresentation.isEnabled() && hideDisabled)) { // don't create invisible items in the menu
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
          childPresentation.setEnabled(actionGroup.canBePerformed(myDataContext) || visibleChildren);
        }

        return Collections.singletonList(child);
      }

      return doExpandActionGroup((ActionGroup)child, hideDisabled || actionGroup instanceof CompactActionGroup);
    }

    return Collections.singletonList(child);
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
    AnActionEvent event = new AnActionEvent(null, myDataContext, myPlace, myFactory.getPresentation(action),
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

    AnActionEvent event = createActionEvent(group);
    for (AnAction anAction : getGroupChildren(group, event)) {
      if (anAction instanceof Separator) {
        continue;
      }
      final Project project = CommonDataKeys.PROJECT.getData(myDataContext);
      if (project != null && DumbService.getInstance(project).isDumb() && !anAction.isDumbAware()) {
        continue;
      }

      final Presentation presentation = myFactory.getPresentation(anAction);
      Utils.updateGroupChild(myDataContext, myPlace, anAction, presentation);
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
