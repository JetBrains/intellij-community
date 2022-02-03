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

  public JBEmptyBorder(int top, int left, int bottom, int right) {
    super(new JBInsets(top, left, bottom, right));
  }

  public JBEmptyBorder(Insets insets) {
    super(JBInsets.create(insets));
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

  public static final class JBEmptyBorderUIResource extends JBEmptyBorder implements UIResource {
    public JBEmptyBorderUIResource(JBEmptyBorder border) {
      super(0, 0, 0, 0);
      top = border.top;
      left = border.left;
      bottom = border.bottom;
      right = border.right;
    }
  }
}
