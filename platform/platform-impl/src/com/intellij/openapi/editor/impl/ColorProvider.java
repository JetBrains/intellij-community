// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * Defines common contract for target {@link Color} retrieving.
 */
public abstract class ColorProvider {

  /**
   * @return    target {@link Color} object managed by the current holder
   */
  public abstract @NotNull Color getColor();

  /**
   * Factory method for creating color holder that always returns given {@link Color} object.
   *
   * @param color   target color to use
   * @return        color holder that uses given color all the time
   */
  public static @NotNull ColorProvider byColor(@NotNull Color color) {
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

  private static final class StaticColorHolder extends ColorProvider {

    private final Color myColor;

    StaticColorHolder(@NotNull Color color) {
      myColor = color;
    }

    @Override
    public @NotNull Color getColor() {
      return myColor;
    }
  }

  private static final class ColorSchemeBasedHolder extends ColorProvider {

    private final EditorColorsScheme myScheme;
    private final List<ColorKey> myKeys = new ArrayList<>();

    ColorSchemeBasedHolder(EditorColorsScheme scheme, ColorKey ... keys) {
      myScheme = scheme;
      myKeys.addAll(Arrays.asList(keys));
      Collections.reverse(myKeys); // Reverse collection in order to reduce removal cost
    }

    @Override
    public @NotNull Color getColor() {
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
