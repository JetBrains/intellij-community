// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.util;

import java.awt.*;

public abstract class Axis {
  public abstract int getMinValue(Rectangle r);
  public abstract int getMaxValue(Rectangle r);
  public abstract int getSize(Rectangle r);

  public static Axis X = new Axis() {
    @Override
    public int getMinValue(Rectangle r) {
      return r.x;
    }

    @Override
    public int getMaxValue(Rectangle r) {
      return (int) r.getMaxX();
    }

    @Override
    public int getSize(Rectangle r) {
      return r.width;
    }
  };

  public static Axis Y = new Axis() {
    @Override
    public int getMinValue(Rectangle r) {
      return r.y;
    }

    @Override
    public int getMaxValue(Rectangle r) {
      return (int)r.getMaxY();
    }

    @Override
    public int getSize(Rectangle r) {
      return r.height;
    }
  };
}
