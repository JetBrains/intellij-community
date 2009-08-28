package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.util.ui.UIUtil;

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
  }

  public Dimension getPreferredSize() {
    final Dimension preferredSize = new Dimension(super.getPreferredSize());
    final String text = getText();
    final FontMetrics fontMetrics = getFontMetrics(getFont());
    preferredSize.width += ICON_TEXT_SPACE;
    preferredSize.width += fontMetrics.stringWidth(text);
    return preferredSize;
  }

  public void paintComponent(Graphics g) {
    final String text = getText();
    final Icon icon = getIcon();
    final FontMetrics fontMetrics = getFontMetrics(getFont());
    int x = (int)Math.ceil((getWidth() - icon.getIconWidth() - fontMetrics.stringWidth(text)) / 2);
    int y = (int)Math.ceil((getHeight() - icon.getIconHeight()) / 2);
    ActionButtonLook look = ActionButtonLook.IDEA_LOOK;
    look.paintBackground(g, this);
    look.paintIconAt(g, this, icon, x, y);
    look.paintBorder(g, this);
    final int textHeight = fontMetrics.getMaxAscent() + fontMetrics.getMaxDescent();

    UIUtil.applyRenderingHints(g);
    g.setColor(isButtonEnabled() ? UIUtil.getLabelForeground() : UIUtil.getTextInactiveTextColor());
    final int iconTextDifference = (int)Math.ceil((icon.getIconHeight() - textHeight) / 2);
    final int textStartX = x + icon.getIconWidth() + ICON_TEXT_SPACE;
    g.drawString(text, textStartX, y + iconTextDifference + fontMetrics.getMaxAscent());
    final int mnemonicIndex = getMnemonicCharIndex(text);
    if (mnemonicIndex >= 0) {
      final char[] chars = text.toCharArray();
      final int startX = textStartX + fontMetrics.charsWidth(chars, 0, mnemonicIndex);
      final int startY = y + iconTextDifference + fontMetrics.getMaxAscent() + fontMetrics.getMaxDescent();
      final int endX = startX + fontMetrics.charWidth(text.charAt(mnemonicIndex));
      UIUtil.drawLine(g, startX, startY, endX, startY);
    }
  }

  private int getMnemonicCharIndex(String text) {
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
