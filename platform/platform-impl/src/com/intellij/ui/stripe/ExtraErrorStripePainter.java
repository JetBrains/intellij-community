/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ui.stripe;

import java.awt.Graphics2D;

/**
 * @author Sergey.Malenkov
 */
public class ExtraErrorStripePainter extends ErrorStripePainter {
  private final ErrorStripePainter myPainter = new ErrorStripePainter(true, null);
  private boolean myGroupSwap;
  private Integer myGroupGap;

  public ExtraErrorStripePainter(boolean single) {
    super(single);
  }

  public boolean isGroupSwap() {
    return myGroupSwap;
  }

  public void setGroupSwap(boolean swap) {
    myGroupSwap = swap;
  }

  public int getGroupGap() {
    return myGroupGap != null ? myGroupGap : getMinimalThickness();
  }

  public void setGroupGap(int gap) {
    myGroupGap = gap;
  }

  public void setMaximalThickness(int thickness) {
    super.setMaximalThickness(thickness);
    myPainter.setMaximalThickness(getMaximalThickness());
  }

  public void setMinimalThickness(int thickness) {
    super.setMinimalThickness(thickness);
    myPainter.setMinimalThickness(getMinimalThickness() + getErrorStripeGap());
  }

  @Override
  public void setErrorStripeGap(int gap) {
    super.setErrorStripeGap(gap);
    myPainter.setMinimalThickness(getMinimalThickness() + getErrorStripeGap());
  }

  public void setErrorStripeCount(int count) {
    super.setErrorStripeCount(count);
    myPainter.setErrorStripeCount(count);
  }

  public void setExtraStripe(int index, ErrorStripe stripe) {
    myPainter.setErrorStripe(index, stripe);
  }

  @Override
  public void paint(Graphics2D g, int x, int y, int width, int height, Object object) {
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
