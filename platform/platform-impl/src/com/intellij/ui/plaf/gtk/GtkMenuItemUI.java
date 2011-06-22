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
import sun.swing.plaf.synth.SynthUI;

import javax.swing.*;
import javax.swing.plaf.basic.BasicMenuItemUI;
import java.awt.*;

public class GtkMenuItemUI extends BasicMenuItemUI {
  private final BasicMenuItemUI myOriginalUI;

  public GtkMenuItemUI(final BasicMenuItemUI originalUI) {
    myOriginalUI = originalUI;
  }

  @Override
  public void installUI(final JComponent c) {
    super.installUI(c);

    final JComponent temp = new JMenuItem();
    myOriginalUI.installUI(temp);
    menuItem.setBorder(temp.getBorder());
  }

  @Override
  public void update(final Graphics g, final JComponent c) {
    if (UIUtil.isMurrineBasedTheme()) {
      acceleratorFont = menuItem.getFont();
      final Color fg = GtkPaintingUtil.getForeground(myOriginalUI, menuItem);
      acceleratorForeground = UIUtil.mix(fg, menuItem.getBackground(), menuItem.isSelected() ? 0.4 : 0.2);
      disabledForeground = fg;
    }
    if (checkIcon != null && !(checkIcon instanceof IconWrapper)) {
      checkIcon = new IconWrapper(checkIcon, (SynthUI)myOriginalUI);
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
