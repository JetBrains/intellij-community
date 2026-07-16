// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.Document;
import com.intellij.util.BitUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

final class LineBidiRun {
  public static final LineBidiRun[] EMPTY_ARRAY = new LineBidiRun[0];
  private static final int CHUNK_CHARACTERS = 1024;

  private @Nullable List<LineChunk> chunks; // in logical order
  private final int startOffset;
  private final int endOffset;
  private final byte level;
  private int visualStartLogicalColumn;

  LineBidiRun(int length) {
    this(0, length, (byte) 0, null);
  }

  LineBidiRun(int startOffset, int endOffset, byte level, @Nullable List<LineChunk> chunks) {
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    this.level = level;
    this.chunks = chunks;
  }

  boolean isRtl() {
    return BitUtil.isSet(level, 1);
  }

  @NotNull LineChunk getFirstChunk() {
    return chunks == null ? new LineChunk(0, endOffset) : chunks.getFirst();
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

  int getChunkCount() {
    return (endOffset - startOffset + CHUNK_CHARACTERS - 1) / CHUNK_CHARACTERS;
  }

  Stream<LineChunk> chunkStream() {
    return chunks == null ? Stream.empty() : chunks.stream();
  }

  @NotNull List<LineChunk> getChunks(CharSequence text, int startOffsetInText) {
    List<LineChunk> c = chunks;
    if (c == null) {
      int chunkCount = getChunkCount();
      c = new ArrayList<>(chunkCount);
      for (int i = 0; i < chunkCount; i++) {
        int from = startOffset + i * CHUNK_CHARACTERS;
        int to = i == chunkCount - 1 ? endOffset : from + CHUNK_CHARACTERS;
        int chunkStart = alignToCodePointBoundary(text, from + startOffsetInText) - startOffsetInText;
        int chunkEnd = alignToCodePointBoundary(text, to + startOffsetInText) - startOffsetInText;
        LineChunk chunk = new LineChunk(chunkStart, chunkEnd);
        c.add(chunk);
      }
      chunks = c;
    }
    return c;
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
      LineChunk subChunk = chunk.subChunk(view, line, start, end, getLevel(), isRtl(), quickEvaluationListener);
      subChunks.add(subChunk);
    }
    LineBidiRun subRun = new LineBidiRun(start, end, getLevel(), subChunks);
    int visualColumn = (subRun.isRtl() ? end == getEndOffset() : start == getStartOffset())
                       ? getVisualStartLogicalColumn()
                       : view.getLogicalPositionCache().offsetToLogicalColumn(line, subRun.isRtl() ? end : start);
    subRun.setVisualStartLogicalColumn(visualColumn);
    return subRun;
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
