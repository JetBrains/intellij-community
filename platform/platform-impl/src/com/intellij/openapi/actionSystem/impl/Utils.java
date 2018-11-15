/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
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
  @NonNls public static final String NOTHING_HERE = "Nothing here";
  public static final AnAction EMPTY_MENU_FILLER = new AnAction(NOTHING_HERE) {

    {
      getTemplatePresentation().setEnabled(false);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(false);
      super.update(e);
    }
  };

  /**
   * @param list this list contains expanded actions.
   * @param actionManager manager
   */
  public static void expandActionGroup(boolean isInModalContext,
                                       @NotNull ActionGroup group,
                                       List<? super AnAction> list,
                                       PresentationFactory presentationFactory,
                                       @NotNull DataContext context,
                                       String place,
                                       ActionManager actionManager){
    expandActionGroup(isInModalContext, group, list, presentationFactory, context, place, actionManager, false, group instanceof CompactActionGroup);
  }


  /**
   * @param list this list contains expanded actions.
   * @param actionManager manager
   */
  private static void expandActionGroup(boolean isInModalContext,
                                        @NotNull ActionGroup group,
                                        List<? super AnAction> list,
                                        PresentationFactory presentationFactory,
                                        DataContext context,
                                        @NotNull String place,
                                        ActionManager actionManager,
                                        boolean transparentOnly,
                                        boolean hideDisabled) {
    expandActionGroup(isInModalContext , group, list, presentationFactory, context,
                      place, actionManager, transparentOnly, hideDisabled, false, false);
  }

  /**
   * @param list this list contains expanded actions.
   * @param actionManager manager
   */
  public static void expandActionGroup(boolean isInModalContext,
                                       @NotNull ActionGroup group,
                                       List<? super AnAction> list,
                                       PresentationFactory presentationFactory,
                                       DataContext context,
                                       @NotNull String place,
                                       ActionManager actionManager,
                                       boolean transparentOnly,
                                       boolean hideDisabled,
                                       boolean isContextMenuAction,
                                       boolean isToolbarAction) {
    list.addAll(new ActionUpdater(isInModalContext, presentationFactory, context, place, isContextMenuAction, isToolbarAction)
      .expandActionGroup(group, hideDisabled, transparentOnly));
  }

  public static void updateGroupChild(DataContext context, String place, AnAction anAction, final Presentation presentation) {
    AnActionEvent event1 = new AnActionEvent(null, context, place, presentation, ActionManager.getInstance(), 0);
    event1.setInjectedContext(anAction.isInInjectedContext());
    ActionUpdater.doUpdate(false, anAction, event1, presentation);
  }

  public static void fillMenu(@NotNull final ActionGroup group,
                              final JComponent component,
                              final boolean enableMnemonics,
                              final PresentationFactory presentationFactory,
                              @NotNull DataContext context,
                              final String place,
                              final boolean isWindowMenu,
                              final boolean mayDataContextBeInvalid,
                              boolean isInModalContext,
                              final boolean useDarkIcons) {
    final ActionCallback menuBuilt = new ActionCallback();
    final boolean checked = group instanceof CheckedActionGroup;

    ActionUpdater updater = new ActionUpdater(isInModalContext, presentationFactory, context, place, true, false);
    List<AnAction> list = updater.expandActionGroup(group, group instanceof CompactActionGroup, false);

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
            public void doLayout() {
              super.doLayout();
              if (myMenu != null) {
                myMenu.setBounds(getBounds());
              }
            }

            @Override
            protected void paintComponent(Graphics g) {
              if (UIUtil.isUnderDarcula() || UIUtil.isUnderWin10LookAndFeel()) {
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
                 !updater.hasVisibleChildren((ActionGroup)action))) {
        ActionMenu menu = new ActionMenu(context, place, (ActionGroup)action, presentationFactory, enableMnemonics, useDarkIcons);
        component.add(menu);
        children.add(menu);
      }
      else {
        final ActionMenuItem each =
          new ActionMenuItem(action, presentationFactory.getPresentation(action), place, context, enableMnemonics, !fixMacScreenMenu, checked, useDarkIcons);
        component.add(each);
        children.add(each);
      }
    }

    if (list.isEmpty()) {
      final ActionMenuItem each =
        new ActionMenuItem(EMPTY_MENU_FILLER, presentationFactory.getPresentation(EMPTY_MENU_FILLER), place, context, enableMnemonics,
                           !fixMacScreenMenu, checked, useDarkIcons);
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
  }
}
