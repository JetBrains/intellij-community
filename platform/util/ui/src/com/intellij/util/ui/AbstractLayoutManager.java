// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.ui;

import java.awt.*;

public abstract class AbstractLayoutManager implements LayoutManager2 {


  @Override
  public void addLayoutComponent(final Component comp, final Object constraints) {
  }

  @Override
  public Dimension maximumLayoutSize(final Container target) {
    return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
  }

  @Override
  public float getLayoutAlignmentX(final Container target) {
    return 0;
  }

  @Override
  public float getLayoutAlignmentY(final Container target) {
    return 0;
  }

  @Override
  public void invalidateLayout(final Container target) {
  }

  @Override
  public void addLayoutComponent(final String name, final Component comp) {
  }

  @Override
  public void removeLayoutComponent(final Component comp) {
  }

  @Override
  public Dimension minimumLayoutSize(final Container parent) {
    return JBUI.emptySize();
  }

}
