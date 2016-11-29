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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsScheme;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * Defines common contract for target {@link Color} retrieving.
 *
 * @author Denis Zhdanov
 * @since Jul 2, 2010 11:12:07 AM
 */
public abstract class ColorProvider {

  /**
   * @return    target {@link Color} object managed by the current holder
   */
  public abstract Color getColor();

  /**
   * Factory method for creating color holder that always returns given {@link Color} object.
   *
   * @param color   target color to use
   * @return        color holder that uses given color all the time
   */
  public static ColorProvider byColor(Color color) {
    return new StaticColorHolder(color);
  }

  /**
   * Shortcut for calling {@link #byColorsScheme(EditorColorsScheme, ColorKey...)} with colors scheme retrieved from the given editor.
   *
   * @param editor    target colors scheme holder
   * @param keys      target color identifiers
   * @return          color holder that delegates target color retrieval to the colors scheme associated
   *                  with the given editor using given color keys
   */
  public static ColorProvider byColorScheme(Editor editor, ColorKey ... keys) {
    return byColorsScheme(editor.getColorsScheme(), keys);
  }

  /**
   * Factory method for creating color holder that delegates target color retrieval to the given colors scheme.
   * It checks if particular color key has a value at the given scheme and falls back to the next key if any
   * (i.e. the order of given arguments is significant).
   * <p/>
   * There is a possible case that there are no colors for all of the given color keys -
   * {@link EditorColorsScheme#getDefaultForeground() default color} is used then.
   *
   * @param scheme    target colors scheme to use
   * @param keys      target color identifiers
   * @return          color holder that delegates target color retrieval to the given colors scheme using given color keys
   */
  public static ColorProvider byColorsScheme(EditorColorsScheme scheme, ColorKey ... keys) {
    return new ColorSchemeBasedHolder(scheme, keys);
  }

  private static class StaticColorHolder extends ColorProvider {

    private final Color myColor;

    StaticColorHolder(Color color) {
      myColor = color;
    }

    @Override
    public Color getColor() {
      return myColor;
    }
  }

  private static class ColorSchemeBasedHolder extends ColorProvider {

    private final EditorColorsScheme myScheme;
    private final List<ColorKey> myKeys = new ArrayList<>();

    ColorSchemeBasedHolder(EditorColorsScheme scheme, ColorKey ... keys) {
      myScheme = scheme;
      myKeys.addAll(Arrays.asList(keys));
      Collections.reverse(myKeys); // Reverse collection in order to reduce removal cost
    }

    @Override
    public Color getColor() {
      while (!myKeys.isEmpty()) {
        ColorKey key = myKeys.get(myKeys.size() - 1);
        Color result = myScheme.getColor(key);
        if (result == null || result.equals(myScheme.getDefaultForeground())) {
          myKeys.remove(myKeys.size() - 1);
        }
        else {
          return result;
        }
      }
      return myScheme.getDefaultForeground();
    }
  }
}
