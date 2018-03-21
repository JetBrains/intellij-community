/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
package com.intellij.util.ui;

import java.awt.*;

/**
 * Draws a 'wavy' line of 1-pixel amplitude. Instances are cached for performance reasons.
 * <p/>
 * This class is not thread-safe, it's supposed to be used in EDT only.
 *
 * @see WavePainter2D
 */
public abstract class WavePainter {
  protected WavePainter() {}

  /**
   * Paints a wave in given coordinate range. {@code y} defines the lower boundary of painted wave.
   */
  public abstract void paint(Graphics2D g, int xStart, int xEnd, int y);

  public static WavePainter forColor(Color color) {
    return WavePainter2D.forColor(color);
  }
}
