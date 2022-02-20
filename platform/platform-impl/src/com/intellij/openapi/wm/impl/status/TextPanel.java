// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.ui.AntialiasingType;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsContexts.StatusBarText;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import java.awt.*;

public class TextPanel extends NonOpaquePanel implements Accessible {
  public static final String PROPERTY_TEXT = "TextPanel.text";

  @Nullable @Nls private String myText;

  private Integer myPrefHeight;
  private Dimension myExplicitSize;

  protected float myAlignment;

  protected TextPanel() {
    updateUI();
  }

  @Override
  public void updateUI() {
    GraphicsUtil.setAntialiasingType(this, AntialiasingType.getAAHintForSwingComponent());
    Object value = UIManager.getDefaults().get(RenderingHints.KEY_FRACTIONALMETRICS);
    if (value == null) value = RenderingHints.VALUE_FRACTIONALMETRICS_OFF;
    putClientProperty(RenderingHints.KEY_FRACTIONALMETRICS, value);
  }

  @Override
  public Font getFont() {
    return SystemInfo.isMac && !ExperimentalUI.isNewUI() ? JBUI.Fonts.label(11) : JBFont.label();
  }

  public void recomputeSize() {
    final JLabel label = new JLabel("XXX"); //NON-NLS
    label.setFont(getFont());
    myPrefHeight = label.getPreferredSize().height;
  }

  /**
   * @deprecated no effect
   */
  @Deprecated(forRemoval = true)
  public void resetColor() {
  }

  @Override
  protected void paintComponent(final Graphics g) {
    @Nls String s = getText();
    int panelWidth = getWidth();
    int panelHeight = getHeight();
    if (s == null) return;

    Graphics2D g2 = (Graphics2D)g;
    g2.setFont(getFont());
    UISettings.setupAntialiasing(g);

    Rectangle bounds = new Rectangle(panelWidth, panelHeight);
    FontMetrics fm = g.getFontMetrics();
    int textWidth = fm.stringWidth(s);
    int x = textWidth > panelWidth ? getInsets().left : getTextX(g2);
    int maxWidth = panelWidth - x - getInsets().right;
    if (textWidth > maxWidth) {
      s = truncateText(s, bounds, fm, new Rectangle(), new Rectangle(), maxWidth);
    }

    int y = UIUtil.getStringY(s, bounds, g2);
    if (ExperimentalUI.isNewUI() && SystemInfo.isJetBrainsJvm) {
      y += fm.getLeading(); // See SimpleColoredComponent.getTextBaseline
    }

    var effect = ComponentUtil.getClientProperty(this, IdeStatusBarImpl.WIDGET_EFFECT_KEY);
    var foreground = isEnabled() ?
                   effect == IdeStatusBarImpl.WidgetEffect.PRESSED ? JBUI.CurrentTheme.StatusBar.Widget.PRESSED_FOREGROUND :
                   effect == IdeStatusBarImpl.WidgetEffect.HOVER ? JBUI.CurrentTheme.StatusBar.Widget.HOVER_FOREGROUND :
                   JBUI.CurrentTheme.StatusBar.Widget.FOREGROUND :
                 UIUtil.getInactiveTextColor();

    g2.setColor(foreground);
    g2.drawString(s, x, y);
  }

  protected int getTextX(Graphics g) {
    String text = getText();
    Insets insets = getInsets();
    if (text == null || myAlignment == Component.LEFT_ALIGNMENT) {
      return insets.left;
    }
    if (myAlignment == Component.RIGHT_ALIGNMENT) {
      FontMetrics fm = g.getFontMetrics();
      int textWidth = fm.stringWidth(text);
      return getWidth() - insets.right - textWidth;
    }
    if (myAlignment == Component.CENTER_ALIGNMENT) {
      FontMetrics fm = g.getFontMetrics();
      int textWidth = fm.stringWidth(text);
      return (getWidth() - insets.left - insets.right - textWidth) / 2 + insets.left;
    }
    return insets.left;
  }

  @Nls
  protected String truncateText(@Nls String text, Rectangle bounds, FontMetrics fm, Rectangle textR, Rectangle iconR, int maxWidth) {
    return SwingUtilities.layoutCompoundLabel(this, fm, text, null, SwingConstants.CENTER, SwingConstants.CENTER, SwingConstants.CENTER,
                                              SwingConstants.TRAILING,
                                              bounds, iconR, textR, 0);
  }

  public void setTextAlignment(final float alignment) {
    myAlignment = alignment;
  }

  public final void setText(@Nullable @StatusBarText String text) {
    text = StringUtil.notNullize(text);
    if (text.equals(myText)) {
      return;
    }

    String oldAccessibleName = null;
    if (accessibleContext != null) {
      oldAccessibleName = accessibleContext.getAccessibleName();
    }

    String oldText = myText;
    myText = text;
    firePropertyChange(PROPERTY_TEXT, oldText, text);

    if ((accessibleContext != null) && !StringUtil.equals(accessibleContext.getAccessibleName(), oldAccessibleName)) {
      accessibleContext.firePropertyChange(
        AccessibleContext.ACCESSIBLE_VISIBLE_DATA_PROPERTY,
        oldAccessibleName,
        accessibleContext.getAccessibleName());
    }

    setPreferredSize(getPanelDimensionFromFontMetrics(myText));
    revalidate();
    repaint();
  }

  @Nullable
  @Nls
  public String getText() {
    return myText;
  }

  @Override
  public Dimension getPreferredSize() {
    if (myExplicitSize != null) {
      return myExplicitSize;
    }

    String text = getTextForPreferredSize();
    return getPanelDimensionFromFontMetrics(text);
  }

  private Dimension getPanelDimensionFromFontMetrics(String text) {
    Insets insets = getInsets();
    int width = insets.left + insets.right + (text != null ? getFontMetrics(getFont()).stringWidth(text) : 0);
    int height = (myPrefHeight == null) ? getMinimumSize().height : myPrefHeight;
    return new Dimension(width, height);
  }

  /**
   * @return the text that is used to calculate the preferred size
   */
  @Nullable
  protected String getTextForPreferredSize() {
    return myText;
  }

  public void setExplicitSize(@Nullable Dimension explicitSize) {
    myExplicitSize = explicitSize;
  }

  public static class WithIconAndArrows extends TextPanel {
    private final static int GAP = JBUIScale.scale(2);
    @Nullable private Icon myIcon;

    @Override
    protected void paintComponent(@NotNull final Graphics g) {
      super.paintComponent(g);
      Icon icon = myIcon == null || isEnabled() ? myIcon : IconLoader.getDisabledIcon(myIcon);
      if (icon != null) {
        icon.paintIcon(this, g, getIconX(g), getHeight() / 2 - icon.getIconHeight() / 2);
      }
    }

    /**
     * @deprecated arrows are not painted anymore
     */
    @Deprecated(forRemoval = true)
    protected boolean shouldPaintArrows() {
      return false;
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension preferredSize = super.getPreferredSize();
      if (myIcon == null) {
        return preferredSize;
      }
      return new Dimension(Math.max(preferredSize.width + myIcon.getIconWidth(), getHeight()), preferredSize.height);
    }

    @Override
    protected int getTextX(Graphics g) {
      int x = super.getTextX(g);
      if (myIcon == null || myAlignment == RIGHT_ALIGNMENT) {
        return x;
      }
      if (myAlignment == CENTER_ALIGNMENT) {
        return x + (myIcon.getIconWidth() + GAP) / 2;
      }
      if (myAlignment == LEFT_ALIGNMENT) {
        return x + myIcon.getIconWidth() + GAP;
      }
      return x;
    }

    private int getIconX(Graphics g) {
      int x = super.getTextX(g);
      if (myIcon == null || getText() == null || myAlignment == LEFT_ALIGNMENT) {
        return x;
      }
      if (myAlignment == CENTER_ALIGNMENT) {
        return x - (myIcon.getIconWidth() + GAP) / 2;
      }
      if (myAlignment == RIGHT_ALIGNMENT) {
        return x - myIcon.getIconWidth() - GAP;
      }
      return x;
    }

    public void setIcon(@Nullable Icon icon) {
      myIcon = icon;
    }

    public boolean hasIcon() { return myIcon != null; }

    public @Nullable Icon getIcon() { return myIcon; }
  }

  public static class ExtraSize extends TextPanel {
    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      return new Dimension(size.width + 3, size.height);
    }
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleTextPanel();
    }
    return accessibleContext;
  }

  protected class AccessibleTextPanel extends AccessibleJComponent {
    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.LABEL;
    }

    @Override
    public String getAccessibleName() {
      return myText;
    }
  }
}
