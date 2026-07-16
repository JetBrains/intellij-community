// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

final class VisualOrderIterator implements Iterator<LineVisualFragment> {
  private final EditorView myView;
  private final CharSequence myText;
  private final int myLine;
  private final int myLineStartOffset;
  private final LineBidiRun[] myRuns;
  private final LineVisualFragment myFragment;
  private int myRunIndex;
  private int myChunkIndex;
  private int myFragmentIndex;
  private int myOffsetInsideRun;

  VisualOrderIterator(EditorView view, int line, float startX, int startVisualColumn, int startOffset, LineBidiRun[] runsInVisualOrder) {
    myView = view;
    myText = view == null ? null : view.getDocument().getImmutableCharSequence();
    myLine = line;
    myLineStartOffset = view == null ? 0 : view.getDocument().getLineStartOffset(line);
    myRuns = runsInVisualOrder;
    myFragment = new LineVisualFragment(startOffset, startVisualColumn, startX);
  }

  @Override
  public boolean hasNext() {
    if (myRunIndex >= myRuns.length) {
      return false;
    }
    LineBidiRun run = myRuns[myRunIndex];
    List<LineChunk> chunks = run.getChunks(myText, myLineStartOffset);
    if (myChunkIndex >= chunks.size()) {
      return false;
    }
    int chunkIndex = run.isRtl() ? (chunks.size() - myChunkIndex - 1) : myChunkIndex;
    LineChunk chunk = chunks.get(chunkIndex);
    if (myView != null) {
      chunk.ensureLayout(myView, myLine, run.getLevel(), run.isRtl());
    }
    return myFragmentIndex < chunk.fragmentCount();
  }

  @Override
  public LineVisualFragment next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    LineBidiRun run = myRuns[myRunIndex];
    boolean isFirstRun = myRunIndex == 0 && myChunkIndex == 0 && myFragmentIndex == 0;
    int startLogicalColumn;
    int startVisualColumn;
    float startX;
    if (isFirstRun) {
      startLogicalColumn = run.getVisualStartLogicalColumn();
      startVisualColumn = myFragment.getStartVisualColumn();
      startX = myFragment.getStartX();
    } else {
      startLogicalColumn = myChunkIndex == 0 && myFragmentIndex == 0
                           ? run.getVisualStartLogicalColumn()
                           : myFragment.getEndLogicalColumn();
      startVisualColumn = myFragment.getEndVisualColumn();
      startX = myFragment.getEndX();
    }
    List<LineChunk> chunks = run.getChunks(myText, myLineStartOffset);
    int chunkIndex = run.isRtl() ? chunks.size() - 1 - myChunkIndex : myChunkIndex;
    LineChunk chunk = chunks.get(chunkIndex);
    int fragmentIndex = run.isRtl() ? chunk.fragmentCount() - 1 - myFragmentIndex : myFragmentIndex;
    LineFragment delegate = chunk.getFragment(fragmentIndex);
    int startOffset = run.isRtl() ? run.getEndOffset() - myOffsetInsideRun : run.getStartOffset() + myOffsetInsideRun;
    myFragment.setState(
      delegate,
      startOffset,
      startLogicalColumn,
      startVisualColumn,
      startX,
      run.isRtl()
    );
    myOffsetInsideRun += myFragment.getLength();
    myFragmentIndex++;
    if (myFragmentIndex >= chunk.fragmentCount()) {
      myFragmentIndex = 0;
      myChunkIndex++;
      if (myChunkIndex >= chunks.size()) {
        myChunkIndex = 0;
        myOffsetInsideRun = 0;
        myRunIndex++;
      }
    }
    return myFragment;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
