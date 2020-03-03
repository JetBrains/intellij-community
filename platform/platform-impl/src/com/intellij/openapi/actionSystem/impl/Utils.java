// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class Utils {
  private static final Logger LOG = Logger.getInstance(Utils.class);
  @NonNls public static final String NOTHING_HERE = CommonBundle.message("empty.menu.filler");
  public static final AnAction EMPTY_MENU_FILLER = new AnAction(CommonBundle.messagePointer("empty.menu.filler")) {

    {
      getTemplatePresentation().setEnabled(false);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(false);
    }
  };

  /**
   * @return actions from the given and nested non-popup groups that are visible after updating
   */
  public static List<AnAction> expandActionGroup(boolean isInModalContext,
                                                 @NotNull ActionGroup group,
                                                 PresentationFactory presentationFactory,
                                                 @NotNull DataContext context,
                                                 String place){
    return expandActionGroup(isInModalContext, group, presentationFactory, context, place, null);
  }

  public static List<AnAction> expandActionGroup(boolean isInModalContext,
                                                 @NotNull ActionGroup group,
                                                 PresentationFactory presentationFactory,
                                                 @NotNull DataContext context,
                                                 String place, ActionGroupVisitor visitor) {
    return new ActionUpdater(isInModalContext, presentationFactory, context, place, false, false, false, visitor)
      .expandActionGroup(group, group instanceof CompactActionGroup);
  }

  public static CancellablePromise<List<AnAction>> expandActionGroupAsync(boolean isInModalContext,
                                                                          @NotNull ActionGroup group,
                                                                          PresentationFactory presentationFactory,
                                                                          @NotNull DataContext context,
                                                                          String place, @Nullable Utils.ActionGroupVisitor visitor) {
    if (!(context instanceof AsyncDataContext))
      context = new AsyncDataContext(context);
    return new ActionUpdater(isInModalContext, presentationFactory, context, place, false, false, false, visitor)
      .expandActionGroupAsync(group, group instanceof CompactActionGroup);
  }

  public static List<AnAction> expandActionGroupWithTimeout(boolean isInModalContext,
                                                 @NotNull ActionGroup group,
                                                 PresentationFactory presentationFactory,
                                                 @NotNull DataContext context,
                                                 String place, ActionGroupVisitor visitor,
                                                 int timeoutMs) {
    return new ActionUpdater(isInModalContext, presentationFactory, context, place, false, false, false, visitor)
      .expandActionGroupWithTimeout(group, group instanceof CompactActionGroup, timeoutMs);
  }

  private static final boolean DO_FULL_EXPAND = Boolean.getBoolean("actionSystem.use.full.group.expand"); // for tests and debug

  static void fillMenu(@NotNull ActionGroup group,
                       JComponent component,
                       boolean enableMnemonics,
                       PresentationFactory presentationFactory,
                       @NotNull DataContext context,
                       String place,
                       boolean isWindowMenu,
                       boolean isInModalContext,
                       boolean useDarkIcons) {
    final boolean checked = group instanceof CheckedActionGroup;

    ActionUpdater updater = new ActionUpdater(isInModalContext, presentationFactory, context, place, true, false, false);
    List<AnAction> list = DO_FULL_EXPAND ?
                          updater.expandActionGroupFull(group, group instanceof CompactActionGroup) :
                          updater.expandActionGroupWithTimeout(group, group instanceof CompactActionGroup);

    final boolean fixMacScreenMenu = SystemInfo.isMacSystemMenu && isWindowMenu && Registry.is("actionSystem.mac.screenMenuNotUpdatedFix");
    final ArrayList<Component> children = new ArrayList<>();

    for (int i = 0, size = list.size(); i < size; i++) {
      final AnAction action = list.get(i);
      Presentation presentation = presentationFactory.getPresentation(action);
      if (!(action instanceof Separator) && presentation.isVisible() && StringUtil.isEmpty(presentation.getText())) {
        String message = "Skipping empty menu item for action " + action + " of " + action.getClass();
        if (action.getTemplatePresentation().getText() == null) {
          message += ". Please specify some default action text in plugin.xml or action constructor";
        }
        LOG.warn(message);
        continue;
      }

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
              if (StartupUiUtil.isUnderDarcula() || UIUtil.isUnderWin10LookAndFeel()) {
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
               !(updater.canBePerformedCached((ActionGroup)action) &&
                 !updater.hasVisibleChildren((ActionGroup)action))) {
        ActionMenu menu = new ActionMenu(context, place, (ActionGroup)action, presentationFactory, enableMnemonics, useDarkIcons);
        component.add(menu);
        children.add(menu);
      }
      else {
        final ActionMenuItem each =
          new ActionMenuItem(action, presentation, place, context, enableMnemonics, !fixMacScreenMenu, checked, useDarkIcons);
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
      });
    }
  }

  public interface ActionGroupVisitor {
    void begin();

    boolean enterNode(@NotNull ActionGroup groupNode);
    void visitLeaf(@NotNull AnAction act);
    void leaveNode();
    @Nullable Component getCustomComponent(@NotNull AnAction action);

    boolean beginUpdate(@NotNull AnAction action, AnActionEvent e);
    void endUpdate(@NotNull AnAction action);
  }
}
