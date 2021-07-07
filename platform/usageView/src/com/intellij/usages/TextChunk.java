// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.markup.AttributesFlyweight;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class TextChunk {

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

  private static TextAttributes defaultAttributes() {
    return UsageTreeColorsScheme.getInstance().getScheme().getAttributes(HighlighterColors.TEXT);
  }

  /**
   * @return TextChunk, TextChunk[], String, or Object[] with String or TextChunk elements
   */
  static @NotNull Object compact(@NotNull TextChunk @NotNull [] chunks) {
    if (chunks.length == 0) {
      return EMPTY_ARRAY;
    }
    return replaceDefaultAttributeChunksWithStrings(chunks);
  }

  private static @NotNull Object replaceDefaultAttributeChunksWithStrings(@NotNull TextChunk @NotNull [] chunks) {
    AttributesFlyweight defaultFlyweight = defaultAttributes().getFlyweight();
    if (ContainerUtil.and(chunks, chunk -> !chunk.myAttributes.equals(defaultFlyweight))) {
      return chunks;
    }
    List<Object> result = ContainerUtil.map(chunks, chunk -> chunk.myAttributes.equals(defaultFlyweight) ? chunk.myText : chunk);
    return ArrayUtil.toObjectArray(result);
  }

  static @NotNull TextChunk @NotNull [] inflate(@NotNull Object compact) {
    if (compact instanceof TextChunk[]) {
      return (TextChunk[])compact;
    }
    TextAttributes defaultAttributes = defaultAttributes();
    return ContainerUtil.map(
      (Object[])compact,
      stringOrChunk -> stringOrChunk instanceof String ? new TextChunk(defaultAttributes, (String)stringOrChunk) : (TextChunk)stringOrChunk
    ).toArray(EMPTY_ARRAY);
  }
}
