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
  private int runChunkCount;
  private @Nullable LineChunk chunk;

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
    LineChunk chunk = currentChunk();
    return chunk != null && fragmentIndex < chunk.fragmentCount();
  }

  @Override
  public LineVisualFragment next() {
    LineChunk chunk = currentChunk();
    if (chunk == null || fragmentIndex >= chunk.fragmentCount()) {
      throw new NoSuchElementException();
    }
    LineBidiRun run = runs[runIndex];
    int fragmentIndexRtl = run.isRtl() ? (chunk.fragmentCount() - fragmentIndex - 1) : fragmentIndex;
    LineFragment fragment = chunk.getFragment(fragmentIndexRtl);
    setCurrentFragment(run, fragment);
    offsetInsideRun += fragment.getLength();
    fragmentIndex++;
    if (fragmentIndex >= chunk.fragmentCount()) {
      fragmentIndex = 0;
      chunkIndex++;
      this.chunk = null;
      if (chunkIndex >= runChunkCount) {
        chunkIndex = 0;
        offsetInsideRun = 0;
        runIndex++;
      }
    }
    return current;
  }

  /**
   * Gives the chunk at the current position with its fragments, or null after the last run.
   * It lays the chunk out once, and again only when the layout cache drops its fragments.
   */
  private @Nullable LineChunk currentChunk() {
    LineChunk known = chunk;
    if (known != null && known.isLaidOut()) {
      return known;
    }
    if (runIndex >= runs.length) {
      return null;
    }
    // run -> chunk -> fragment
    LineBidiRun run = runs[runIndex];
    var chars = document == null ? null : document.luckyChars();
    List<LineChunk> chunks = run.getChunks(chars, lineStartOffset);
    if (chunkIndex >= chunks.size()) {
      return null;
    }
    int chunkIndexRtl = run.isRtl() ? (chunks.size() - chunkIndex - 1) : chunkIndex;
    LineChunk found = chunks.get(chunkIndexRtl);
    if (view != null && document != null) {
      found.ensureLayout(view, document, line, run.getLevel());
    }
    runChunkCount = chunks.size();
    chunk = found;
    return found;
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
