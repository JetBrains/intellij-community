// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.Document;
import com.intellij.util.BitUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class LineBidiRun {
  public static final LineBidiRun[] EMPTY_ARRAY = new LineBidiRun[0];
  private static final int CHUNK_CHARACTERS = 1024;

  private @Nullable List<LineChunk> chunks; // in logical order
  private final int startOffset;
  private final int endOffset;
  private final byte level;
  private int visualStartLogicalColumn;

  LineBidiRun(int length) {
    this(0, length, (byte) 0);
  }

  LineBidiRun(int startOffset, int endOffset, byte level) {
    this(startOffset, endOffset, level, 0, null);
  }

  LineBidiRun(
    int startOffset,
    int endOffset,
    byte level,
    int visualStartLogicalColumn,
    @Nullable List<LineChunk> chunks
  ) {
    this.chunks = chunks;
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    this.level = level;
    this.visualStartLogicalColumn = visualStartLogicalColumn;
  }

  boolean isRtl() {
    return BitUtil.isSet(level, 1);
  }

  int getStartOffset() {
    return startOffset;
  }

  int getEndOffset() {
    return endOffset;
  }

  byte getLevel() {
    return level;
  }

  int getVisualStartLogicalColumn() {
    return visualStartLogicalColumn;
  }

  void setVisualStartLogicalColumn(int visualStartLogicalColumn) {
    this.visualStartLogicalColumn = visualStartLogicalColumn;
  }

  boolean canMergeWith(@NotNull LineBidiRun other) {
    return level == 0 && other.level == 0;
  }

  @NotNull LineBidiRun mergeWith(@NotNull LineBidiRun other) {
    assert endOffset == other.startOffset;
    return new LineBidiRun(startOffset, other.endOffset, (byte) 0);
  }

  boolean isSingle() {
    return level == 0 && getChunkCount() == 1;
  }

  @NotNull LineChunk getFirstChunk() {
    return chunks == null ? new LineChunk(0, endOffset) : chunks.getFirst();
  }

  void forEachChunk(@NotNull Consumer<? super LineChunk> action) {
    List<LineChunk> chunks = this.chunks;
    if (chunks != null) {
      for (int i = 0; i < chunks.size(); i++) {
        action.accept(chunks.get(i));
      }
    }
  }

  // text == null && startOffsetInText == 0
  @NotNull List<LineChunk> getChunks(CharSequence text, int startOffsetInText) {
    List<LineChunk> chunks = this.chunks;
    if (chunks == null) {
      int chunkCount = getChunkCount();
      chunks = new ArrayList<>(chunkCount);
      for (int i = 0; i < chunkCount; i++) {
        int from = startOffset + i * CHUNK_CHARACTERS;
        int to = i == chunkCount - 1 ? endOffset : from + CHUNK_CHARACTERS;
        int chunkStart = alignToCodePointBoundary(text, from + startOffsetInText) - startOffsetInText;
        int chunkEnd = alignToCodePointBoundary(text, to + startOffsetInText) - startOffsetInText;
        LineChunk chunk = new LineChunk(chunkStart, chunkEnd);
        chunks.add(chunk);
      }
      this.chunks = chunks;
    }
    return chunks;
  }

  @NotNull LineBidiRun subRun(
    @NotNull EditorView view,
    int line,
    int targetStartOffset,
    int targetEndOffset,
    @Nullable Runnable quickEvaluationListener
  ) {
    assert targetStartOffset < getEndOffset();
    assert targetEndOffset > getStartOffset();
    int start = Math.max(getStartOffset(), targetStartOffset);
    int end = Math.min(getEndOffset(), targetEndOffset);
    List<LineChunk> subChunks = new SmartList<>();
    Document document = view.getDocument();
    List<LineChunk> chunks = getChunks(document.getImmutableCharSequence(), document.getLineStartOffset(line));
    for (int i = (start - getStartOffset()) / CHUNK_CHARACTERS; i < chunks.size(); i++) {
      LineChunk chunk = chunks.get(i);
      if (chunk.getEndOffset() <= start) {
        continue;
      }
      if (chunk.getStartOffset() >= end) {
        break;
      }
      LineChunk subChunk = chunk.subChunk(view, line, start, end, getLevel(), quickEvaluationListener);
      subChunks.add(subChunk);
    }
    int visualColumn = (isRtl() ? end == getEndOffset() : start == getStartOffset())
                       ? getVisualStartLogicalColumn()
                       : view.offsetToLogicalColumn(line, isRtl() ? end : start);
    return new LineBidiRun(start, end, getLevel(), visualColumn, subChunks);
  }

  private int getChunkCount() {
    return (endOffset - startOffset + CHUNK_CHARACTERS - 1) / CHUNK_CHARACTERS;
  }

  private static int alignToCodePointBoundary(CharSequence text, int offset) {
    if (offset > 0 &&
        offset < text.length() &&
        Character.isHighSurrogate(text.charAt(offset - 1)) &&
        Character.isLowSurrogate(text.charAt(offset))) {
      return offset - 1;
    }
    return offset;
  }
}
