// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.BitUtil;
import com.intellij.util.ui.*;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.ComponentInputMapUIResource;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class ActionButtonWithText extends ActionButton {
  private static final int ICON_TEXT_SPACE = 2;
  private int myHorizontalTextPosition = SwingConstants.TRAILING;
  private int myHorizontalTextAlignment = SwingConstants.CENTER;

  public ActionButtonWithText(final AnAction action,
                              final Presentation presentation,
                              final String place,
                              final Dimension minimumSize) {
    super(action, presentation, place, minimumSize);
    setFont(action.useSmallerFontForTextInToolbar() ? JBUI.Fonts.toolbarSmallComboBoxFont() : UIUtil.getLabelFont());
    setForeground(UIUtil.getLabelForeground());
    myPresentation.addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(Presentation.PROP_MNEMONIC_KEY)) {
          int oldValue = evt.getOldValue() instanceof Integer ? (Integer)evt.getOldValue() : 0;
          int newValue = evt.getNewValue() instanceof Integer ? (Integer)evt.getNewValue() : 0;
          updateMnemonic(oldValue, newValue);
        }
      }
    });
    getActionMap().put("doClick", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        click();
      }
    });
    updateMnemonic(0, myPresentation.getMnemonic());
  }

  private void updateMnemonic(int lastMnemonic, int mnemonic) {
    if (mnemonic == lastMnemonic) {
      return;
    }
    InputMap windowInputMap = SwingUtilities.getUIInputMap(
      this, JComponent.WHEN_IN_FOCUSED_WINDOW);

    int mask = SystemInfo.isMac ? InputEvent.ALT_MASK | InputEvent.CTRL_MASK : InputEvent.ALT_MASK;
    if (lastMnemonic != 0 && windowInputMap != null) {
      windowInputMap.remove(KeyStroke.getKeyStroke(lastMnemonic, mask, false));
    }
    if (mnemonic != 0) {
      if (windowInputMap == null) {
        windowInputMap = new ComponentInputMapUIResource(this);
        SwingUtilities.replaceUIInputMap(this, JComponent.
          WHEN_IN_FOCUSED_WINDOW, windowInputMap);
      }
      windowInputMap.put(KeyStroke.getKeyStroke(mnemonic, mask, false), "doClick");
    }
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension basicSize = super.getPreferredSize();

    Icon icon = getIcon();
    int position = horizontalTextPosition();
    if ((icon == null || icon instanceof EmptyIcon) && myAction instanceof ActionGroup && ((ActionGroup)myAction).isPopup()) {
      icon = AllIcons.General.LinkDropTriangle;
      position = SwingConstants.LEFT;
    }

    FontMetrics fm = getFontMetrics(getFont());
    Rectangle viewRect = new Rectangle(0, 0, Short.MAX_VALUE, Short.MAX_VALUE);
    Insets insets = getInsets();
    int dx = insets.left + insets.right;
    int dy = insets.top + insets.bottom;

    Rectangle iconR = new Rectangle();
    Rectangle textR = new Rectangle();
    SwingUtilities.layoutCompoundLabel(this, fm, getText(), icon,
                                       SwingConstants.CENTER, horizontalTextAlignment(),
                                       SwingConstants.CENTER, position,
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

  @Override
  protected void updateToolTipText() {
    String description = myPresentation.getDescription();
    if (Registry.is("ide.helptooltip.enabled")) {
      HelpTooltip.dispose(this);
      if (StringUtil.isNotEmpty(description)) {
        new HelpTooltip().setDescription(description).installOn(this);
      }
    } else {
      setToolTipText(description);
    }
  }

  @Override
  public void paintComponent(Graphics g) {
    Icon icon = getIcon();
    int position = horizontalTextPosition();
    if ((icon == null || icon instanceof EmptyIcon) && myAction instanceof ActionGroup && ((ActionGroup)myAction).isPopup()) {
      icon = AllIcons.General.LinkDropTriangle;
      position = SwingConstants.LEFT;
    }

    FontMetrics fm = getFontMetrics(getFont());
    Rectangle viewRect = getButtonRect();
    JBInsets.removeFrom(viewRect, getInsets());

    Rectangle iconRect = new Rectangle();
    Rectangle textRect = new Rectangle();
    String text = SwingUtilities.layoutCompoundLabel(this, fm, getText(), icon,
                                                     SwingConstants.CENTER, horizontalTextAlignment(),
                                                     SwingConstants.CENTER, position,
                                                     viewRect, iconRect, textRect, iconTextSpace());
    ActionButtonLook look = ActionButtonLook.SYSTEM_LOOK;
    look.paintBackground(g, this);
    look.paintIconAt(g, icon, iconRect.x, iconRect.y);
    look.paintBorder(g, this);

    UISettings.setupAntialiasing(g);
    g.setColor(isButtonEnabled() ? getForeground() : getInactiveTextColor());
    UIUtilities.drawStringUnderlineCharAt(this, g, text,
                                          getMnemonicCharIndex(text),
                                          textRect.x,
                                              textRect.y + fm.getAscent());
  }

  protected Rectangle getButtonRect() {
    return new Rectangle(getSize());
  }

  @Override
  protected void presentationPropertyChanged(@NotNull PropertyChangeEvent e) {
    super.presentationPropertyChanged(e);
    if (Presentation.PROP_TEXT.equals(e.getPropertyName())) {
      revalidate(); // recalc preferred size & repaint instantly
    }
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
    return (getIcon() instanceof EmptyIcon || getIcon() == null) ? 0 : ICON_TEXT_SPACE;
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
        if (BitUtil.isSet(modifiers, InputEvent.ALT_MASK)) {
          return (keyStroke.getKeyChar() != KeyEvent.CHAR_UNDEFINED)
                 ? text.indexOf(keyStroke.getKeyChar())
                 : text.indexOf(KeyEvent.getKeyText(keyStroke.getKeyCode()));
        }
      }
    }
    return -1;
  }

  @NotNull
  private String getText() {
    final String text = myPresentation.getText();
    return text != null ? text : "";
  }

  public int getMnemonic() {
    return KeyEvent.getExtendedKeyCodeForChar(myPresentation.getMnemonic());
  }
}
