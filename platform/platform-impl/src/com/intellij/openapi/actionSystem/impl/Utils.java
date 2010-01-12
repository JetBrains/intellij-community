/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class Utils{
  private static final Logger LOG=Logger.getInstance("#com.intellij.openapi.actionSystem.impl.Utils");
  @NonNls public static final String NOTHING_HERE = "Nothing here";
  public static final AnAction EMPTY_MENU_FILLER = new AnAction(NOTHING_HERE) {

    {
      getTemplatePresentation().setEnabled(false);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(false);
      super.update(e);
    }
  };

  private Utils() {}

  private static void handleUpdateException(AnAction action, Presentation presentation, Throwable exc) {
    String id = ActionManager.getInstance().getId(action);
    if (id != null) {
      LOG.error("update failed for AnAction with ID=" + id, exc);
    }
    else {
      LOG.error("update failed for ActionGroup: " + action + "[" + presentation.getText() + "]", exc);
    }
  }

  /**
   * @param actionManager manager
   * @param list this list contains expanded actions.
   */
  public static void expandActionGroup(@NotNull ActionGroup group,
                                       ArrayList<AnAction> list,
                                       PresentationFactory presentationFactory,
                                       DataContext context,
                                       String place, ActionManager actionManager){
    Presentation presentation = presentationFactory.getPresentation(group);
    AnActionEvent e = new AnActionEvent(
      null,
      context,
      place,
      presentation,
      actionManager,
      0
    );
    if (!doUpdate(group, e, presentation)) return;

    if(!presentation.isVisible()){ // don't process invisible groups
      return;
    }
    AnAction[] children=group.getChildren(e);
    for (int i = 0; i < children.length; i++) {
      AnAction child = children[i];
      if (child == null) {
        String groupId = ActionManager.getInstance().getId(group);
        LOG.assertTrue(false, "action is null: i=" + i + " group=" + group + " group id=" + groupId);
        continue;
      }

      presentation = presentationFactory.getPresentation(child);
      AnActionEvent e1 = new AnActionEvent(null, context, place, presentation, actionManager, 0);
      e1.setInjectedContext(child.isInInjectedContext());
      if (!doUpdate(child, e1, presentation)) continue;
      if (!presentation.isVisible()) { // don't create invisible items in the menu
        continue;
      }
      if (child instanceof ActionGroup) {
        ActionGroup actionGroup = (ActionGroup)child;
        if (actionGroup.isPopup()) { // popup menu has its own presentation
          // disable group if it contains no visible actions
          final boolean enabled = hasVisibleChildren(actionGroup, presentationFactory, context, place);
          presentation.setEnabled(enabled);
          list.add(child);
        }
        else {
          expandActionGroup((ActionGroup)child, list, presentationFactory, context, place, actionManager);
        }
      }
      else if (child instanceof Separator) {
        if (!list.isEmpty() && !(list.get(list.size() - 1) instanceof Separator)) {
          list.add(child);
        }
      }
      else {
        list.add(child);
      }
    }
  }

  // returns false if exception was thrown and handled
  private static boolean doUpdate(final AnAction action, final AnActionEvent e, final Presentation presentation) throws ProcessCanceledException {
    if (ApplicationManager.getApplication().isDisposed()) return false;

    long startTime = System.currentTimeMillis();
    final boolean result;
    try {
      result = !ActionUtil.performDumbAwareUpdate(action, e, false);
    }
    catch (ProcessCanceledException ex) {
      throw ex;
    }
    catch (Throwable exc) {
      handleUpdateException(action, presentation, exc);
      return false;
    }
    long endTime = System.currentTimeMillis();
    if (endTime - startTime > 10 && LOG.isDebugEnabled()) {
      LOG.debug("Action " + action + ": updated in " + (endTime-startTime) + " ms");
    }
    return result;
  }

  private static boolean hasVisibleChildren(ActionGroup group, PresentationFactory factory, DataContext context, String place) {
    AnActionEvent event = new AnActionEvent(null, context, place, factory.getPresentation(group), ActionManager.getInstance(), 0);
    event.setInjectedContext(group.isInInjectedContext());
    AnAction[] children = group.getChildren(event);
    for (AnAction anAction : children) {
      if (anAction instanceof Separator) {
        continue;
      }
      final Project project = PlatformDataKeys.PROJECT.getData(context);
      if (project != null && DumbService.getInstance(project).isDumb() && !(anAction instanceof DumbAware) && !(anAction instanceof ActionGroup)) {
        continue;
      }

      LOG.assertTrue(anAction != null, "Null action found in group " + group);

      final Presentation presentation = factory.getPresentation(anAction);
      updateGroupChild(context, place, anAction, presentation);
      if (anAction instanceof ActionGroup) {
        ActionGroup childGroup = (ActionGroup)anAction;

        // popup menu must be visible itself
        if (childGroup.isPopup()) {
          if (!presentation.isVisible()) {
            continue;
          }
        }

        if (hasVisibleChildren(childGroup, factory, context, place)) {
          return true;
        }
      }
      else if (presentation.isVisible()) {
        return true;
      }
    }

    return false;
  }

  public static void updateGroupChild(DataContext context, String place, AnAction anAction, final Presentation presentation) {
    AnActionEvent event1 = new AnActionEvent(null, context, place, presentation, ActionManager.getInstance(), 0);
    event1.setInjectedContext(anAction.isInInjectedContext());
    doUpdate(anAction, event1, presentation);
  }


  public static void fillMenu(@NotNull ActionGroup group,JComponent component, boolean enableMnemonics, PresentationFactory presentationFactory, DataContext context, String place, boolean isWindowMenu){
    ArrayList<AnAction> list = new ArrayList<AnAction>();
    expandActionGroup(group, list, presentationFactory, context, place, ActionManager.getInstance());

    final boolean fixMacScreenMenu = SystemInfo.isMacSystemMenu && isWindowMenu && Registry.is("actionSystem.mac.screenMenuNotUpdatedFix");

    final ArrayList<ActionMenuItem> menuItems = new ArrayList<ActionMenuItem>();

    for (int i = 0; i < list.size(); i++) {
      AnAction action = list.get(i);
      if (action instanceof Separator) {
        if (i > 0 && i < list.size() - 1) {
          component.add(new JPopupMenu.Separator());
        }
      }
      else if (action instanceof ActionGroup) {
        component.add(new ActionMenu(context, place, (ActionGroup)action, presentationFactory, enableMnemonics));
      }
      else {
        final ActionMenuItem each =
          new ActionMenuItem(action, presentationFactory.getPresentation(action), place, context, enableMnemonics, !fixMacScreenMenu);
        component.add(each);
        menuItems.add(each);
      }
    }

    if (list.isEmpty()) {
      final ActionMenuItem each =
        new ActionMenuItem(EMPTY_MENU_FILLER, presentationFactory.getPresentation(EMPTY_MENU_FILLER), place, context, enableMnemonics,
                           !fixMacScreenMenu);
      component.add(each);
      menuItems.add(each);
    }

    if (fixMacScreenMenu) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          for (ActionMenuItem each : menuItems) {
            if (each.getParent() != null) {
              each.prepare();
            }
          }
        }
      });
    }
  }
}
