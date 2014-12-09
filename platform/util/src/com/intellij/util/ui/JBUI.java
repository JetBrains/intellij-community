/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class JBUI {
  public static int scale(int i) {
    return isHiDPI() ? 2 * i : i;
  }

  public static JBDimension size(int width, int height) {
    return new JBDimension(width, height);
  }

  public static JBInsets insets(int top, int left, int bottom, int right) {
    return new JBInsets(top, left, bottom, right);
  }

  public static Icon emptyIcon(int i) {
    return EmptyIcon.create(scale(i));
  }

  public static float scale(float f) {
    return f * scale(1);
  }

  public static JBInsets insets(Insets insets) {
    return JBInsets.create(insets);
  }

  public static boolean isHiDPI() {
    return "true".equals(System.getProperty("hidpi"));
  }

  public static class Fonts {
    public static JBFont label() {
      return JBFont.create(UIManager.getFont("Label.font"));
    }

    public static JBFont label(float size) {
      return label().deriveFont(scale(size));
    }
  }

  public static class Borders {
    public static Border empty(int top, int left, int bottom, int right) {
      return new EmptyBorder(insets(top, left, bottom, right));
    }
  }
}
