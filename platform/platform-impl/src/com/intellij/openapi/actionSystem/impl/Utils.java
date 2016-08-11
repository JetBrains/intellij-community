/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

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
   * @param list this list contains expanded actions.
   * @param actionManager manager
   */
  public static void expandActionGroup(@NotNull ActionGroup group,
                                       List<AnAction> list,
                                       PresentationFactory presentationFactory,
                                       @NotNull DataContext context,
                                       String place,
                                       ActionManager actionManager){
    expandActionGroup(group, list, presentationFactory, context, place, actionManager, false, group instanceof CompactActionGroup);
  }

  public static void expandActionGroup(@NotNull ActionGroup group,
                                       List<AnAction> list,
                                       PresentationFactory presentationFactory,
                                       DataContext context,
                                       @NotNull String place,
                                       ActionManager actionManager,
                                       boolean transparentOnly) {
    expandActionGroup(group, list, presentationFactory, context, place, actionManager, transparentOnly, false);
  }

  /**
   * @param list this list contains expanded actions.
   * @param actionManager manager
   */
  public static void expandActionGroup(@NotNull ActionGroup group,
                                       List<AnAction> list,
                                       PresentationFactory presentationFactory,
                                       DataContext context,
                                       @NotNull String place,
                                       ActionManager actionManager,
                                       boolean transparentOnly,
                                       boolean hideDisabled) {
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

    if (!presentation.isVisible()) { // don't process invisible groups
      return;
    }
    AnAction[] children = group.getChildren(e);
    for (int i = 0; i < children.length; i++) {
      AnAction child = children[i];
      if (child == null) {
        String groupId = ActionManager.getInstance().getId(group);
        LOG.error("action is null: i=" + i + " group=" + group + " group id=" + groupId);
        continue;
      }

      presentation = presentationFactory.getPresentation(child);
      AnActionEvent e1 = new AnActionEvent(null, context, place, presentation, actionManager, 0);
      e1.setInjectedContext(child.isInInjectedContext());

      if (transparentOnly && child.isTransparentUpdate() || !transparentOnly) {
        if (!doUpdate(child, e1, presentation)) continue;
      }

      if (!presentation.isVisible() || (!presentation.isEnabled() && hideDisabled)) { // don't create invisible items in the menu
        continue;
      }
      if (child instanceof ActionGroup) {
        ActionGroup actionGroup = (ActionGroup)child;
        boolean skip = hideDisabled && !hasEnabledChildren(actionGroup, presentationFactory, context, place);
        if (skip) {
          continue;
        }
        if (actionGroup.isPopup()) { // popup menu has its own presentation
          if (actionGroup.disableIfNoVisibleChildren()) {
            final boolean visibleChildren = hasVisibleChildren(actionGroup, presentationFactory, context, place);
            if (actionGroup.hideIfNoVisibleChildren() && !visibleChildren) {
              continue;
            }
            presentation.setEnabled(actionGroup.canBePerformed(context) || visibleChildren);
          }


          list.add(child);
        }
        else {
          expandActionGroup((ActionGroup)child, list, presentationFactory, context, place, actionManager, false, hideDisabled);
        }
      }
      else if (child instanceof Separator) {
        if (!StringUtil.isEmpty(((Separator)child).getText()) || (!list.isEmpty() && !(list.get(list.size() - 1) instanceof Separator))) {
          list.add(child);
        }
      }
      else {
        if (hideDisabled && !hasEnabledChildren(new DefaultActionGroup(child), presentationFactory, context, place)) {
          continue;
        }
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
    return hasChildrenWithState(group, factory, context, place, true, false);
  }

  private static boolean hasEnabledChildren(ActionGroup group, PresentationFactory factory, DataContext context, String place) {
    return hasChildrenWithState(group, factory, context, place, false, true);
  }

  private static boolean hasChildrenWithState(ActionGroup group,
                                              PresentationFactory factory,
                                              DataContext context,
                                              String place,
                                              boolean checkVisible,
                                              boolean checkEnabled) {
    //noinspection InstanceofIncompatibleInterface
    if (group instanceof AlwaysVisibleActionGroup) {
      return true;
    }

    AnActionEvent event = new AnActionEvent(null, context, place, factory.getPresentation(group), ActionManager.getInstance(), 0);
    event.setInjectedContext(group.isInInjectedContext());
    for (AnAction anAction : group.getChildren(event)) {
      if (anAction == null) {
        LOG.error("Null action found in group " + group + ", " + factory.getPresentation(group));
        continue;
      }
      if (anAction instanceof Separator) {
        continue;
      }
      final Project project = CommonDataKeys.PROJECT.getData(context);
      if (project != null && DumbService.getInstance(project).isDumb() && !anAction.isDumbAware()) {
        continue;
      }

      final Presentation presentation = factory.getPresentation(anAction);
      updateGroupChild(context, place, anAction, presentation);
      if (anAction instanceof ActionGroup) {
        ActionGroup childGroup = (ActionGroup)anAction;

        // popup menu must be visible itself
        if (childGroup.isPopup()) {
          if ((checkVisible && !presentation.isVisible()) || (checkEnabled && !presentation.isEnabled())) {
            continue;
          }
        }

        if (hasChildrenWithState(childGroup, factory, context, place, checkVisible, checkEnabled)) {
          return true;
        }
      }
      else if ((checkVisible && presentation.isVisible()) || (checkEnabled && presentation.isEnabled())) {
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

  public static void fillMenu(@NotNull final ActionGroup group,
                              final JComponent component,
                              final boolean enableMnemonics,
                              final PresentationFactory presentationFactory,
                              @NotNull DataContext context,
                              final String place,
                              final boolean isWindowMenu,
                              final boolean mayDataContextBeInvalid){
    final ActionCallback menuBuilt = new ActionCallback();
    final boolean checked = group instanceof CheckedActionGroup;

    final ArrayList<AnAction> list = new ArrayList<>();
    expandActionGroup(group, list, presentationFactory, context, place, ActionManager.getInstance());

    final boolean fixMacScreenMenu = SystemInfo.isMacSystemMenu && isWindowMenu && Registry.is("actionSystem.mac.screenMenuNotUpdatedFix");
    final ArrayList<Component> children = new ArrayList<>();

    for (int i = 0, size = list.size(); i < size; i++) {
      final AnAction action = list.get(i);
      if (action instanceof Separator) {
        final String text = ((Separator)action).getText();
        if (!StringUtil.isEmpty(text) || (i > 0 && i < size - 1)) {
          component.add(new JPopupMenu.Separator() {
            private final JMenuItem myMenu = !StringUtil.isEmpty(text) ? new JMenuItem(text) : null;
            @Override
            public Insets getInsets() {
              final Insets insets = super.getInsets();
              final boolean fix = UIUtil.isUnderGTKLookAndFeel() &&
                                  getBorder() != null &&
                                  insets.top + insets.bottom == 0;
              return fix ? new Insets(2, insets.left, 3, insets.right) : insets;  // workaround for Sun bug #6636964
            }

            @Override
            public void doLayout() {
              super.doLayout();
              if (myMenu != null) {
                myMenu.setBounds(getBounds());
              }
            }

            @Override
            protected void paintComponent(Graphics g) {
              if (UIUtil.isUnderWindowsClassicLookAndFeel() || UIUtil.isUnderDarcula() || UIUtil.isUnderWindowsLookAndFeel()) {
                g.setColor(component.getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
              }
              if (myMenu != null) {
                myMenu.paint(g);
              } else {
                super.paintComponent(g);
              }
            }

            @Override
            public Dimension getPreferredSize() {
              return myMenu != null ? myMenu.getPreferredSize() : super.getPreferredSize();
            }
          });
        }
      }
      else if (action instanceof ActionGroup &&
               !(((ActionGroup)action).canBePerformed(context) &&
                 !hasVisibleChildren((ActionGroup)action, presentationFactory, context, place))) {
        ActionMenu menu = new ActionMenu(context, place, (ActionGroup)action, presentationFactory, enableMnemonics, false);
        component.add(menu);
        children.add(menu);
      }
      else {
        final ActionMenuItem each =
          new ActionMenuItem(action, presentationFactory.getPresentation(action), place, context, enableMnemonics, !fixMacScreenMenu, checked);
        component.add(each);
        children.add(each);
      }
    }

    if (list.isEmpty()) {
      final ActionMenuItem each =
        new ActionMenuItem(EMPTY_MENU_FILLER, presentationFactory.getPresentation(EMPTY_MENU_FILLER), place, context, enableMnemonics,
                           !fixMacScreenMenu, checked);
      component.add(each);
      children.add(each);
    }

    if (fixMacScreenMenu) {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        for (Component each : children) {
          if (each.getParent() != null && each instanceof ActionMenuItem) {
            ((ActionMenuItem)each).prepare();
          }
        }
        menuBuilt.setDone();
      });
    }
    else {
      menuBuilt.setDone();
    }

    menuBuilt.doWhenDone(() -> {
      if (!mayDataContextBeInvalid) return;

      if (IdeFocusManager.getInstance(null).isFocusBeingTransferred()) {
        IdeFocusManager.getInstance(null).doWhenFocusSettlesDown(() -> {
          if (!component.isShowing()) return;

          DataContext context1 = DataManager.getInstance().getDataContext();
          expandActionGroup(group, new ArrayList<>(), presentationFactory, context1, place, ActionManager.getInstance());

          for (Component each : children) {
            if (each instanceof ActionMenuItem) {
              ((ActionMenuItem)each).updateContext(context1);
            } else if (each instanceof ActionMenu) {
              ((ActionMenu)each).updateContext(context1);
            }
          }
        });
      }
    });

  }
}
