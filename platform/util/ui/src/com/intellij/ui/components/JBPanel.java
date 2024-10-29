// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.components.JBComponent;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

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

  public final T withPreferredWidth(int width) {
    myPreferredWidth = width;
    return (T)this;
  }

  public final T withPreferredHeight(int height) {
    myPreferredHeight = height;
    return (T)this;
  }

  public final T resetPreferredHeight() {
    myPreferredHeight = null;
    return (T)this;
  }

  public final T withPreferredSize(int width, int height) {
    myPreferredWidth = width;
    myPreferredHeight = height;
    return (T)this;
  }

  public final T withMaximumWidth(int width) {
    myMaximumWidth = width;
    return (T)this;
  }

  public final T withMaximumHeight(int height) {
    myMaximumHeight = height;
    return (T)this;
  }

  public final T withMaximumSize(int width, int height) {
    myMaximumWidth = width;
    myMaximumHeight = height;
    return (T)this;
  }

  public final T withMinimumWidth(int width) {
    myMinimumWidth = width;
    return (T)this;
  }

  public final T withMinimumHeight(int height) {
    myMinimumHeight = height;
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

  private static Dimension getSize(Dimension size, Integer width, Integer height, boolean isSet) {
    if (!isSet && size != null) {
      if (width != null) size.width = JBUIScale.scale(width);
      if (height != null) size.height = JBUIScale.scale(height);
    }
    return size;
  }
}
