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

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.MagicConstant;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class ActionButtonWithText extends ActionButton {
  private static final int ICON_TEXT_SPACE = 2;
  private int myHorizontalTextPosition = SwingConstants.TRAILING;
  private int myHorizontalTextAlignment = SwingConstants.CENTER;

  public ActionButtonWithText(final AnAction action,
                              final Presentation presentation,
                              final String place,
                              final Dimension minimumSize) {
    super(action, presentation, place, minimumSize);
    setFont(UIUtil.getLabelFont());
    setForeground(UIUtil.getLabelForeground());
  }

  public Dimension getPreferredSize() {
    Dimension basicSize = super.getPreferredSize();

    Icon icon = getIcon();
    FontMetrics fm = getFontMetrics(getFont());
    Rectangle viewRect = new Rectangle(0, 0, Short.MAX_VALUE, Short.MAX_VALUE);
    Insets insets = getInsets();
    int dx = insets.left + insets.right;
    int dy = insets.top + insets.bottom;

    Rectangle iconR = new Rectangle();
    Rectangle textR = new Rectangle();
    SwingUtilities.layoutCompoundLabel(this, fm, getText(), icon,
                                       SwingConstants.CENTER, horizontalTextAlignment(),
                                       SwingConstants.CENTER, horizontalTextPosition(),
                                       viewRect, iconR, textR, iconTextSpace());
    int x1 = Math.min(iconR.x, textR.x);
    int x2 = Math.max(iconR.x + iconR.width, textR.x + textR.width);
    int y1 = Math.min(iconR.y, textR.y);
    int y2 = Math.max(iconR.y + iconR.height, textR.y + textR.height);
    Dimension rv = new Dimension(x2 - x1 + dx, y2 - y1 + dy);

    rv.width += Math.max(basicSize.height - rv.height, 0);

    rv.width = Math.max(rv.width, basicSize.width);
    rv.height = Math.max(rv.height, basicSize.height);
    return rv;
  }

  public void paintComponent(Graphics g) {
    Icon icon = getIcon();
    FontMetrics fm = getFontMetrics(getFont());
    Rectangle viewRect = new Rectangle(getSize());
    JBInsets.removeFrom(viewRect, getInsets());

    Rectangle iconRect = new Rectangle();
    Rectangle textRect = new Rectangle();
    String text = SwingUtilities.layoutCompoundLabel(this, fm, getText(), icon,
                                                     SwingConstants.CENTER, horizontalTextAlignment(),
                                                     SwingConstants.CENTER, horizontalTextPosition(),
                                                     viewRect, iconRect, textRect, iconTextSpace());
    ActionButtonLook look = ActionButtonLook.IDEA_LOOK;
    look.paintBackground(g, this);
    look.paintIconAt(g, this, icon, iconRect.x, iconRect.y);
    look.paintBorder(g, this);

    UISettings.setupAntialiasing(g);
    g.setColor(isButtonEnabled() ? getForeground() : getInactiveTextColor());
    SwingUtilities2.drawStringUnderlineCharAt(this, g, text,
                                              getMnemonicCharIndex(text),
                                              textRect.x,
                                              textRect.y + fm.getAscent());
  }

  public Color getInactiveTextColor() {
    return UIUtil.getInactiveTextColor();
  }

  public void setHorizontalTextPosition(@MagicConstant(valuesFromClass = SwingConstants.class) int position) {
    myHorizontalTextPosition = position;
  }

  public void setHorizontalTextAlignment(@MagicConstant(flagsFromClass = SwingConstants.class) int alignment) {
    myHorizontalTextAlignment = alignment;
  }

  protected int horizontalTextPosition() {
    return myHorizontalTextPosition;
  }

  protected int horizontalTextAlignment() {
    return myHorizontalTextAlignment;
  }

  protected int iconTextSpace() {
    return (getIcon() instanceof EmptyIcon || getIcon() == null ) ? 0 : ICON_TEXT_SPACE;
  }

  private int getMnemonicCharIndex(String text) {
    final int mnemonicIndex = myPresentation.getDisplayedMnemonicIndex();
    if (mnemonicIndex != -1) {
      return mnemonicIndex;
    }
    final ShortcutSet shortcutSet = myAction.getShortcutSet();
    final Shortcut[] shortcuts = shortcutSet.getShortcuts();
    for (Shortcut shortcut : shortcuts) {
      if (!(shortcut instanceof KeyboardShortcut)) continue;

      KeyboardShortcut keyboardShortcut = (KeyboardShortcut)shortcut;
      if (keyboardShortcut.getSecondKeyStroke() == null) { // we are interested only in "mnemonic-like" shortcuts
        final KeyStroke keyStroke = keyboardShortcut.getFirstKeyStroke();
        final int modifiers = keyStroke.getModifiers();
        if ((modifiers & InputEvent.ALT_MASK) != 0) {
          return (keyStroke.getKeyChar() != KeyEvent.CHAR_UNDEFINED)
                 ? text.indexOf(keyStroke.getKeyChar())
                 : text.indexOf(KeyEvent.getKeyText(keyStroke.getKeyCode()));
        }
      }
    }
    return -1;
  }

  private String getText() {
    final String text = myPresentation.getText();
    return text != null? text : "";
  }

}
