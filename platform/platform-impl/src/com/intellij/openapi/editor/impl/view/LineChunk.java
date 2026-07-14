// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.FontFallbackIterator;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class LineChunk {
  private static final Logger LOG = Logger.getInstance(LineChunk.class);

  private List<LineFragment> fragments; // in logical order
  private final int startOffset;
  private final int endOffset;

  LineChunk(int startOffset, int endOffset) {
    this(startOffset, endOffset, null);
  }

  LineChunk(int startOffset, int endOffset, List<LineFragment> fragments) {
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    this.fragments = fragments;
  }

  int getStartOffset() {
    return startOffset;
  }

  int getEndOffset() {
    return endOffset;
  }

  void addFragment(@NotNull LineFragment fragment) {
    fragments.add(fragment);
  }

  int fragmentCount() {
    return fragments.size();
  }

  LineFragment getFragment(int index) {
    return fragments.get(index);
  }

  void initFragments() {
    fragments = new ArrayList<>();
  }

  void clearFragments() {
    fragments = null;
  }

  boolean isReal() {
    return true;
  }

  void ensureLayout(@NotNull EditorView view, LineBidiRun run, int line) {
    if (isReal()) {
      view.getTextLayoutCache().onChunkAccess(this);
    }
    if (fragments != null) {
      return;
    }
    assert isReal();
    initFragments();
    EditorImpl editor = view.getEditor();
    int lineStartOffset = view.getDocument().getLineStartOffset(line);
    int start = lineStartOffset + startOffset;
    int end = lineStartOffset + endOffset;
    if (LOG.isDebugEnabled()) {
      LOG.debug("Text layout for " + editor.getVirtualFile() + " (" + start + "-" + end + ")");
    }
    IterationState it = new IterationState(editor, start, end, null, false, true, false, false);
    FontFallbackIterator ffi = new FontFallbackIterator()
      .setPreferredFonts(editor.getColorsScheme().getFontPreferences())
      .setFontRenderContext(view.getFontRenderContext());
    boolean specialCharsEnabled = editor.getSettings().isShowingSpecialChars();
    char[] chars = CharArrayUtil.fromSequence(view.getDocument().getImmutableCharSequence(), start, end);
    int currentFontType = 0;
    Color currentColor = null;
    int currentStart = start;
    while (!it.atEnd()) {
      int fontType = it.getMergedAttributes().getFontType();
      Color color = it.getMergedAttributes().getForegroundColor();
      //noinspection MagicConstant
      if (fontType != currentFontType || !color.equals(currentColor)) {
        int tokenStart = it.getStartOffset();
        if (tokenStart > currentStart) {
          LineLayout.addFragments(view, run, this, chars, currentStart - start, tokenStart - start, view.getTabFragment(), specialCharsEnabled, ffi);
        }
        currentStart = tokenStart;
        currentColor = color;
        //noinspection MagicConstant
        ffi.setFontStyle(currentFontType = fontType);
      }
      it.advance();
    }
    if (end > currentStart) {
      LineLayout.addFragments(view, run, this, chars, currentStart - start, end - start, view.getTabFragment(), specialCharsEnabled, ffi);
    }
    view.getSizeManager().textLayoutPerformed(start, end);
    assert !fragments.isEmpty();
  }

  LineChunk subChunk(EditorView view, LineBidiRun run, int line, int targetStartOffset, int targetEndOffset, @Nullable Runnable quickEvaluationListener) {
    assert isReal();
    assert targetStartOffset < endOffset;
    assert targetEndOffset > startOffset;
    int start = Math.max(startOffset, targetStartOffset);
    int end = Math.min(endOffset, targetEndOffset);
    if (quickEvaluationListener != null && fragments == null) {
      quickEvaluationListener.run();
      int startColumn = view.getLogicalPositionCache().offsetToLogicalColumn(line, start);
      int endColumn = view.getLogicalPositionCache().offsetToLogicalColumn(line, end);
      ApproximationFragment approximationFragment = new ApproximationFragment(end - start, endColumn - startColumn, view.getMaxCharWidth());
      return new SyntheticLineChunk(start, end, Collections.singletonList(approximationFragment));
    }
    if (start == startOffset && end == endOffset) {
      return this;
    }
    ensureLayout(view, run, line);
    LineChunk chunk = new SyntheticLineChunk(start, end, new ArrayList<>());
    int offset = startOffset;
    for (LineFragment fragment : fragments) {
      if (end <= offset) {
        break;
      }
      int endOffset = offset + fragment.getLength();
      if (start < endOffset) {
        int subStart = Math.max(start, offset) - offset;
        int subEnd = Math.min(end, endOffset) - offset;
        LineFragment subFragment = fragment.subFragment(subStart, subEnd);
        chunk.addFragment(subFragment);
      }
      offset = endOffset;
    }
    return chunk;
  }

  private static final class SyntheticLineChunk extends LineChunk {
    private SyntheticLineChunk(int startOffset, int endOffset, List<LineFragment> fragments) {
      super(startOffset, endOffset, fragments);
    }

    @Override
    boolean isReal() {
      return false;
    }
  }
}
