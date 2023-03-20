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
public class LineExtensionInfo {
  @NotNull private final @Nls String myText;
  @Nullable private final Color myColor;
  @Nullable private final EffectType myEffectType;
  @Nullable private final Color myEffectColor;
  @Nullable private final Color myBgColor;
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

  @NotNull
  public @Nls String getText() {
    return myText;
  }

  @Nullable
  public Color getColor() {
    return myColor;
  }

  @Nullable
  public Color getBgColor() {
    return myBgColor;
  }

  @Nullable
  public EffectType getEffectType() {
    return myEffectType;
  }

  @Nullable
  public Color getEffectColor() {
    return myEffectColor;
  }

  @JdkConstants.FontStyle
  public int getFontType() {
    return myFontType;
  }
}
