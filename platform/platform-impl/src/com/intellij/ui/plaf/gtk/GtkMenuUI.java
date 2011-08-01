/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.MenuItemUI;
import javax.swing.plaf.basic.BasicMenuUI;
import java.awt.*;

public class GtkMenuUI extends BasicMenuUI {
  private final BasicMenuUI myOriginalUI;

  public GtkMenuUI(final MenuItemUI originalUI) {
    assert isUiAcceptable(originalUI) : originalUI;
    myOriginalUI = (BasicMenuUI)originalUI;
  }

  public static boolean isUiAcceptable(final MenuItemUI ui) {
    return ui instanceof BasicMenuUI && GtkPaintingUtil.isSynthUI(ui);
  }

  @Override
  public void installUI(final JComponent c) {
    super.installUI(c);

    final JMenu temp = new JMenu();
    myOriginalUI.installUI(temp);
    menuItem.setBorder(temp.getBorder());
  }

  @Override
  public void update(final Graphics g, final JComponent c) {
    if (arrowIcon != null && !(arrowIcon instanceof IconWrapper)) {
      arrowIcon = new IconWrapper(arrowIcon, myOriginalUI);
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
