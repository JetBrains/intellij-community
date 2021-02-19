// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class Utils {
  private static final Logger LOG = Logger.getInstance(Utils.class);
  @Nls public static final String NOTHING_HERE = CommonBundle.message("empty.menu.filler");
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

  @NotNull
  public static DataContext wrapDataContext(@NotNull DataContext dataContext) {
    if (dataContext instanceof DataManagerImpl.MyDataContext &&
        Registry.is("actionSystem.update.actions.async")) {
      return new PreCachedDataContext(dataContext);
    }
    return dataContext;
  }

  public static boolean isAsyncDataContext(@NotNull DataContext dataContext) {
    return dataContext instanceof PreCachedDataContext;
  }

  /**
   * @return actions from the given and nested non-popup groups that are visible after updating
   */
  public static List<AnAction> expandActionGroup(boolean isInModalContext,
                                                 @NotNull ActionGroup group,
                                                 @NotNull PresentationFactory presentationFactory,
                                                 @NotNull DataContext context,
                                                 @NotNull String place){
    return expandActionGroup(isInModalContext, group, presentationFactory, context, place, false, null);
  }

  public static CancellablePromise<List<AnAction>> expandActionGroupAsync(boolean isInModalContext,
                                                                          @NotNull ActionGroup group,
                                                                          @NotNull PresentationFactory presentationFactory,
                                                                          @NotNull DataContext context,
                                                                          @NotNull String place,
                                                                          @Nullable Utils.ActionGroupVisitor visitor) {
    if (!(context instanceof PreCachedDataContext)) {
      context = new PreCachedDataContext(context);
    }
    return new ActionUpdater(isInModalContext, presentationFactory, context, place, false, false, visitor)
      .expandActionGroupAsync(group, group instanceof CompactActionGroup);
  }

  public static List<AnAction> expandActionGroupWithTimeout(boolean isInModalContext,
                                                 @NotNull ActionGroup group,
                                                 @NotNull PresentationFactory presentationFactory,
                                                 @NotNull DataContext context,
                                                 @NotNull String place,
                                                 @Nullable ActionGroupVisitor visitor,
                                                 int timeoutMs) {
    return new ActionUpdater(isInModalContext, presentationFactory, context, place, false, false, visitor)
      .expandActionGroupWithTimeout(group, group instanceof CompactActionGroup, timeoutMs);
  }

  private static final boolean DO_FULL_EXPAND = Boolean.getBoolean("actionSystem.use.full.group.expand"); // for tests and debug

  public static List<AnAction> expandActionGroup(boolean isInModalContext,
                                                 @NotNull ActionGroup group,
                                                 @NotNull PresentationFactory presentationFactory,
                                                 @NotNull DataContext context,
                                                 @NotNull String place,
                                                 boolean isContextMenu,
                                                 @Nullable ActionGroupVisitor visitor) {
    ActionUpdater updater = new ActionUpdater(
      isInModalContext, presentationFactory, context, place, isContextMenu, false, visitor);
    return DO_FULL_EXPAND ?
           updater.expandActionGroupFull(group, group instanceof CompactActionGroup) :
           updater.expandActionGroupWithTimeout(group, group instanceof CompactActionGroup);
  }

  static void fillMenu(@NotNull ActionGroup group,
                       @NotNull JComponent component,
                       boolean enableMnemonics,
                       @NotNull PresentationFactory presentationFactory,
                       @NotNull DataContext context,
                       @NotNull String place,
                       boolean isWindowMenu,
                       boolean isInModalContext,
                       boolean useDarkIcons) {
    List<AnAction> list = expandActionGroup(isInModalContext, group, presentationFactory, context, place, true, null);
    boolean checked = group instanceof CheckedActionGroup;
    fillMenuInner(component, list, checked, enableMnemonics, presentationFactory, context, place, isWindowMenu, useDarkIcons);
  }

  private static void fillMenuInner(JComponent component,
                                    List<AnAction> list,
                                    boolean checked,
                                    boolean enableMnemonics,
                                    PresentationFactory presentationFactory,
                                    @NotNull DataContext context,
                                    String place,
                                    boolean isWindowMenu,
                                    boolean useDarkIcons) {
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
          JPopupMenu.Separator separator = createSeparator(text);
          component.add(separator);
          children.add(separator);
        }
      }
      else if (action instanceof ActionGroup &&
               !Boolean.TRUE.equals(presentation.getClientProperty("actionGroup.perform.only"))) {
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
      SwingUtilities.invokeLater(() -> {
        for (Component each : children) {
          if (each.getParent() != null && each instanceof ActionMenuItem) {
            ((ActionMenuItem)each).prepare();
          }
        }
      });
    }
    if (SystemInfo.isMacSystemMenu && isWindowMenu) {
      if (ActionMenu.isAligned()) {
        Icon icon = hasIcons(children) ? ActionMenuItem.EMPTY_ICON : null;
        children.forEach(child -> replaceIconIn(child, icon));
      } else if (ActionMenu.isAlignedInGroup()) {
        ArrayList<Component> currentGroup = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
          Component child = children.get(i);
          boolean isSeparator = child instanceof JPopupMenu.Separator;
          boolean isLastElement = i == children.size() - 1;
          if (isLastElement || isSeparator) {
            if (isLastElement && !isSeparator) {
              currentGroup.add(child);
            }
            Icon icon = hasIcons(currentGroup) ? ActionMenuItem.EMPTY_ICON : null;
            currentGroup.forEach(menuItem -> replaceIconIn(menuItem, icon));
            currentGroup.clear();
          } else {
            currentGroup.add(child);
          }
        }
      }
    }
  }

  @NotNull
  private static JPopupMenu.Separator createSeparator(@NlsContexts.Separator String text) {
    return new JPopupMenu.Separator() {
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
          g.setColor(getParent().getBackground());
          g.fillRect(0, 0, getWidth(), getHeight());
        }
        if (myMenu != null) {
          myMenu.paint(g);
        }
        else {
          super.paintComponent(g);
        }
      }

      @Override
      public Dimension getPreferredSize() {
        return myMenu != null ? myMenu.getPreferredSize() : super.getPreferredSize();
      }
    };
  }

  private static void replaceIconIn(Component menuItem, Icon icon) {
    Icon from = icon == null ? ActionMenuItem.EMPTY_ICON : null;

    if (menuItem instanceof ActionMenuItem && ((ActionMenuItem)menuItem).getIcon() == from) {
        ((ActionMenuItem)menuItem).setIcon(icon);
    } else if (menuItem instanceof ActionMenu && ((ActionMenu)menuItem).getIcon() == from) {
        ((ActionMenu)menuItem).setIcon(icon);
    }
  }

  private static boolean hasIcons(List<? extends Component> components) {
    for (Component comp : components) {
      if (hasNotEmptyIcon(comp)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasNotEmptyIcon(Component comp) {
    Icon icon = null;
    if (comp instanceof ActionMenuItem) {
      icon = ((ActionMenuItem)comp).getIcon();
    } else if (comp instanceof ActionMenu) {
      icon = ((ActionMenu)comp).getIcon();
    }

    return icon != null && icon != ActionMenuItem.EMPTY_ICON;
  }

  @NotNull
  public static UpdateSession getOrCreateUpdateSession(@NotNull AnActionEvent e) {
    UpdateSession updater = e.getUpdateSession();
    if (updater == null) {
      ActionUpdater actionUpdater = new ActionUpdater(
        LaterInvocator.isInModalContext(), new PresentationFactory(), e.getDataContext(),
        e.getPlace(), e.isFromContextMenu(), e.isFromActionToolbar());
      updater = actionUpdater.asUpdateSession();
    }
    return updater;
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
