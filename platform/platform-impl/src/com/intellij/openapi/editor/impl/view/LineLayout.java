// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.FontFallbackIterator;
import com.intellij.util.DocumentInternalUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.JdkConstants.FontStyle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.Bidi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Layout of a single line of document text. Consists of a series of BidiRuns, which, in turn, consist of TextFragments.
 * TextFragments within BidiRun are grouped into Chunks for performance reasons, glyph layout is performed per-Chunk, and only
 * for required Chunks.
 */
sealed abstract class LineLayout permits SingleChunkLayout, MultiChunkLayout, LineLayoutWithSize {

  /**
   * Creates a layout for a fragment of text from editor.
   * <p>
   * Reads a logical line from the editor document, uses editor highlighting/attributes lazily, and can skip bidi analysis
   */
  static @NotNull LineLayout createForDocumentLine(@NotNull EditorView view, int line, boolean skipBidiLayout) {
    List<LineBidiRun> runs = createRunsForDocumentLine(view, line, skipBidiLayout);
    return createLayout(view, runs, null, line);
  }

  /**
   * Creates a layout for an arbitrary piece of text (using a common font style).
   * <p>
   * Lays out supplied text eagerly with one font style, performs bidi independently of document highlighting, and precomputes width
   */
  static @NotNull LineLayout createForStandaloneText(@NotNull EditorView view, @NotNull CharSequence text, @FontStyle int fontStyle) {
    List<LineBidiRun> runs = createRunsForStandaloneText(view, text, fontStyle);
    LineLayout delegate = createLayout(view, runs, text, 0);
    return new LineLayoutWithSize(delegate);
  }

  abstract Stream<LineChunk> getChunksInLogicalOrder();

  abstract boolean isLtr();

  abstract boolean isRtlLocation(int offset, boolean leanForward);

  abstract int findNearestDirectionBoundary(int offset, boolean lookForward);

  abstract LineBidiRun[] getRunsInLogicalOrder();

  abstract LineBidiRun[] getRunsInVisualOrder();

  float getWidth() {
    throw new RuntimeException("This LineLayout instance doesn't have precalculated width");
  }

  Iterable<LineVisualFragment> getFragmentsInVisualOrder(float startX) {
    return () -> new VisualOrderIterator(null, 0, startX, 0, 0, getRunsInVisualOrder());
  }

  /**
   * If {@code quickEvaluationListener} is provided, quick approximate iteration becomes enabled, listener will be invoked
   * if approximation will in fact be used during width calculation.
   */
  Iterator<LineVisualFragment> getFragmentsInVisualOrder(
    @NotNull EditorView view,
    int line,
    float startX,
    int startVisualColumn,
    int startOffset,
    int endOffset,
    @Nullable Runnable quickEvaluationListener
  ) {
    view.getEditor().assertOrDumpState(startOffset <= endOffset, "startOffset must be less or equal to endOffset");
    Document document = view.getDocument();
    int lineStartOffset = document.getLineStartOffset(line);
    assert !DocumentUtil.isInsideSurrogatePair(document, lineStartOffset + startOffset);
    assert !DocumentUtil.isInsideSurrogatePair(document, lineStartOffset + endOffset);
    LineBidiRun[] runs;
    if (startOffset == endOffset) {
      runs = LineBidiRun.EMPTY_ARRAY;
    } else {
      List<LineBidiRun> runList = new ArrayList<>();
      for (LineBidiRun run : getRunsInLogicalOrder()) {
        if (run.getEndOffset() <= startOffset) {
          continue;
        }
        if (run.getStartOffset() >= endOffset) {
          break;
        }
        LineBidiRun subRun = run.subRun(view, line, startOffset, endOffset, quickEvaluationListener);
        runList.add(subRun);
      }
      runs = runList.toArray(LineBidiRun.EMPTY_ARRAY);
    }
    reorderRunsVisually(runs);
    return new VisualOrderIterator(view, line, startX, startVisualColumn, startOffset, runs);
  }

  private static List<LineBidiRun> createRunsForDocumentLine(@NotNull EditorView view, int line, boolean skipBidiLayout) {
    Document document = view.getDocument();
    int lineStartOffset = document.getLineStartOffset(line);
    int lineEndOffset = document.getLineEndOffset(line);
    if (lineEndOffset <= lineStartOffset) {
      return Collections.emptyList();
    }
    if (skipBidiLayout) {
      return Collections.singletonList(new LineBidiRun(lineEndOffset - lineStartOffset));
    }
    CharSequence text = document.getImmutableCharSequence().subSequence(lineStartOffset, lineEndOffset);
    char[] chars = CharArrayUtil.fromSequence(text);
    return BidiRunUtil.createRuns(view, chars, lineStartOffset);
  }

  private static List<LineBidiRun> createRunsForStandaloneText(@NotNull EditorView view, @NotNull CharSequence text, @FontStyle int fontStyle) {
    if (text.isEmpty()) {
      return Collections.emptyList();
    }
    FontFallbackIterator ffi = new FontFallbackIterator()
      .setPreferredFonts(view.getEditor().getColorsScheme().getFontPreferences())
      .setFontStyle(fontStyle)
      .setFontRenderContext(view.getFontRenderContext());
    char[] chars = CharArrayUtil.fromSequence(text);
    List<LineBidiRun> runs = BidiRunUtil.createRuns(view, chars, -1);
    for (LineBidiRun run : runs) {
      for (LineChunk chunk : run.getChunks(text, 0)) {
        chunk.addFragments(chars, run.getLevel(), run.isRtl(), ffi, view);
      }
    }
    return runs;
  }

  private static @NotNull LineLayout createLayout(
    @NotNull EditorView view,
    @NotNull List<LineBidiRun> runs,
    @Nullable CharSequence text, // if null, use line
    int line
  ) {
    if (runs.isEmpty()) {
      return new SingleChunkLayout(null);
    }
    if (runs.size() == 1) {
      LineBidiRun run = runs.getFirst();
      if (run.getLevel() == 0 && run.getChunkCount() == 1) {
        return new SingleChunkLayout(run.getFirstChunk());
      }
    }
    LineBidiRun[] runArray = new LineBidiRun[runs.size()];
    int prevColumn = 0;
    for (int i = 0; i < runs.size(); i++) {
      LineBidiRun run = runs.get(i);
      assert i == 0 || run.getStartOffset() == runs.get(i - 1).getEndOffset();
      int startColumn = prevColumn;
      int endColumn = text == null
                      ? view.getLogicalPositionCache().offsetToLogicalColumn(line, run.getEndOffset())
                      : DocumentInternalUtil.calcLogicalColumn(text, run.getStartOffset(), prevColumn, run.getEndOffset(), view.getTabSize());
      run.setVisualStartLogicalColumn(run.isRtl() ? endColumn : startColumn);
      prevColumn = endColumn;
      runArray[i] = run;
    }
    LineBidiRun[] runsInVisualOrder = runArray.length > 1 ? runArray.clone() : runArray;
    reorderRunsVisually(runsInVisualOrder);
    return new MultiChunkLayout(runArray, runsInVisualOrder);
  }

  // runs are supposed to be in logical order initially
  private static void reorderRunsVisually(LineBidiRun @NotNull [] bidiRuns) {
    if (bidiRuns.length <= 1) {
      return;
    }
    byte[] levels = new byte[bidiRuns.length];
    for (int i = 0; i < bidiRuns.length; i++) {
      levels[i] = bidiRuns[i].getLevel();
    }
    Bidi.reorderVisually(levels, 0, bidiRuns, 0, levels.length);
  }
}
