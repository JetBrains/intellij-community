// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.ex.DocumentText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

final class VisualOrderIterator implements Iterator<LineVisualFragment> {
  // optional params
  private final @Nullable EditorView view;
  private final @Nullable DocumentText document;
  private final int line;
  private final int lineStartOffset;

  // mandatory param
  private final LineBidiRun[] runs;

  private final LineVisualFragment current;
  private int runIndex;
  private int chunkIndex;
  private int fragmentIndex;
  private int offsetInsideRun;

  VisualOrderIterator(
    @Nullable EditorView view,
    @Nullable DocumentText document,
    int line,
    int startOffset,
    int startVisualColumn,
    float startX,
    LineBidiRun @NotNull [] runsInVisualOrder
  ) {
    this.view = view;
    this.document = document;
    this.line = line;
    this.lineStartOffset = document == null ? 0 : document.lineStartOffset(line);
    this.runs = runsInVisualOrder;
    this.current = new LineVisualFragment(startOffset, startVisualColumn, startX);
  }

  @Override
  public boolean hasNext() {
    // run -> chunk -> fragment
    if (runIndex < runs.length) {
      LineBidiRun run = runs[runIndex];
      var chars = document == null ? null : document.luckyChars();
      List<LineChunk> chunks = run.getChunks(chars, lineStartOffset);
      if (chunkIndex < chunks.size()) {
        int chunkIndexRtl = run.isRtl() ? (chunks.size() - chunkIndex - 1) : chunkIndex;
        LineChunk chunk = chunks.get(chunkIndexRtl);
        if (view != null && document != null) {
          chunk.ensureLayout(view, document, line, run.getLevel());
        }
        if (fragmentIndex < chunk.fragmentCount()) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public LineVisualFragment next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    LineBidiRun run = runs[runIndex];
    var chars = document == null ? null : document.luckyChars();
    List<LineChunk> chunks = run.getChunks(chars, lineStartOffset);
    int chunkIndexRtl = run.isRtl() ? (chunks.size() - chunkIndex - 1) : chunkIndex;
    LineChunk chunk = chunks.get(chunkIndexRtl);
    int fragmentIndexRtl = run.isRtl() ? (chunk.fragmentCount() - fragmentIndex - 1) : fragmentIndex;
    LineFragment fragment = chunk.getFragment(fragmentIndexRtl);
    setCurrentFragment(run, fragment);
    offsetInsideRun += fragment.getLength();
    fragmentIndex++;
    if (fragmentIndex >= chunk.fragmentCount()) {
      fragmentIndex = 0;
      chunkIndex++;
      if (chunkIndex >= chunks.size()) {
        chunkIndex = 0;
        offsetInsideRun = 0;
        runIndex++;
      }
    }
    return current;
  }

  private void setCurrentFragment(@NotNull LineBidiRun run, @NotNull LineFragment fragment) {
    boolean isFirstFragmentInRun = chunkIndex == 0 && fragmentIndex == 0;
    boolean isFirstFragment = runIndex == 0 && isFirstFragmentInRun;
    int startLogicalColumn = isFirstFragmentInRun
                             ? run.getVisualStartLogicalColumn()
                             : current.getEndLogicalColumn();
    int startVisualColumn = isFirstFragment
                            ? current.getStartVisualColumn()
                            : current.getEndVisualColumn();
    float startX = isFirstFragment
                   ? current.getStartX()
                   : current.getEndX();
    int startOffset = run.isRtl()
                      ? run.getEndOffset() - offsetInsideRun
                      : run.getStartOffset() + offsetInsideRun;
    current.setState(
      fragment,
      startOffset,
      startLogicalColumn,
      startVisualColumn,
      startX,
      run.isRtl()
    );
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
