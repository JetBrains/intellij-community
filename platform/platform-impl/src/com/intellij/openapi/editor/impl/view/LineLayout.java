/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.bidi.BidiRegionsSeparator;
import com.intellij.openapi.editor.bidi.LanguageBidiRegionsSeparator;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.text.Bidi;
import java.util.*;
import java.util.List;

/**
 * Layout of a single line of document text. Consists of a series of BidiRuns, which, in turn, consist of TextFragments.
 */
class LineLayout {
  private final BidiRun[] myBidiRunsInLogicalOrder;
  private final BidiRun[] myBidiRunsInVisualOrder;
  private final float myWidth;

  /**
   * Creates a layout for a fragment of text from editor.
   */
  LineLayout(@NotNull EditorView view, 
             int startOffset, int endOffset, 
             @NotNull FontRenderContext fontRenderContext) {
    this(createFragments(view, startOffset, endOffset, fontRenderContext), false);
  }

  /**
   * Creates a layout for an arbitrary piece of text (using a common font style).
   */
  LineLayout(@NotNull EditorView view, 
             @NotNull CharSequence text, @JdkConstants.FontStyle int fontStyle, 
             @NotNull FontRenderContext fontRenderContext) {
    this(createFragments(view, text, fontStyle, fontRenderContext), true);
  }
  
  private LineLayout(@NotNull List<BidiRun> runs, boolean calculateWidth) {
    myBidiRunsInLogicalOrder = runs.toArray(new BidiRun[runs.size()]);
    myBidiRunsInVisualOrder = myBidiRunsInLogicalOrder.clone();
   
    reorderRunsVisually(myBidiRunsInVisualOrder);

    myWidth = calculateWidth ? calculateWidth() : -1;
  }

  private static void reorderRunsVisually(BidiRun[] bidiRunsInLogicalOrder) {
    if (bidiRunsInLogicalOrder.length > 1) {
      byte[] levels = new byte[bidiRunsInLogicalOrder.length];
      for (int i = 0; i < bidiRunsInLogicalOrder.length; i++) {
        levels[i] = bidiRunsInLogicalOrder[i].level;
      }
      Bidi.reorderVisually(levels, 0, bidiRunsInLogicalOrder, 0, levels.length);
    }
  }
  
  private static List<BidiRun> createFragments(@NotNull EditorView view, int lineStartOffset, int lineEndOffset,
                                                @NotNull FontRenderContext fontRenderContext) {
    if (lineEndOffset <= lineStartOffset) return Collections.emptyList();
    EditorImpl editor = view.getEditor();
    FontPreferences fontPreferences = editor.getColorsScheme().getFontPreferences();
    char[] chars = CharArrayUtil.fromSequence(editor.getDocument().getImmutableCharSequence(), lineStartOffset, lineEndOffset);
    List<BidiRun> runs = createRuns(editor, chars, lineStartOffset);
    for (BidiRun run : runs) {
      IterationState it = new IterationState(editor, lineStartOffset + run.startOffset, lineStartOffset + run.endOffset, 
                                             false, false, false, false);
      while (!it.atEnd()) {
        addFragments(run, chars, it.getStartOffset() - lineStartOffset, it.getEndOffset() - lineStartOffset,
                     it.getMergedAttributes().getFontType(), fontPreferences, fontRenderContext, view.getTabFragment());
        it.advance();
      }
      assert !run.fragments.isEmpty();
    }
    return runs;
  }

  private static List<BidiRun> createFragments(@NotNull EditorView view, @NotNull CharSequence text, 
                                                @JdkConstants.FontStyle int fontStyle, @NotNull FontRenderContext fontRenderContext) {
    if (text.length() == 0) return Collections.emptyList();
    EditorImpl editor = view.getEditor();
    FontPreferences fontPreferences = editor.getColorsScheme().getFontPreferences();
    char[] chars = CharArrayUtil.fromSequence(text);
    List<BidiRun> runs = createRuns(editor, chars, -1);
    for (BidiRun run : runs) {
      addFragments(run, chars, run.startOffset, run.endOffset, fontStyle, fontPreferences, fontRenderContext, null);
      assert !run.fragments.isEmpty();
    }
    return runs;
  }
  
  private static List<BidiRun> createRuns(EditorImpl editor, char[] text, int startOffsetInEditor) {
    int textLength = text.length;
    if (editor.myDisableRtl) return Collections.singletonList(new BidiRun((byte)0, 0, textLength));
    List<BidiRun> runs = new ArrayList<BidiRun>();
    if (startOffsetInEditor >= 0) {
      // running bidi algorithm separately for text fragments corresponding to different lexer tokens
      int lastOffset = startOffsetInEditor;
      IElementType lastToken = null;
      HighlighterIterator iterator = editor.getHighlighter().createIterator(startOffsetInEditor);
      int endOffsetInEditor = startOffsetInEditor + textLength;
      while (!iterator.atEnd() && iterator.getStart() < endOffsetInEditor) {
        IElementType currentToken = iterator.getTokenType();
        if (distinctTokens(lastToken, currentToken)) {
          int tokenStart = Math.max(iterator.getStart(), startOffsetInEditor);
          addRuns(runs, text, lastOffset - startOffsetInEditor, tokenStart - startOffsetInEditor);
          lastToken = currentToken;
          lastOffset = tokenStart;
        }
        iterator.advance();
      }
      addRuns(runs, text, lastOffset - startOffsetInEditor, endOffsetInEditor - startOffsetInEditor);
    }
    else {
      addRuns(runs, text, 0, textLength);
    }
    return runs;
  }

  private static boolean distinctTokens(@Nullable IElementType token1, @Nullable IElementType token2) {
    if (token1 == token2) return false;
    if (token1 == null || token2 == null) return true;
    if (!token1.getLanguage().is(token2.getLanguage())) return true;
    BidiRegionsSeparator separator = LanguageBidiRegionsSeparator.INSTANCE.forLanguage(token1.getLanguage());
    return separator.createBorderBetweenTokens(token1, token2);
  }
  
  private static void addRuns(List<BidiRun> runs, char[] text, int start, int end) {
    if (start >= end) return;
    Bidi bidi = new Bidi(text, start, null, 0, end - start, Bidi.DIRECTION_LEFT_TO_RIGHT);
    int runCount = bidi.getRunCount();
    for (int i = 0; i < runCount; i++) {
      addOrMergeRun(runs, new BidiRun((byte)bidi.getRunLevel(i), start + bidi.getRunStart(i), start + bidi.getRunLimit(i)));
    }
  }

  private static void addOrMergeRun(List<BidiRun> runs, BidiRun run) {
    int size = runs.size();
    if (size > 0 && runs.get(size - 1).level == 0 && run.level == 0) {
      BidiRun lastRun = runs.remove(size - 1);
      assert lastRun.endOffset == run.startOffset;
      runs.add(new BidiRun((byte)0, lastRun.startOffset, run.endOffset));
    }
    else {
      runs.add(run);
    }
  }
  
  private static void addFragments(BidiRun run, char[] text, int start, int end, int fontStyle,
                                   FontPreferences fontPreferences, FontRenderContext fontRenderContext, 
                                   @Nullable TabFragment tabFragment) {
    Font currentFont = null;
    int currentIndex = start;
    for(int i = start; i < end; i++) {
      char c = text[i];
      if (c == '\t' && tabFragment != null) {
        assert run.level == 0;
        addTextFragmentIfNeeded(run, text, currentIndex, i, currentFont, fontRenderContext, run.isRtl());
        run.fragments.add(tabFragment);
        currentFont = null;
        currentIndex = i + 1;
      }
      else {
        Font font = ComplementaryFontsRegistry.getFontAbleToDisplay(c, fontStyle, fontPreferences).getFont();
        if (!font.equals(currentFont)) {
          addTextFragmentIfNeeded(run, text, currentIndex, i, currentFont, fontRenderContext, run.isRtl());
          currentFont = font;
          currentIndex = i;
        }
      }
    }
    addTextFragmentIfNeeded(run, text, currentIndex, end, currentFont, fontRenderContext, run.isRtl());
  }
  
  private static void addTextFragmentIfNeeded(BidiRun run, char[] chars, int from, int to, Font font, 
                                              FontRenderContext fontRenderContext, boolean isRtl) {
    if (to > from) {
      assert font != null;
      run.fragments.add(new TextFragment(chars, from, to, isRtl, font, fontRenderContext));
    }
  }
  
  private float calculateWidth() {
    float x = 0;
    for (VisualFragment fragment : getFragmentsInVisualOrder(x)) {
      x = fragment.getEndX();
    }
    return x;
  }
  
  float getWidth() {
    if (myWidth < 0) throw new RuntimeException("This LineLayout instance doesn't have precalculated width");
    return myWidth;
  }
  
  Iterable<VisualFragment> getFragmentsInVisualOrder(final float startX) {
    return new Iterable<VisualFragment>() {
      @Override
      public Iterator<VisualFragment> iterator() {
        return new VisualOrderIterator(startX, 0, myBidiRunsInVisualOrder);
      }
    };
  }

  Iterable<VisualFragment> getFragmentsInVisualOrder(final float startX, final int startVisualColumn, int startOffset, int endOffset) {
    assert startOffset <= endOffset;
    final BidiRun[] runs;
    if (startOffset == endOffset) {
      runs = new BidiRun[0];
    }
    else {
      List<BidiRun> runList = new ArrayList<BidiRun>();
      for (BidiRun run : myBidiRunsInLogicalOrder) {
        if (run.endOffset <= startOffset) continue;
        if (run.startOffset >= endOffset) break;
        runList.add(run.subRun(startOffset, endOffset));
      }
      runs = runList.toArray(new BidiRun[runList.size()]);
      reorderRunsVisually(runs);
    }
    return new Iterable<VisualFragment>() {
      @Override
      public Iterator<VisualFragment> iterator() {
        return new VisualOrderIterator(startX, startVisualColumn, runs);
      }
    };
  }

  boolean isLtr() {
    return myBidiRunsInLogicalOrder.length == 0 || myBidiRunsInLogicalOrder.length == 1 && !myBidiRunsInLogicalOrder[0].isRtl();
  }
  
  boolean isRtlLocation(int offset, boolean leanForward) {
    if (offset == 0 && !leanForward) return false;
    for (BidiRun run : myBidiRunsInLogicalOrder) {
      if (offset < run.endOffset || offset == run.endOffset && !leanForward) return run.isRtl();
    }
    return false;
  }

  boolean isDirectionBoundary(int offset, boolean leanForward) {
    boolean prevIsRtl = false;
    boolean found = offset == 0 && !leanForward;
    for (BidiRun run : myBidiRunsInVisualOrder) {
      boolean curIsRtl = run.isRtl();
      if (found || offset == (curIsRtl ? run.endOffset : run.startOffset)) return curIsRtl != prevIsRtl;
      if (offset > run.startOffset && offset < run.endOffset) return false;
      found = (offset == (curIsRtl ? run.startOffset : run.endOffset));
      prevIsRtl = curIsRtl;
    }
    return prevIsRtl;
  }

  int findNearestDirectionBoundary(int offset, boolean lookForward) {
    if (lookForward) {
      boolean foundOrigin = false;
      boolean originIsRtl = false;
      for (BidiRun run : myBidiRunsInLogicalOrder) {
        if (foundOrigin) {
          if (run.isRtl() != originIsRtl) return run.startOffset;
        }
        else if (run.endOffset > offset) {
          foundOrigin = true;
          originIsRtl = run.isRtl();
        }
      }
      return originIsRtl ? myBidiRunsInLogicalOrder[myBidiRunsInLogicalOrder.length - 1].endOffset : -1;
    }
    else {
      boolean foundOrigin = false;
      boolean originIsRtl = false;
      for (int i = myBidiRunsInLogicalOrder.length - 1; i >= 0; i--) {
        BidiRun run = myBidiRunsInLogicalOrder[i];
        if (foundOrigin) {
          if (run.isRtl() != originIsRtl) return run.endOffset;
        }
        else if (run.startOffset < offset) {
          foundOrigin = true;
          originIsRtl = run.isRtl();
        }
      }
      return originIsRtl ? 0 : -1;
    }
  }

  private static class BidiRun {
    private final byte level;
    private final int startOffset;
    private final int endOffset;
    private final List<LineFragment> fragments = new ArrayList<LineFragment>(); // in logical order

    private BidiRun(byte level, int startOffset, int endOffset) {
      this.level = level;
      this.startOffset = startOffset;
      this.endOffset = endOffset;
    }
    
    private boolean isRtl() {
      return (level & 1) != 0;
    }

    private BidiRun subRun(int targetStartOffset, int targetEndOffset) {
      assert targetStartOffset < endOffset;
      assert targetEndOffset > startOffset;
      if (targetStartOffset <= startOffset && targetEndOffset >= this.endOffset) {
        return this;
      }
      int start = Math.max(startOffset, targetStartOffset);
      int end = Math.min(endOffset, targetEndOffset);
      BidiRun run = new BidiRun(level, start, end);
      int offset = startOffset;
      for (LineFragment fragment : fragments) {
        if (end <= offset) break;
        int endOffset = offset + fragment.getLength();
        if (start < endOffset) {
          run.fragments.add(fragment.subFragment(Math.max(start, offset) - offset, Math.min(end, endOffset) - offset));
        }
        offset = endOffset;
      }
      return run;
    }
  }

  private static class VisualOrderIterator implements Iterator<VisualFragment> {
    private BidiRun[] myRuns;
    private int myRunIndex = 0;
    private int myFragmentIndex = 0;
    private int myOffsetInsideRun = 0;
    private VisualFragment myFragment = new VisualFragment();

    public VisualOrderIterator(float startX, int startVisualColumn, BidiRun[] runsInVisualOrder) {
      myRuns = runsInVisualOrder;
      myFragment.startX = startX;
      myFragment.startVisualColumn = startVisualColumn;
    }

    @Override
    public boolean hasNext() {
      return myRunIndex < myRuns.length && myFragmentIndex < myRuns[myRunIndex].fragments.size();
    }

    @Override
    public VisualFragment next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      BidiRun run = myRuns[myRunIndex];

      if (myRunIndex > 0 || myFragmentIndex > 0) {
        myFragment.startLogicalColumn = myFragment.getEndLogicalColumn();
        if (myFragmentIndex == 0) {
          myFragment.startLogicalColumn += (run.isRtl() ? run.endOffset : run.startOffset) - myFragment.getEndOffset();
        }
        myFragment.startVisualColumn = myFragment.getEndVisualColumn();
        myFragment.startX = myFragment.getEndX();
      }
      
      myFragment.isRtl = run.isRtl();
      myFragment.delegate = run.fragments.get(run.isRtl() ? run.fragments.size() - 1 - myFragmentIndex : myFragmentIndex);
      myFragment.startOffset = run.isRtl() ? run.endOffset - myOffsetInsideRun : run.startOffset + myOffsetInsideRun;
      
      if (myRunIndex == 0 && myFragmentIndex == 0) {
        myFragment.startLogicalColumn = myFragment.startOffset;
      }

      myOffsetInsideRun += myFragment.getLength();
      myFragmentIndex++;
      if (myFragmentIndex >= run.fragments.size()) {
        myFragmentIndex = 0;
        myOffsetInsideRun = 0;
        myRunIndex++;
      }
      
      return myFragment;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
  
  static class VisualFragment {
    private LineFragment delegate;
    private int startOffset;
    private int startLogicalColumn;
    private int startVisualColumn;
    private float startX;
    private boolean isRtl;

    boolean isRtl() {
      return isRtl;
    }

    int getMinOffset() {
      return isRtl ? startOffset - getLength() : startOffset;
    }

    int getMaxOffset() {
      return isRtl ? startOffset : startOffset + getLength();
    }

    int getStartOffset() {
      return startOffset;
    }

    int getEndOffset() {
      return isRtl ? startOffset - getLength() : startOffset + getLength();
    }

    int getLength() {
      return delegate.getLength();
    }

    int getStartLogicalColumn() {
      return startLogicalColumn;
    }

    int getEndLogicalColumn() {
      return isRtl ? startLogicalColumn - getLogicalColumnCount() : startLogicalColumn + getLogicalColumnCount();
    }

    int getMinLogicalColumn() {
      return isRtl ? startLogicalColumn - getLogicalColumnCount() : startLogicalColumn;
    }

    int getMaxLogicalColumn() {
      return isRtl ? startLogicalColumn : startLogicalColumn + getLogicalColumnCount();
    }

    int getStartVisualColumn() {
      return startVisualColumn;
    }

    int getEndVisualColumn() {
      return startVisualColumn + getVisualColumnCount();
    }

    int getLogicalColumnCount() {
      return isRtl ? getLength() : delegate.getLogicalColumnCount(getMinLogicalColumn());
    }

    int getVisualColumnCount() {
      return delegate.getVisualColumnCount(startX);
    }

    float getStartX() {
      return startX;
    }

    float getEndX() {
      return delegate.offsetToX(startX, 0, getLength());
    }

    float getWidth() {
      return getEndX() - getStartX();
    }
    
    // column is expected to be between minLogicalColumn and maxLogicalColumn for this fragment
    int logicalToVisualColumn(int column) {
      return startVisualColumn + delegate.logicalToVisualColumn(startX, getMinLogicalColumn(), 
                                                                isRtl ? startLogicalColumn - column : column - startLogicalColumn);
    }

    // column is expected to be between startVisualColumn and endVisualColumn for this fragment
    int visualToLogicalColumn(int column) {
      int relativeLogicalColumn = delegate.visualToLogicalColumn(startX, getMinLogicalColumn(), column - startVisualColumn);
      return isRtl ? startLogicalColumn - relativeLogicalColumn : startLogicalColumn + relativeLogicalColumn;
    }

    // offset is expected to be between minOffset and maxOffset for this fragment
    float offsetToX(int offset) {
      return delegate.offsetToX(startX, 0, getRelativeOffset(offset));
    }

    // both startOffset and offset are expected to be between minOffset and maxOffset for this fragment
    float offsetToX(float startX, int startOffset, int offset) {
      return delegate.offsetToX(startX, getRelativeOffset(startOffset), getRelativeOffset(offset));
    }

    // x is expected to be between startX and endX for this fragment
    // returns array of two elements 
    // - first one is visual column, 
    // - second one is 1 if target location is closer to larger columns and 0 otherwise
    int[] xToVisualColumn(float x) {
      int[] column = delegate.xToVisualColumn(startX, x);
      column[0] += startVisualColumn;
      return column;
    }

    // column is expected to be between startVisualColumn and endVisualColumn for this fragment
    float visualColumnToX(int column) {
      return delegate.visualColumnToX(startX, column - startVisualColumn);
    }

    void draw(Graphics2D g, float x, float y) {
      delegate.draw(g, x, y, 0, getLength());
    }

    // columns are visual (relative to fragment's start)
    void draw(Graphics2D g, float x, float y, int startRelativeColumn, int endRelativeColumn) {
      delegate.draw(g, x, y, startRelativeColumn, endRelativeColumn);
    }

    private int getRelativeOffset(int offset) {
      return isRtl ? startOffset - offset : offset - startOffset;
    }
  }
}
