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

import java.awt.Color;

/**
 * @author Sergey.Malenkov
 */
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
