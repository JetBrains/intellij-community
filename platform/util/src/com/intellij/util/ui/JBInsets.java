/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class JBInsets extends Insets {
  public static final JBInsets NONE = new JBInsets(0, 0, 0, 0);
  /**
   * Creates and initializes a new <code>Insets</code> object with the
   * specified top, left, bottom, and right insets.
   *
   * @param top    the inset from the top.
   * @param left   the inset from the left.
   * @param bottom the inset from the bottom.
   * @param right  the inset from the right.
   */
  public JBInsets(int top, int left, int bottom, int right) {
    super(top, left, bottom, right);
  }
  
  public int width() {
    return left + right;
  }
  
  public int height() {
    return top + bottom;
  }

  public static JBInsets create(@NotNull Insets insets) {
    return insets instanceof JBInsets ? (JBInsets)insets
                                      : new JBInsets(insets.top, insets.left, insets.bottom, insets.right);
  }
}
