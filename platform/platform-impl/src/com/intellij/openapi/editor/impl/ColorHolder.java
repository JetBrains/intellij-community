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

/**
 * Defines common contract for target {@link Color} retrieving.
 *
 * @author Denis Zhdanov
 * @since Jul 2, 2010 11:12:07 AM
 */
public abstract class ColorHolder {

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
  public static ColorHolder byColor(Color color) {
    return new StaticColorHolder(color);
  }

  /**
   * Shortcut for calling {@link #byColorsScheme(EditorColorsScheme, ColorKey)} with colors scheme retrieved from the given editor.
   *
   * @param editor    target colors scheme holder
   * @param key       target color identifier within the colors scheme
   * @return          color holder that delegate target color retrieval to the colors scheme associated
   *                  with the given editor using given color key
   */
  public static ColorHolder byColorsScheme(Editor editor, ColorKey key) {
    return new ColorSchemeBasedHolder(editor.getColorsScheme(), key);
  }

  /**
   * Factory method for creating color holder that delegate target color retrieval to the given colors scheme using given color key.
   *
   * @param scheme    target colors scheme to use
   * @param key       target color identifier
   * @return          color holder that delegate target color retrieval to the given colors scheme using given color key
   */
  public static ColorHolder byColorsScheme(EditorColorsScheme scheme, ColorKey key) {
    return new ColorSchemeBasedHolder(scheme, key);
  }

  private static class StaticColorHolder extends ColorHolder {

    private final Color myColor;

    StaticColorHolder(Color color) {
      myColor = color;
    }

    @Override
    public Color getColor() {
      return myColor;
    }
  }

  private static class ColorSchemeBasedHolder extends ColorHolder {

    private final EditorColorsScheme myScheme;
    private final ColorKey myKey;

    ColorSchemeBasedHolder(EditorColorsScheme scheme, ColorKey key) {
      myScheme = scheme;
      myKey = key;
    }

    @Override
    public Color getColor() {
      return myScheme.getColor(myKey);
    }
  }
}
