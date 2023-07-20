// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class JBEmptyBorder extends EmptyBorder {
  static final JBEmptyBorder SHARED_EMPTY_INSTANCE = new JBEmptyBorder(0);

  private final JBValue topValue;
  private final JBValue leftValue;
  private final JBValue bottomValue;
  private final JBValue rightValue;

  public JBEmptyBorder(int top, int left, int bottom, int right) {
    super(new JBInsets(top, left, bottom, right));
    topValue = JBUI.value(top);
    leftValue = JBUI.value(left);
    bottomValue = JBUI.value(bottom);
    rightValue = JBUI.value(right);
    refreshInsets();
  }

  public JBEmptyBorder(Insets insets) {
    this(insets.top, insets.left, insets.bottom, insets.right);
  }

  public JBEmptyBorder(int offset) {
    this(offset, offset, offset, offset);
  }

  public JBEmptyBorderUIResource asUIResource() {
    return new JBEmptyBorderUIResource(this);
  }

  public <T extends JComponent> T wrap(T component) {
    component.setBorder(this);
    return component;
  }

  @Override
  public Insets getBorderInsets() {
    refreshInsets();
    return super.getBorderInsets();
  }

  @Override
  public Insets getBorderInsets(Component c) {
    refreshInsets();
    return super.getBorderInsets(c);
  }

  @Override
  public Insets getBorderInsets(Component c, Insets insets) {
    refreshInsets();
    return super.getBorderInsets(c, insets);
  }

  protected void refreshInsets() {
    top = topValue.get();
    left = leftValue.get();
    bottom = bottomValue.get();
    right = rightValue.get();
  }

  public static final class JBEmptyBorderUIResource extends JBEmptyBorder implements UIResource {
    public JBEmptyBorderUIResource(JBEmptyBorder border) {
      super(0, 0, 0, 0);
      border.refreshInsets();
      top = border.top;
      left = border.left;
      bottom = border.bottom;
      right = border.right;
    }
  }
}
