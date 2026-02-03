// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.darcula.ui.ToolbarComboWidgetUiSizes;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.BitUtil;
import com.intellij.util.ui.*;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ComponentInputMapUIResource;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.function.Supplier;

public class ActionButtonWithText extends ActionButton {
  private static final int ICON_TEXT_SPACE = 2;
  /**
   * @see ToolbarComboWidgetUiSizes#getGapBeforeExpandIcon()
   */
  private static final int TEXT_ARROW_SPACE = 2;
  private static final int BUTTONS_GAP = 4;

  private int myHorizontalTextPosition = SwingConstants.TRAILING;
  private int myHorizontalTextAlignment = SwingConstants.CENTER;

  public static final Key<Boolean> SHORTCUT_SHOULD_SHOWN = new Key<>("SHORTCUT_SHOULD_SHOWN");

  public ActionButtonWithText(@NotNull AnAction action,
                              @Nullable Presentation presentation,
                              @NotNull String place,
                              @NotNull Dimension minimumSize) {
    this(action, presentation, place, () -> minimumSize);
  }

  public ActionButtonWithText(@NotNull AnAction action,
                              @Nullable Presentation presentation,
                              @NotNull String place,
                              Supplier<? extends @NotNull Dimension> minimumSize) {
    super(action, presentation, place, minimumSize);
    boolean useSmallFonts = Boolean.TRUE.equals(myPresentation.getClientProperty(ActionUtil.USE_SMALL_FONT_IN_TOOLBAR));
    setFont(useSmallFonts ? JBUI.Fonts.toolbarSmallComboBoxFont() : StartupUiUtil.getLabelFont());
    setForeground(UIUtil.getLabelForeground());
    myPresentation.addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(Presentation.PROP_MNEMONIC_KEY)) {
          int oldValue = evt.getOldValue() instanceof Integer ? (Integer)evt.getOldValue() : 0;
          int newValue = evt.getNewValue() instanceof Integer ? (Integer)evt.getNewValue() : 0;
          updateMnemonic(oldValue, newValue);
        }
        if(evt.getPropertyName().equals(SHORTCUT_SHOULD_SHOWN.toString())) {
          updateToolTipText();
        }
        if (evt.getPropertyName().equals(Presentation.PROP_ICON)) {
          revalidate();
        }
      }
    });
    getActionMap().put("doClick", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        click();
      }
    });
    updateMnemonic(KeyEvent.VK_UNDEFINED, myPresentation.getMnemonic());
    ClientProperty.put(this, MnemonicHelper.MNEMONIC_CHECKER, keyCode -> getMnemonic() == keyCode);
  }

  @Override
  public void updateUI() {
    super.updateUI();
    if (ActionPlaces.EDITOR_TOOLBAR.equals(myPlace)) {
      // tweak font & color for editor toolbar to match editor tabs style
      setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
      setForeground(ColorUtil.dimmer(JBColor.BLACK));
    }
    else {
      boolean useSmallFonts = Boolean.TRUE.equals(myPresentation.getClientProperty(ActionUtil.USE_SMALL_FONT_IN_TOOLBAR));
      setFont(useSmallFonts ? JBUI.Fonts.toolbarSmallComboBoxFont() : StartupUiUtil.getLabelFont());
    }
  }

  @Override
  protected @NotNull Icon getFallbackIcon(boolean enabled) {
    return EmptyIcon.ICON_0;
  }

  private void updateMnemonic(int lastMnemonic, int mnemonic) {
    if (mnemonic == lastMnemonic) {
      return;
    }
    InputMap windowInputMap = SwingUtilities.getUIInputMap(this, WHEN_IN_FOCUSED_WINDOW);

    int mask = SystemInfo.isMac ? InputEvent.ALT_MASK | InputEvent.CTRL_MASK : InputEvent.ALT_MASK;
    if (lastMnemonic != 0 && windowInputMap != null) {
      windowInputMap.remove(KeyStroke.getKeyStroke(lastMnemonic, mask, false));
    }
    if (mnemonic != 0) {
      if (windowInputMap == null) {
        windowInputMap = new ComponentInputMapUIResource(this);
        SwingUtilities.replaceUIInputMap(this, WHEN_IN_FOCUSED_WINDOW, windowInputMap);
      }
      windowInputMap.put(KeyStroke.getKeyStroke(mnemonic, mask, false), "doClick");
    }
  }

  protected @NotNull Insets getMargins() {
    return JBUI.insets(0, BUTTONS_GAP);
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension basicSize = super.getPreferredSize();

    Icon icon = getIcon();
    int position = horizontalTextPosition();

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

    if (shallPaintDownArrow()) {
      rv.width += getDownArrowIcon().getIconWidth() + JBUI.scale(TEXT_ARROW_SPACE);
    }

    Insets m = getMargins();
    JBInsets.addTo(rv, m);

    rv.width = Math.max(rv.width, basicSize.width);
    rv.height = Math.max(rv.height, basicSize.height);
    return rv;
  }

  @Override
  protected void updateToolTipText() {
    String description = myPresentation.getDescription();
    if (UISettings.isIdeHelpTooltipEnabled()) {
      HelpTooltip.dispose(this);
      HelpTooltip tooltip = myPresentation.getClientProperty(CUSTOM_HELP_TOOLTIP);
      if (tooltip == null && !StringUtil.isEmpty(description)) {
        tooltip = new HelpTooltip().setDescription(description);
        Boolean property = myPresentation.getClientProperty(SHORTCUT_SHOULD_SHOWN);
        if (property != null && property) {
          tooltip.setShortcut(getShortcutText());
        }
      }
      if (tooltip != null) {
        tooltip.installOn(this);
      }
    }
    else {
      setToolTipText(description);
    }
  }

  @Override
  public void paintComponent(Graphics g) {
    Icon icon = getIcon();
    Icon arrowIcon = shallPaintDownArrow() ? getEnableOrDisable(getDownArrowIcon()) : null;

    UISettings.setupAntialiasing(g);

    FontMetrics fm = getFontMetrics(getFont());
    Rectangle viewRect = getButtonRect();
    JBInsets.removeFrom(viewRect, getInsets());
    JBInsets.removeFrom(viewRect, getMargins());

    Rectangle iconRect = new Rectangle();
    Rectangle textRect = new Rectangle();
    String text = SwingUtilities.layoutCompoundLabel(this, fm, getText(), icon,
                                                     SwingConstants.CENTER, horizontalTextAlignment(),
                                                     SwingConstants.CENTER, horizontalTextPosition(),
                                                     viewRect, iconRect, textRect, iconTextSpace());
    if (arrowIcon != null) {
      int alignment = horizontalTextAlignment();
      int dx = alignment == SwingConstants.CENTER ? arrowIcon.getIconWidth() / 2 - 2:
               alignment == SwingConstants.RIGHT ? arrowIcon.getIconWidth() :
               0;
      iconRect.x -= dx;
      textRect.x -= dx;
    }
    ActionButtonLook look = getButtonLook();
    look.paintBackground(g, this);
    look.paintIcon(g, this, icon, iconRect.x, iconRect.y);
    look.paintBorder(g, this);

    g.setColor(isEnabled() ? getForeground() : getInactiveTextColor());
    UIUtilities.drawStringUnderlineCharAt(this, g, text, getMnemonicCharIndex(text),
                                          textRect.x, textRect.y + fm.getAscent());
    if (arrowIcon != null) {
      int x = Math.max(iconRect.x + iconRect.width, textRect.x + textRect.width) + JBUI.scale(TEXT_ARROW_SPACE);
      int y = textRect.y + (textRect.height - arrowIcon.getIconHeight()) / 2 + 1;
      getButtonLook().paintIcon(g, this, arrowIcon, x, y);
    }
  }

  protected @NotNull Icon getDownArrowIcon() {
    return AllIcons.General.LinkDropTriangle;
  }

  protected Rectangle getButtonRect() {
    return new Rectangle(getSize());
  }

  @Override
  protected void presentationPropertyChanged(@NotNull PropertyChangeEvent e) {
    super.presentationPropertyChanged(e);
    if (Presentation.PROP_TEXT_WITH_SUFFIX.equals(e.getPropertyName())) {
      revalidate(); // recalc preferred size & repaint instantly
      repaint();
    }
  }

  public Color getInactiveTextColor() {
    return NamedColorUtil.getInactiveTextColor();
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
    Icon icon = getIcon();
    return icon instanceof EmptyIcon || icon == null ? 0 : JBUI.scale(ICON_TEXT_SPACE);
  }

  protected int getMnemonicCharIndex(String text) {
    final int mnemonicIndex = myPresentation.getDisplayedMnemonicIndex();
    if (mnemonicIndex != -1) {
      return mnemonicIndex;
    }
    final ShortcutSet shortcutSet = myAction.getShortcutSet();
    final Shortcut[] shortcuts = shortcutSet.getShortcuts();
    for (Shortcut shortcut : shortcuts) {
      if (!(shortcut instanceof KeyboardShortcut keyboardShortcut)) continue;

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

  protected @NotNull @NlsActions.ActionText String getText() {
    final String text = myPresentation.getText(true);
    return text != null ? text : "";
  }

  public int getMnemonic() {
    return myPresentation.getMnemonic();
  }
}
