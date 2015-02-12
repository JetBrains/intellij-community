/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class LineExtensionInfo {
  @NotNull private final String myText;
  @Nullable private final Color myColor;
  @Nullable private final EffectType myEffectType;
  @Nullable private final Color myEffectColor;
  @JdkConstants.FontStyle private final int myFontType;

  public LineExtensionInfo(@NotNull String text,
                              @Nullable Color color,
                              @Nullable EffectType effectType,
                              @Nullable Color effectColor,
                              @JdkConstants.FontStyle int fontType) {
    myText = text;
    myColor = color;
    myEffectType = effectType;
    myEffectColor = effectColor;
    myFontType = fontType;
  }
  public LineExtensionInfo(@NotNull String text, @NotNull TextAttributes attr) {
    myText = text;
    myColor = attr.getForegroundColor();
    myEffectType = attr.getEffectType();
    myEffectColor = attr.getEffectColor();
    myFontType = attr.getFontType();
  }

  @NotNull
  public String getText() {
    return myText;
  }

  @Nullable
  public Color getColor() {
    return myColor;
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
