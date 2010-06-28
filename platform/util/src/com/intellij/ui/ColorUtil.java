/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.ui;

import java.awt.*;

public class ColorUtil {
  private ColorUtil() {
  }

  public static Color shift(Color c, double d) {
    return new Color((int)(c.getRed() * d), (int)(c.getGreen() * d), (int)(c.getBlue() * d), c.getAlpha());
  }

  public static Color withAlpha(Color c, double a) {
    return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int)(255 * a));
  }

  public static Color withAlphaAdjustingDarkness(Color c, double d) {
    return shift(withAlpha(c, d), d);
  }
}
