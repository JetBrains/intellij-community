/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.status;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import java.awt.*;

public class TextPanel extends JComponent implements Accessible {
  @Nullable private String  myText;
  @Nullable private Color myCustomColor;

  private Integer   myPrefHeight;
  private Dimension myExplicitSize;

  private float myAlignment;
  private int myRightPadding = JBUI.scale(14);

  protected TextPanel() {
    setOpaque(false);
  }

  @Override
  public Font getFont() {
    return SystemInfo.isMac ? JBUI.Fonts.label(11) : JBUI.Fonts.label();
  }

  public void recomputeSize() {
    final JLabel label = new JLabel("XXX");
    label.setFont(getFont());
    myPrefHeight = label.getPreferredSize().height;
  }

  public void resetColor() {
    myCustomColor = null;
  }

  public void setCustomColor(@Nullable Color customColor) {
    myCustomColor = customColor;
  }

  @Override
  protected void paintComponent(final Graphics g) {
    String s = getText();
    final Rectangle bounds = getBounds();
    if (UIUtil.isUnderDarcula() && getClientProperty("NoFillPanelColorForDarcula") == null) {
      g.setColor(UIUtil.getPanelBackground());
      g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }
    if (s == null) return;
    final Insets insets = getInsets();

    final Graphics2D g2 = (Graphics2D)g;
    g2.setFont(getFont());

    UISettings.setupAntialiasing(g);

    final FontMetrics fm = g2.getFontMetrics();
    final int sWidth = fm.stringWidth(s);

    int x = insets.left;
    if (myAlignment == Component.CENTER_ALIGNMENT || myAlignment == Component.RIGHT_ALIGNMENT) {
      x = myAlignment == Component.CENTER_ALIGNMENT ? (bounds.width - sWidth) / 2 : bounds.width - insets.right - sWidth;
    }

    final Rectangle textR = new Rectangle();
    final Rectangle iconR = new Rectangle();
    final Rectangle viewR = new Rectangle(bounds);
    textR.x = textR.y = textR.width = textR.height = 0;

    viewR.width -= insets.left;
    viewR.width -= insets.right;

    final int maxWidth = bounds.width - insets.left - insets.right;
    if (sWidth > maxWidth) {
      s = truncateText(s, bounds, fm, textR, iconR, maxWidth);
    }

    final int y = UIUtil.getStringY(s, bounds, g2);

    g2.setColor(myCustomColor == null ? getForeground() : myCustomColor);
    g2.drawString(s, x, y);
  }

  protected String truncateText(String text, Rectangle bounds, FontMetrics fm, Rectangle textR, Rectangle iconR, int maxWidth) {
    return SwingUtilities.layoutCompoundLabel(fm, text, null, SwingConstants.CENTER, SwingConstants.CENTER, SwingConstants.CENTER,
                                               SwingConstants.TRAILING,
                                               bounds, iconR, textR, 0);
  }

  public void setTextAlignment(final float alignment) {
    myAlignment = alignment;
  }

  private static String splitText(final JLabel label, final String text, final int widthLimit) {
    final FontMetrics fontMetrics = label.getFontMetrics(label.getFont());

    final String[] lines = UIUtil.splitText(text, fontMetrics, widthLimit, ' ');

    final StringBuilder result = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      final String line = lines[i];
      if (i > 0) {
        result.append('\n');
      }
      result.append(line);
    }
    return result.toString();
  }

  public final void setText(@Nullable String text) {
    text = StringUtil.notNullize(text);
    if (text.equals(myText)) {
      return;
    }

    String oldAccessibleName = null;
    if (accessibleContext != null) {
      oldAccessibleName = accessibleContext.getAccessibleName();
    }

    myText = text;

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

  public String getText() {
    return myText;
  }

  public Dimension getPreferredSize() {
    if (myExplicitSize != null) {
      return myExplicitSize;
    }

    String text = getTextForPreferredSize();
    return getPanelDimensionFromFontMetrics(text);
  }

  public void setRightPadding(int rightPadding) {
    myRightPadding = rightPadding;
  }

  private Dimension getPanelDimensionFromFontMetrics (String text) {
    int width = (text == null) ? 0 : myRightPadding + getFontMetrics(getFont()).stringWidth(text);
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
    private final static int GAP = 2;
    @Nullable private Icon myIcon;

    @Override
    protected void paintComponent(@NotNull final Graphics g) {
      super.paintComponent(g);
      if (shouldPaintIconAndArrows() && getText() != null) {
        Rectangle r = getBounds();
        Insets insets = getInsets();
        Icon arrows = AllIcons.Ide.Statusbar_arrows;
        arrows.paintIcon(this, g, r.width - insets.right - arrows.getIconWidth() - 2,
                         r.height / 2 - arrows.getIconHeight() / 2);
        if (myIcon != null) {
          myIcon.paintIcon(this, g, insets.left - GAP - myIcon.getIconWidth(), r.height / 2 - myIcon.getIconHeight() / 2);
        }
      }
    }

    protected boolean shouldPaintIconAndArrows() {
      return true;
    }

    @NotNull
    @Override
    public Insets getInsets() {
      Insets insets = super.getInsets();
      if (myIcon != null) {
        insets.left += myIcon.getIconWidth() + GAP * 2;
      }
      return insets;
    }

    @Override
    public Dimension getPreferredSize() {
      final Dimension preferredSize = super.getPreferredSize();
      int deltaWidth = AllIcons.Ide.Statusbar_arrows.getIconWidth();
      if (myIcon != null) {
        deltaWidth += myIcon.getIconWidth();
      }
      return new Dimension(preferredSize.width + deltaWidth, preferredSize.height);
    }

    public void setIcon(@Nullable Icon icon) {
      myIcon = icon;
    }
  }

  public static class ExtraSize extends TextPanel {
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
