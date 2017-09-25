/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui.components;

import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
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
  public T withBorder(Border border) {
    setBorder(border);
    return (T)this;
  }

  @Override
  public T withFont(JBFont font) {
    setFont(font);
    return (T)this;
  }

  @Override
  public T andTransparent() {
    setOpaque(false);
    return (T)this;
  }

  @Override
  public T andOpaque() {
    setOpaque(true);
    return (T)this;
  }

  public T withBackground(@Nullable Color background) {
    setBackground(background);
    return (T)this;
  }

  public T withPreferredWidth(int width) {
    myPreferredWidth = width;
    return (T)this;
  }

  public T withPreferredHeight(int height) {
    myPreferredHeight = height;
    return (T)this;
  }

  public T withPreferredSize(int width, int height) {
    myPreferredWidth = width;
    myPreferredHeight = height;
    return (T)this;
  }

  public T withMaximumWidth(int width) {
    myMaximumWidth = width;
    return (T)this;
  }

  public T withMaximumHeight(int height) {
    myMaximumHeight = height;
    return (T)this;
  }

  public T withMinimumWidth(int width) {
    myMinimumWidth = width;
    return (T)this;
  }

  public T withMinimumHeight(int height) {
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
      if (width != null) size.width = JBUI.scale(width);
      if (height != null) size.height = JBUI.scale(height);
    }
    return size;
  }
}
