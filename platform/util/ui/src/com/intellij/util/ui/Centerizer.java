// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.util.Couple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class Centerizer extends JPanel {
  public enum TYPE {
    HORIZONTAL,
    VERTICAL,
    BOTH
  }

  private TYPE type;

  public Centerizer(@NotNull JComponent comp) {
    this(comp, TYPE.BOTH);
  }

  public Centerizer(@NotNull JComponent comp, @NotNull TYPE type) {
    super(false);
    this.type = type;

    setOpaque(false);
    setBorder(null);
    setFocusable(false);

    add(comp);
  }

  private @Nullable Component getComponent() {
    if (getComponentCount() != 1) return null;
    return getComponent(0);
  }

  @Override
  public void doLayout() {
    final Component c = getComponent();
    if (c == null) return;

    final Dimension compSize = c.getPreferredSize();

    final Dimension size = getSize();

    final Couple<Integer> x = (type == TYPE.BOTH || type == TYPE.HORIZONTAL) ? getFit(compSize.width, size.width) : Couple.of(0, compSize.width);
    final Couple<Integer> y = (type == TYPE.BOTH || type == TYPE.VERTICAL) ? getFit(compSize.height, size.height) : Couple.of(0, compSize.height);

    c.setBounds(x.first.intValue(), y.first.intValue(), x.second.intValue(), y.second.intValue());
  }

  private static Couple<Integer> getFit(int compSize, int containerSize) {
    if (compSize >= containerSize) {
      return Couple.of(0, compSize);
    } else {
      final int position = containerSize / 2 - compSize / 2;
      return Couple.of(position, compSize);
    }
  }

  @Override
  public Dimension getPreferredSize() {
    return getComponent() != null ? getComponent().getPreferredSize() : super.getPreferredSize();
  }

  @Override
  public Dimension getMinimumSize() {
    return getComponent() != null ? getComponent().getMinimumSize() : super.getPreferredSize();
  }

  @Override
  public Dimension getMaximumSize() {
    return getComponent() != null ? getComponent().getMaximumSize() : super.getPreferredSize();
  }
}
