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
package com.intellij.ui.plaf.gtk;

import com.intellij.openapi.actionSystem.impl.ActionMenuItem;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.MenuItemUI;
import javax.swing.plaf.basic.BasicMenuItemUI;
import javax.swing.plaf.synth.SynthContext;
import java.awt.*;

public class GtkMenuItemUI extends BasicMenuItemUI {
  private static Icon myCachedCheckIcon = null;

  private final BasicMenuItemUI myOriginalUI;
  private JCheckBoxMenuItem myHiddenItem;

  public GtkMenuItemUI(final MenuItemUI originalUI) {
    assert isUiAcceptable(originalUI) : originalUI;
    myOriginalUI = (BasicMenuItemUI)originalUI;
  }

  public static boolean isUiAcceptable(final MenuItemUI ui) {
    return ui instanceof BasicMenuItemUI && GtkPaintingUtil.isSynthUI(ui);
  }

  @Override
  public void installUI(final JComponent c) {
    super.installUI(c);

    myHiddenItem = new JCheckBoxMenuItem();
    myOriginalUI.installUI(myHiddenItem);
    menuItem.setBorder(myHiddenItem.getBorder());
    final Icon icon = getCheckIconFromContext(myOriginalUI, myHiddenItem);
    checkIcon = isCheckBoxItem() ? icon : EmptyIcon.create(icon);
  }

  @Override
  public void uninstallUI(final JComponent c) {
    super.uninstallUI(c);

    myOriginalUI.uninstallUI(myHiddenItem);
    myHiddenItem = null;
    resetCachedCheckIcon();
  }

  private static Icon getCheckIconFromContext(final BasicMenuItemUI originalUI, final JCheckBoxMenuItem item) {
    if (myCachedCheckIcon == null) {
      final SynthContext context = GtkPaintingUtil.getSynthContext(originalUI, item);
      myCachedCheckIcon = context.getStyle().getIcon(context, "CheckBoxMenuItem.checkIcon");
    }
    return myCachedCheckIcon;
  }

  private boolean isCheckBoxItem() {
    return menuItem instanceof ActionMenuItem && ((ActionMenuItem)menuItem).isToggleable();
  }

  private static void resetCachedCheckIcon() {
    myCachedCheckIcon = null;
  }

  @Override
  public void update(final Graphics g, final JComponent c) {
    myHiddenItem.setSelected(menuItem.isSelected());

    if (UIUtil.isMurrineBasedTheme()) {
      acceleratorFont = menuItem.getFont();
      final Color fg = GtkPaintingUtil.getForeground(myOriginalUI, menuItem);
      acceleratorForeground = UIUtil.mix(fg, menuItem.getBackground(), menuItem.isSelected() ? 0.4 : 0.2);
      disabledForeground = fg;
    }
    if (checkIcon != null && !(checkIcon instanceof IconWrapper) && !(checkIcon instanceof EmptyIcon)) {
      checkIcon = new IconWrapper(checkIcon, myOriginalUI);
    }

    super.update(g, c);
  }

  @Override
  protected void paintText(final Graphics g, final JMenuItem menuItem, final Rectangle textRect, final String text) {
    if (!menuItem.isEnabled() && UIUtil.isMurrineBasedTheme()) {
      GtkPaintingUtil.paintDisabledText(myOriginalUI, g, menuItem, textRect, text);
      return;
    }
    super.paintText(g, menuItem, textRect, text);
  }
}
