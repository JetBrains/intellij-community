// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

final class VisualOrderIterator implements Iterator<LineVisualFragment> {
  // optional params
  private final @Nullable EditorView view;
  private final @Nullable CharSequence text;
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
    int line,
    int startOffset,
    int startVisualColumn,
    float startX,
    LineBidiRun @NotNull [] runsInVisualOrder
  ) {
    this.view = view;
    this.text = view == null ? null : view.getDocument().getImmutableCharSequence();
    this.line = line;
    this.lineStartOffset = view == null ? 0 : view.getDocument().getLineStartOffset(line);
    this.runs = runsInVisualOrder;
    this.current = new LineVisualFragment(startOffset, startVisualColumn, startX);
  }

  @Override
  public boolean hasNext() {
    // run -> chunk -> fragment
    if (runIndex < runs.length) {
      LineBidiRun run = runs[runIndex];
      List<LineChunk> chunks = run.getChunks(text, lineStartOffset);
      if (chunkIndex < chunks.size()) {
        int chunkIndexRtl = run.isRtl() ? (chunks.size() - chunkIndex - 1) : chunkIndex;
        LineChunk chunk = chunks.get(chunkIndexRtl);
        if (view != null) {
          chunk.ensureLayout(view, line, run.getLevel());
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
    List<LineChunk> chunks = run.getChunks(text, lineStartOffset);
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
