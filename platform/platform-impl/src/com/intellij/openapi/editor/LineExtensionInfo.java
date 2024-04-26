// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Adds informational text to the end of a line in a text editor.
 *
 * @author Konstantin Bulenkov
 */
public final class LineExtensionInfo {
  private final @NotNull @Nls String myText;
  private final @Nullable Color myColor;
  private final @Nullable EffectType myEffectType;
  private final @Nullable Color myEffectColor;
  private final @Nullable Color myBgColor;
  @JdkConstants.FontStyle private final int myFontType;

  public LineExtensionInfo(@NotNull @Nls String text,
                           @Nullable Color color,
                           @Nullable EffectType effectType,
                           @Nullable Color effectColor,
                           @JdkConstants.FontStyle int fontType) {
    myText = text;
    myColor = color;
    myEffectType = effectType;
    myEffectColor = effectColor;
    myFontType = fontType;
    myBgColor = null;
  }

  public LineExtensionInfo(@NotNull @Nls String text, @NotNull TextAttributes attr) {
    myText = text;
    myColor = attr.getForegroundColor();
    myEffectType = attr.getEffectType();
    myEffectColor = attr.getEffectColor();
    myFontType = attr.getFontType();
    myBgColor = attr.getBackgroundColor();
  }

  public @NotNull @Nls String getText() {
    return myText;
  }

  public @Nullable Color getColor() {
    return myColor;
  }

  public @Nullable Color getBgColor() {
    return myBgColor;
  }

  public @Nullable EffectType getEffectType() {
    return myEffectType;
  }

  public @Nullable Color getEffectColor() {
    return myEffectColor;
  }

  @JdkConstants.FontStyle
  public int getFontType() {
    return myFontType;
  }
}
