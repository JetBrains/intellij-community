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

import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.RegionPainter;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.TreeSet;

/**
 * @author Sergey.Malenkov
 */
public class ErrorStripePainter extends RegionPainter.Image {
  public enum Alignment {TOP, CENTER, BOTTOM}

  private final boolean mySingleValue;
  private final Alignment myAlignment;
  private Value[] myArray;
  private int myArraySize;
  private int myImageY;
  private int myImageHeight;
  private int myMax = Integer.MAX_VALUE;
  private int myMin = 1;
  private int myGap;

  public ErrorStripePainter(boolean single) {
    this(single, Alignment.CENTER);
  }

  public ErrorStripePainter(boolean single, Alignment style) {
    mySingleValue = single;
    myAlignment = style;
  }

  public int getMaximalThickness() {
    return myMax;
  }

  public void setMaximalThickness(int thickness) {
    if (myMax != thickness) {
      myMax = thickness;
      invalidate();
    }
  }

  public int getMinimalThickness() {
    return myMin;
  }

  public void setMinimalThickness(int thickness) {
    if (thickness < 1) thickness = 1;
    if (myMin != thickness) {
      myMin = thickness;
      invalidate();
    }
  }

  public int getErrorStripeGap() {
    return myGap;
  }

  public void setErrorStripeGap(int gap) {
    if (gap < 0) gap = 0;
    if (myGap != gap) {
      myGap = gap;
      invalidate();
    }
  }

  public int findIndex(int x, int y) {
    if (0 < myImageHeight && myImageY <= y) {
      int index = myArraySize * (y - myImageY) / myImageHeight;
      if (index < myArraySize) return index;
    }
    return -1;
  }

  public int getErrorStripeCount() {
    return myArraySize;
  }

  public void setErrorStripeCount(int count) {
    if (count < 0) count = 0;
    if (myArray == null) {
      myArray = new Value[count];
    }
    else if (myArray.length < count) {
      Value[] old = myArray;
      myArray = new Value[count];
      System.arraycopy(old, 0, myArray, 0, old.length);
    }
    if (myArraySize != count) {
      myArraySize = count;
      invalidate();
    }
  }

  public boolean isModified() {
    for (int index = 0; index < myArraySize; index++) {
      Value value = myArray[index];
      if (value != null && value.myModified) return true;
    }
    return false;
  }

  public void clear() {
    for (int index = 0; index < myArraySize; index++) {
      Value value = myArray[index];
      if (value != null) value.set(null);
    }
  }

  public void clear(int index) {
    Value value = getValue(index, false);
    if (value != null) value.set(null);
  }

  public ErrorStripe getErrorStripe(int index) {
    Value value = getValue(index, false);
    return value == null ? null : value.get();
  }

  public void setErrorStripe(int index, ErrorStripe stripe) {
    Value value = getValue(index, stripe != null);
    if (value != null) value.set(stripe);
  }

  public void addErrorStripe(int index, ErrorStripe stripe) {
    Value value = getValue(index, stripe != null);
    if (value != null) value.add(stripe);
  }

  private Value getValue(int index, boolean create) {
    if (0 > index || index >= myArraySize) return null;
    if (create && null == myArray[index]) {
      myArray[index] = mySingleValue ? new SingleValue() : new ComplexValue();
    }
    return myArray[index];
  }

  private int getOffset(int height, int thickness) {
    if (height > thickness) {
      if (myAlignment == Alignment.CENTER) return (height - thickness) / 2;
      if (myAlignment == Alignment.BOTTOM) return (height - thickness);
    }
    return 0;
  }

  private void updateImage(BufferedImage image, boolean force) {
    int width = ImageUtil.getUserWidth(image);
    int height = ImageUtil.getUserHeight(image);
    Graphics2D g = image.createGraphics();

    myImageHeight = 0;
    int min = myMin + myGap;
    int max = height / myArraySize;
    if (max < min) {
      max = height / min;
      int currentIndex = 0;
      SingleValue currentValue = new SingleValue();
      for (int index = 0; index < myArraySize; index++) {
        Value value = myArray[index];
        int i = index * max / myArraySize;
        if (i > currentIndex) {
          currentValue.paint(g, 0, myImageHeight, width, min, force);
          myImageHeight += min;
          currentIndex = i;
          currentValue.myStripe = value == null ? null : value.get();
          currentValue.myModified = value != null && value.myModified;
        }
        else if (value != null) {
          ErrorStripe stripe = value.get();
          if (stripe != null && stripe.compareTo(currentValue.myStripe) < 0) {
            currentValue.myStripe = stripe;
          }
          if (value.myModified) {
            currentValue.myModified = true;
          }
        }
        if (value != null) {
          value.myModified = false;
        }
      }
      currentValue.paint(g, 0, myImageHeight, width, min, force);
      myImageHeight += min;
    }
    else {
      if (max > myMax) {
        max = Math.max(myMax, min);
      }
      for (int index = 0; index < myArraySize; index++) {
        Value value = myArray[index];
        if (value != null) {
          value.paint(g, 0, myImageHeight, width, max, force);
          value.myModified = false;
        }
        myImageHeight += max;
      }
    }
    g.dispose();
  }

  @Override
  protected void updateImage(BufferedImage image) {
    if (isModified()) updateImage(image, false);
  }

  @Override
  protected BufferedImage createImage(int width, int height) {
    BufferedImage image = myArraySize == 0 ? null : super.createImage(width, height);
    if (image != null) updateImage(image, true);
    return image;
  }

  @Override
  public void paint(Graphics2D g, int x, int y, int width, int height, Object object) {
    myImageY = y;
    super.paint(g, x, y, width, height, object);
  }

  private abstract static class Value {
    boolean myModified;

    abstract boolean set(ErrorStripe stripe);

    abstract boolean add(ErrorStripe stripe);

    abstract ErrorStripe get();

    abstract void paint(Graphics2D g, int x, int y, int width, int height);

    void paint(Graphics2D g, int x, int y, int width, int height, boolean force) {
      if (force || myModified) {
        if (!force) {
          Composite old = g.getComposite();
          g.setComposite(AlphaComposite.Clear);
          g.fillRect(x, y, width, height);
          g.setComposite(old);
        }
        paint(g, x, y, width, height);
      }
    }
  }

  private final class SingleValue extends Value {
    private ErrorStripe myStripe;

    @Override
    boolean set(ErrorStripe stripe) {
      if (stripe == null ? myStripe == null : stripe.equals(myStripe)) return false;
      myStripe = stripe;
      myModified = true;
      return true;
    }

    @Override
    boolean add(ErrorStripe stripe) {
      if (stripe == null || stripe.compareTo(myStripe) >= 0) return false;
      myStripe = stripe;
      myModified = true;
      return true;
    }

    @Override
    ErrorStripe get() {
      return myStripe;
    }

    @Override
    void paint(Graphics2D g, int x, int y, int width, int height) {
      if (myStripe != null) {
        int thickness = myAlignment != null ? myMin + myGap : height;
        y += getOffset(height, thickness);
        g.setColor(myStripe.getColor());
        g.fillRect(x, y, width, thickness - myGap);
      }
    }
  }

  private final class ComplexValue extends Value {
    private TreeSet<ErrorStripe> mySet;

    @Override
    boolean set(ErrorStripe stripe) {
      if (add(stripe)) return true;
      if (mySet == null || mySet.isEmpty()) return false;
      mySet.clear();
      myModified = true;
      return true;
    }

    @Override
    boolean add(ErrorStripe stripe) {
      if (stripe == null) return false;
      if (mySet == null) mySet = new TreeSet<>();
      mySet.add(stripe);
      myModified = true;
      return true;
    }

    @Override
    ErrorStripe get() {
      if (mySet == null) return null;
      Iterator<ErrorStripe> iterator = mySet.iterator();
      return iterator.hasNext() ? iterator.next() : null;
    }

    @Override
    void paint(Graphics2D g, int x, int y, int width, int height) {
      if (mySet != null) {
        Iterator<ErrorStripe> iterator = mySet.iterator();
        if (iterator.hasNext()) {
          int thickness = myAlignment != null ? myMin + myGap : height;
          if (thickness < height) {
            int count = Math.min(height / thickness, mySet.size());
            y += getOffset(height, thickness * count);
            do {
              g.setColor(iterator.next().getColor());
              g.fillRect(x, y, width, thickness - myGap);
              y += thickness;
            }
            while (--count > 0 && iterator.hasNext());
          }
          else {
            g.setColor(iterator.next().getColor());
            g.fillRect(x, y, width, thickness - myGap);
          }
        }
      }
    }
  }
}
