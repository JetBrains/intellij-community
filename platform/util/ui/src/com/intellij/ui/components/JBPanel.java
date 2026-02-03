// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.components.JBComponent;
import org.jetbrains.annotations.Nullable;

import javax.swing.JPanel;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.LayoutManager;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("unchecked")
public class JBPanel<T extends JBPanel> extends JPanel implements JBComponent<T> {
  private Integer myPreferredWidth;
  private Integer myPreferredHeight;
  private Integer myMaximumWidth;
  private Integer myMaximumHeight;
  private Integer myMinimumWidth;
  private Integer myMinimumHeight;

  public JBPanel(LayoutManager layout, boolean isDoubleBuffered) {
    super(layout, isDoubleBuffered);
  }

  public JBPanel(LayoutManager layout) {
    super(layout);
  }

  public JBPanel(boolean isDoubleBuffered) {
    super(isDoubleBuffered);
  }

  public JBPanel() {
    super();
  }

  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }

  @Override
  public final T withBorder(Border border) {
    setBorder(border);
    return (T)this;
  }

  @Override
  public final T withFont(JBFont font) {
    setFont(font);
    return (T)this;
  }

  @Override
  public final T andTransparent() {
    setOpaque(false);
    return (T)this;
  }

  @Override
  public final T andOpaque() {
    setOpaque(true);
    return (T)this;
  }

  public final T withBackground(@Nullable Color background) {
    setBackground(background);
    return (T)this;
  }

  public final T withPreferredWidth(int unscaledWidth) {
    myPreferredWidth = unscaledWidth;
    return (T)this;
  }

  public final T withPreferredHeight(int unscaledHeight) {
    myPreferredHeight = unscaledHeight;
    return (T)this;
  }

  public final T resetPreferredHeight() {
    myPreferredHeight = null;
    return (T)this;
  }

  public final T withPreferredSize(int unscaledWidth, int unscaledHeight) {
    myPreferredWidth = unscaledWidth;
    myPreferredHeight = unscaledHeight;
    return (T)this;
  }

  public final T withMaximumWidth(int unscaledWidth) {
    myMaximumWidth = unscaledWidth;
    return (T)this;
  }

  public final T withMaximumHeight(int unscaledHeight) {
    myMaximumHeight = unscaledHeight;
    return (T)this;
  }

  public final T withMaximumSize(int unscaledWidth, int unscaledHeight) {
    myMaximumWidth = unscaledWidth;
    myMaximumHeight = unscaledHeight;
    return (T)this;
  }

  public final T withMinimumWidth(int unscaledWidth) {
    myMinimumWidth = unscaledWidth;
    return (T)this;
  }

  public final T withMinimumHeight(int unscaledHeight) {
    myMinimumHeight = unscaledHeight;
    return (T)this;
  }

  @Override
  public Dimension getPreferredSize() {
    return getSize(super.getPreferredSize(), myPreferredWidth, myPreferredHeight, isPreferredSizeSet());
  }

  @Override
  public Dimension getMaximumSize() {
    return getSize(super.getMaximumSize(), myMaximumWidth, myMaximumHeight, isMaximumSizeSet());
  }

  @Override
  public Dimension getMinimumSize() {
    return getSize(super.getMinimumSize(), myMinimumWidth, myMinimumHeight, isMinimumSizeSet());
  }

  private static Dimension getSize(Dimension size, Integer unscaledWidth, Integer unscaledHeight, boolean isSet) {
    if (!isSet && size != null) {
      if (unscaledWidth != null) size.width = JBUIScale.scale(unscaledWidth);
      if (unscaledHeight != null) size.height = JBUIScale.scale(unscaledHeight);
    }
    return size;
  }
}
