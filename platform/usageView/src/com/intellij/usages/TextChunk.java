// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.openapi.editor.markup.AttributesFlyweight;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usages.impl.rules.UsageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TextChunk {
  public static final TextChunk[] EMPTY_ARRAY = new TextChunk[0];

  private final AttributesFlyweight myAttributes;
  private final String myText;

  public TextChunk(@NotNull TextAttributes attributes, @NotNull String text) {
    myAttributes = attributes.getFlyweight();
    myText = text;
  }

  /**
   * @deprecated use {@link #TextChunk(TextAttributes, String)}
   */
  @Deprecated
  public TextChunk(@NotNull TextAttributes attributes, @NotNull String text, @SuppressWarnings("unused") @Nullable UsageType type) {
    this(attributes, text);
  }

  @NotNull
  public TextAttributes getAttributes() {
    return TextAttributes.fromFlyweight(myAttributes);
  }

  @NotNull
  public @NlsSafe String getText() {
    return myText;
  }

  @Override
  public String toString() {
    return getText();
  }

  /**
   * @deprecated always returns {@code null}
   */
  @Deprecated
  @Nullable
  public UsageType getType() {
    return null;
  }

  @NotNull
  public SimpleTextAttributes getSimpleAttributesIgnoreBackground() {
    SimpleTextAttributes simples = SimpleTextAttributes.fromTextAttributes(getAttributes());
    simples = new SimpleTextAttributes(null, simples.getFgColor(), simples.getWaveColor(), simples.getStyle());
    return simples;
  }
}
