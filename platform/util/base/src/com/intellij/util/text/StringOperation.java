// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class StringOperation {

  private final @NotNull TextRange myRange;
  private final @NotNull CharSequence myReplacement;

  private StringOperation(@NotNull TextRange range, @NotNull CharSequence replacement) {
    myRange = range;
    myReplacement = replacement;
  }

  public @NotNull TextRange getRange() {
    return myRange;
  }

  public @NotNull CharSequence getReplacement() {
    return myReplacement;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    StringOperation operation = (StringOperation)o;
    return myRange.equals(operation.myRange) &&
           myReplacement.equals(operation.myReplacement);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myRange, myReplacement);
  }

  private static final Comparator<StringOperation> ourComparator = Comparator.comparing(
    o -> o.myRange,
    Comparator.comparingInt(TextRange::getStartOffset).thenComparingInt(TextRange::getEndOffset)
  );

  @Contract(value = "_, _ -> new", pure = true)
  public static @NotNull StringOperation replace(@NotNull TextRange range, @NotNull CharSequence replacement) {
    return new StringOperation(range, replacement);
  }

  @Contract(value = "_, _, _ -> new", pure = true)
  public static @NotNull StringOperation replace(int startOffset, int endOffset, @NotNull CharSequence replacement) {
    return replace(TextRange.create(startOffset, endOffset), replacement);
  }

  @Contract(value = "_, _ -> new", pure = true)
  public static @NotNull StringOperation insert(int offset, @NotNull CharSequence replacement) {
    return replace(offset, offset, replacement);
  }

  @Contract(value = "_ -> new", pure = true)
  public static @NotNull StringOperation remove(@NotNull TextRange range) {
    return replace(range, "");
  }

  @Contract(value = "_, _ -> new", pure = true)
  public static @NotNull StringOperation remove(int startOffset, int endOffset) {
    return remove(TextRange.create(startOffset, endOffset));
  }

  @Contract(pure = true)
  private static @NotNull List<@NotNull StringOperation> sort(@NotNull Collection<@NotNull StringOperation> operations) {
    final List<StringOperation> sorted = new ArrayList<>(operations);
    sorted.sort(ourComparator);
    return sorted;
  }

  @Contract(pure = true)
  public static @NotNull CharSequence applyOperations(@NotNull CharSequence original,
                                                      @NotNull Collection<@NotNull StringOperation> operations) {
    if (operations.isEmpty()) {
      return original;
    }
    final List<StringOperation> sorted = sort(operations);

    // The result looks like this:
    // originalChunk0 + replacement0 + ... + originalChunkN + replacementN + lastChunk

    final StringBuilder result = new StringBuilder();
    int previousRangeEnd = 0;

    // each iteration appends original chunk and following replacement
    for (StringOperation operation : sorted) {
      TextRange range = operation.myRange;
      result.append(original, previousRangeEnd, range.getStartOffset());
      result.append(operation.myReplacement);
      previousRangeEnd = range.getEndOffset(); // next original chunk starts where the current replacement ends
    }

    // last chunk
    result.append(original, previousRangeEnd, original.length());

    return result.toString();
  }
}
