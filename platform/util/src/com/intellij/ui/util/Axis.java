/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui.util;

import java.awt.*;

/**
* @author yole
*/
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
    public int getMinValue(Rectangle r) {
      return r.y;
    }

    public int getMaxValue(Rectangle r) {
      return (int)r.getMaxY();
    }

    public int getSize(Rectangle r) {
      return r.height;
    }
  };
}
