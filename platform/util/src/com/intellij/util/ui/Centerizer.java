/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.openapi.util.Couple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class Centerizer extends JPanel {

  public Centerizer(@NotNull JComponent comp) {
    super(false);
    setOpaque(false);
    setBorder(null);

    add(comp);
  }

  @Nullable
  private Component getComponent() {
    if (getComponentCount() != 1) return null;
    return getComponent(0);
  }

  @Override
  public void doLayout() {
    final Component c = getComponent();
    if (c == null) return;

    final Dimension compSize = c.getPreferredSize();

    final Dimension size = getSize();

    final Couple<Integer> x = getFit(compSize.width, size.width);
    final Couple<Integer> y = getFit(compSize.height, size.height);

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
  @SuppressWarnings({"ConstantConditions"})
  public Dimension getPreferredSize() {
    return getComponent() != null ? getComponent().getPreferredSize() : super.getPreferredSize();
  }

  @Override
  @SuppressWarnings({"ConstantConditions"})
  public Dimension getMinimumSize() {
    return getComponent() != null ? getComponent().getMinimumSize() : super.getPreferredSize();
  }

  @Override
  @SuppressWarnings({"ConstantConditions"})
  public Dimension getMaximumSize() {
    return getComponent() != null ? getComponent().getMaximumSize() : super.getPreferredSize();
  }
}
