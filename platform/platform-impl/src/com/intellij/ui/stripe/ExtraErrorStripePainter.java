// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.stripe;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

@ApiStatus.Internal
public final class ExtraErrorStripePainter extends ErrorStripePainter {
  private final ErrorStripePainter myPainter = new ErrorStripePainter(true, null);
  private boolean myGroupSwap;
  private Integer myGroupGap;

  public ExtraErrorStripePainter(boolean single) {
    super(single);
  }

  public boolean isGroupSwap() {
    return myGroupSwap;
  }

  void setGroupSwap(boolean swap) {
    myGroupSwap = swap;
  }

  public int getGroupGap() {
    return myGroupGap != null ? myGroupGap : getMinimalThickness();
  }

  public void setGroupGap(int gap) {
    myGroupGap = gap;
  }

  @Override
  public void setMaximalThickness(int thickness) {
    super.setMaximalThickness(thickness);
    myPainter.setMaximalThickness(getMaximalThickness());
  }

  @Override
  public void setMinimalThickness(int thickness) {
    super.setMinimalThickness(thickness);
    myPainter.setMinimalThickness(getMinimalThickness() + getErrorStripeGap());
  }

  @Override
  public void setErrorStripeGap(int gap) {
    super.setErrorStripeGap(gap);
    myPainter.setMinimalThickness(getMinimalThickness() + getErrorStripeGap());
  }

  @Override
  public void setErrorStripeCount(int count) {
    super.setErrorStripeCount(count);
    myPainter.setErrorStripeCount(count);
  }

  public void setExtraStripe(int index, ErrorStripe stripe) {
    myPainter.setErrorStripe(index, stripe);
  }

  @Override
  public void paint(@NotNull Graphics2D g, int x, int y, int width, int height, Object object) {
    int min = getMinimalThickness();
    int gap = myGroupGap == null ? min : myGroupGap;
    int pos = x;
    if (myGroupSwap) {
      pos += width - min;
    }
    else {
      x += gap + min;
    }
    myPainter.paint(g, pos, y, min, height, object);
    super.paint(g, x, y, width - gap - min, height, object);
  }
}
