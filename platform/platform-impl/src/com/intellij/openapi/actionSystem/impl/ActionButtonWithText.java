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
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class ActionButtonWithText extends ActionButton {
  private static final int ICON_TEXT_SPACE = 2;

  public ActionButtonWithText(final AnAction action,
                              final Presentation presentation,
                              final String place,
                              final Dimension minimumSize) {
    super(action, presentation, place, minimumSize);
    setFont(UIUtil.getLabelFont());
    setForeground(UIUtil.getLabelForeground());
  }

  public Dimension getPreferredSize() {
    final Dimension preferredSize = new Dimension(super.getPreferredSize());
    final String text = getText();
    final FontMetrics fontMetrics = getFontMetrics(getFont());
    preferredSize.width += iconTextSpace();
    preferredSize.width += fontMetrics.stringWidth(text);
    return preferredSize;
  }

  public void paintComponent(Graphics g) {
    Icon icon = getIcon();
    FontMetrics fm = SwingUtilities2.getFontMetrics(this, g, getFont());
    Rectangle viewRect = new Rectangle(getSize());
    Insets i = getInsets();
    viewRect.x += i.left;
    viewRect.y += i.top;
    viewRect.width -= (i.right + viewRect.x);
    viewRect.height -= (i.bottom + viewRect.y);

    Rectangle iconRect = new Rectangle();
    Rectangle textRect = new Rectangle();
    String text = SwingUtilities.layoutCompoundLabel(this, fm, getText(), icon,
                                                     SwingConstants.CENTER, horizontalTextAlignment(),
                                                     SwingConstants.CENTER, SwingConstants.TRAILING,
                                                     viewRect, iconRect, textRect, iconTextSpace());
    ActionButtonLook look = ActionButtonLook.IDEA_LOOK;
    look.paintBackground(g, this);
    look.paintIconAt(g, this, icon, iconRect.x, iconRect.y);
    look.paintBorder(g, this);

    UIUtil.applyRenderingHints(g);
    g.setColor(isButtonEnabled() ? getForeground() : UIUtil.getInactiveTextColor());
    SwingUtilities2.drawStringUnderlineCharAt(this, g, text,
                                              getMnemonicCharIndex(text),
                                              textRect.x,
                                              textRect.y + fm.getAscent());
  }

  protected int horizontalTextAlignment() {
    return SwingConstants.CENTER;
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
    for (int i = 0; i < shortcuts.length; i++) {
      Shortcut shortcut = shortcuts[i];
      if (shortcut instanceof KeyboardShortcut) {
        KeyboardShortcut keyboardShortcut = (KeyboardShortcut)shortcut;
        if (keyboardShortcut.getSecondKeyStroke() == null) { // we are interested only in "mnemonic-like" shortcuts
          final KeyStroke keyStroke = keyboardShortcut.getFirstKeyStroke();
          final int modifiers = keyStroke.getModifiers();
          if ((modifiers & KeyEvent.ALT_MASK) != 0) {
            return (keyStroke.getKeyChar() != KeyEvent.CHAR_UNDEFINED)? text.indexOf(keyStroke.getKeyChar()) : text.indexOf(KeyEvent.getKeyText(keyStroke.getKeyCode()));
          }
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
