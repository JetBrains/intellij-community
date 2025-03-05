// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TextChunk {

  public static final TextChunk[] EMPTY_ARRAY = new TextChunk[0];

  private final AttributesFlyweight myAttributes;
  private final String myText;

  public TextChunk(@NotNull TextAttributes attributes, @NotNull String text) {
    this(attributes.getFlyweight(), text);
  }

  private TextChunk(@NotNull AttributesFlyweight attributes, @NotNull String text) {
    myAttributes = attributes;
    myText = text;
  }

  public @NotNull TextAttributes getAttributes() {
    return TextAttributes.fromFlyweight(myAttributes);
  }

  public @NotNull @NlsSafe String getText() {
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
  public @Nullable UsageType getType() {
    return null;
  }

  public @NotNull SimpleTextAttributes getSimpleAttributesIgnoreBackground() {
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
    return replaceDefaultAttributeChunksWithStrings(compactSequentialChunksWithSameAttributes(chunks));
  }

  private static @NotNull List<@NotNull TextChunk> compactSequentialChunksWithSameAttributes(@NotNull TextChunk @NotNull [] chunks) {
    if (chunks.length == 0) {
      return Collections.emptyList();
    }
    List<TextChunk> result = new ArrayList<>(chunks.length);
    StringBuilder currentText = new StringBuilder();
    AttributesFlyweight currentAttributes = null;
    for (TextChunk chunk : chunks) {
      if (currentAttributes == null) {
        currentAttributes = chunk.myAttributes;
        currentText.append(chunk.myText);
      }
      else if (currentAttributes.equals(chunk.myAttributes)) {
        currentText.append(chunk.myText);
      }
      else {
        result.add(new TextChunk(currentAttributes, currentText.toString()));
        currentAttributes = chunk.myAttributes;
        currentText.setLength(0);
        currentText.append(chunk.myText);
      }
    }
    result.add(new TextChunk(currentAttributes, currentText.toString()));
    return result;
  }

  private static @NotNull Object replaceDefaultAttributeChunksWithStrings(@NotNull List<@NotNull TextChunk> chunks) {
    AttributesFlyweight defaultFlyweight = defaultAttributes().getFlyweight();
    if (ContainerUtil.and(chunks, chunk -> !chunk.myAttributes.equals(defaultFlyweight))) {
      return chunks.size() == 1
             ? chunks.get(0)                  // TextChunk
             : chunks.toArray(EMPTY_ARRAY);   // TextChunk[] with non-default attributes
    }
    List<Object> result = ContainerUtil.map(chunks, chunk -> chunk.myAttributes.equals(defaultFlyweight) ? chunk.myText : chunk);
    return result.size() == 1
           ? result.get(0)                    // String
           : ArrayUtil.toObjectArray(result); // Object[] with String or TextChunk elements
  }

  static @NotNull TextChunk @NotNull [] inflate(@NotNull Object compact) {
    if (compact instanceof TextChunk) {
      return new TextChunk[]{(TextChunk)compact};
    }
    if (compact instanceof TextChunk[]) {
      return (TextChunk[])compact;
    }
    TextAttributes defaultAttributes = defaultAttributes();
    if (compact instanceof String) {
      return new TextChunk[]{new TextChunk(defaultAttributes, (String)compact)};
    }
    return ContainerUtil.map(
      (Object[])compact,
      stringOrChunk -> stringOrChunk instanceof String ? new TextChunk(defaultAttributes, (String)stringOrChunk) : (TextChunk)stringOrChunk
    ).toArray(EMPTY_ARRAY);
  }
}
