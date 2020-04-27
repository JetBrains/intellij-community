// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.stripe;

import java.awt.Color;

public final class ErrorStripe implements Comparable<ErrorStripe> {
  private final Color myColor;
  private final int myLayer;

  private ErrorStripe(Color color, int layer) {
    myColor = color;
    myLayer = layer;
  }

  public static ErrorStripe create(Color color, int layer) {
    return color == null ? null : new ErrorStripe(color, layer);
  }

  public Color getColor() {
    return myColor;
  }

  public int getLayer() {
    return myLayer;
  }

  @Override
  public int hashCode() {
    return myLayer + myColor.hashCode();
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) return true;
    if (object instanceof ErrorStripe) {
      ErrorStripe stripe = (ErrorStripe)object;
      return stripe.myLayer == myLayer && stripe.myColor.getRGB() == myColor.getRGB();
    }
    return false;
  }

  @Override
  public int compareTo(ErrorStripe stripe) {
    if (stripe == this) return 0;
    if (stripe == null || stripe.myLayer < myLayer) return -1;
    if (stripe.myLayer > myLayer) return 1;

    int thisRGB = myColor.getRGB();
    int thatRGB = stripe.myColor.getRGB();
    if (thatRGB == thisRGB) return 0;
    return thatRGB < thisRGB ? -1 : 1;
  }
}
