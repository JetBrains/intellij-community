// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import javax.swing.*;

public class PseudoSplitter extends Splitter {
  private boolean myFirstIsFixed;
  private int myFirstFixedSize;

  public PseudoSplitter(boolean vertical) {
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
      myProportion = Math.min(0.95f, Math.max(0.05f, fixedProportion));
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
