// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.ui.Splitter;
import com.intellij.util.MathUtil;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.JComponent;

@ApiStatus.Internal
class PseudoSplitter extends Splitter {
  private boolean myFirstIsFixed;
  private int myFirstFixedSize;

  PseudoSplitter(boolean vertical) {
    super(vertical);
    myFirstIsFixed = false;
  }

  private int getSizeForComp(final JComponent component) {
    return getOrientation() ? component.getHeight() : component.getWidth();
  }

  public void fixFirst(final float proportion) {
    assert getFirstComponent() != null;
    int total = getSizeForComp(this);
    myFirstFixedSize = (int)(proportion * (total - getDividerWidth()));
    myFirstIsFixed = true;
  }

  public void fixFirst() {
    assert getFirstComponent() != null;
    myFirstFixedSize = getSizeForComp(getFirstComponent());
    myFirstIsFixed = true;
  }

  public void freeAll() {
    myFirstIsFixed = false;
  }

  @Override
  public void doLayout() {
    int total = getSizeForComp(this);
    if (myFirstIsFixed) {
      float fixedProportion = ((float)myFirstFixedSize) / (total - getDividerWidth());
      myProportion = MathUtil.clamp(fixedProportion, 0.05f, 0.95f);
    }
    super.doLayout();
  }

  @Override
  public void setProportion(float proportion) {
    boolean firstIsFixed = myFirstIsFixed;
    myFirstIsFixed = false;
    super.setProportion(proportion);

    int total = getSizeForComp(this);
    if (firstIsFixed) {
      myFirstFixedSize = (int) (myProportion * (total - getDividerWidth()));
      myFirstIsFixed = true;
    }
  }
}
