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

import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.IterationState2;
import com.intellij.util.text.CharArrayUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

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
  private final float myMaxX;

  /**
   * Creates a layout for a fragment of text from editor.
   */
  LineLayout(@NotNull EditorView view, int startOffset, int endOffset, 
             @NotNull FontRenderContext fontRenderContext, float startX) {
    this(createFragments(view, startOffset, endOffset, fontRenderContext), startX);
  }

  /**
   * Creates a layout for an arbitrary piece of text (using a common font style).
   */
  LineLayout(@NotNull EditorView view, @NotNull CharSequence text, @JdkConstants.FontStyle int fontStyle, 
             @NotNull FontRenderContext fontRenderContext, float startX) {
    this(createFragments(view, text, fontStyle, fontRenderContext), startX);
  }
  
  private LineLayout(@NotNull List<BidiRun> runs, float startX) {
    myBidiRunsInLogicalOrder = runs.toArray(new BidiRun[runs.size()]);
    myBidiRunsInVisualOrder = myBidiRunsInLogicalOrder.clone();
    
    if (myBidiRunsInLogicalOrder.length > 1) {
      byte[] levels = new byte[myBidiRunsInLogicalOrder.length];
      for (int i = 0; i < myBidiRunsInLogicalOrder.length; i++) {
        levels[i] = myBidiRunsInLogicalOrder[i].level;
      }
      Bidi.reorderVisually(levels, 0, myBidiRunsInVisualOrder, 0, levels.length);
    }
    
    myMaxX = calculateMaxX(startX);
  }
  
  private static List<BidiRun> createFragments(@NotNull EditorView view, int lineStartOffset, int lineEndOffset,
                                                @NotNull FontRenderContext fontRenderContext) {
    if (lineEndOffset <= lineStartOffset) return Collections.emptyList();
    EditorImpl editor = view.getEditor();
    FontPreferences fontPreferences = editor.getColorsScheme().getFontPreferences();
    char[] chars = CharArrayUtil.fromSequence(editor.getDocument().getImmutableCharSequence(), lineStartOffset, lineEndOffset);
    List<BidiRun> runs = createRuns(chars);
    for (BidiRun run : runs) {
      IterationState2 it = new IterationState2(editor, lineStartOffset + run.startOffset, lineStartOffset + run.endOffset, false);
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
    List<BidiRun> runs = createRuns(chars);
    for (BidiRun run : runs) {
      addFragments(run, chars, run.startOffset, run.endOffset, fontStyle, fontPreferences, fontRenderContext,
                   view.getTabFragment());
      assert !run.fragments.isEmpty();
    }
    return runs;
  }
  
  private static List<BidiRun> createRuns(char[] text) {
    Bidi bidi = new Bidi(text, 0, null, 0, text.length, Bidi.DIRECTION_LEFT_TO_RIGHT);
    int runCount = bidi.getRunCount();
    List<BidiRun> runs = new ArrayList<BidiRun>(runCount);
    for (int i = 0; i < runCount; i++) {
      runs.add(new BidiRun((byte)bidi.getRunLevel(i), bidi.getRunStart(i), bidi.getRunLimit(i)));
    }
    return runs;
  }
  
  private static void addFragments(BidiRun run, char[] text, int start, int end, int fontStyle,
                                   FontPreferences fontPreferences, FontRenderContext fontRenderContext, TabFragment tabFragment) {
    Font currentFont = null;
    int currentIndex = start;
    for(int i = start; i < end; i++) {
      char c = text[i];
      if (c == '\t') {
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
  
  private float calculateMaxX(float x) {
    for (Fragment fragment : getFragmentsInVisualOrder()) {
      x = fragment.advance(x);
    }
    return x;
  }
  
  float getMaxX() {
    return myMaxX;
  }
  
  Iterable<Fragment> getFragmentsInLogicalOrder() {
    return new Iterable<Fragment>() {
      @Override
      public Iterator<Fragment> iterator() {
        return new LogicalOrderIterator();
      }
    };
  }

  Iterable<Fragment> getFragmentsInVisualOrder() {
    return new Iterable<Fragment>() {
      @Override
      public Iterator<Fragment> iterator() {
        return new VisualOrderIterator();
      }
    };
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
  }
  
  private class LogicalOrderIterator implements Iterator<Fragment> {
    private int myRunIndex = 0;
    private int myFragmentIndex = 0;
    private int myOffset = 0;
    private Fragment myFragment = new Fragment();
    
    @Override
    public boolean hasNext() {
      return myRunIndex < myBidiRunsInLogicalOrder.length && myFragmentIndex < myBidiRunsInLogicalOrder[myRunIndex].fragments.size();
    }

    @Override
    public Fragment next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      BidiRun run = myBidiRunsInLogicalOrder[myRunIndex];
      myFragment.isRtl = run.isRtl();
      myFragment.delegate = run.fragments.get(myFragmentIndex);
      myFragment.startOffset = myOffset;
      
      myOffset += myFragment.delegate.getLength();
      myFragmentIndex++;
      if (myFragmentIndex >= run.fragments.size()) {
        myFragmentIndex = 0;
        myRunIndex++;
      }
      return myFragment;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private class VisualOrderIterator implements Iterator<Fragment> {
    private int myRunIndex = 0;
    private int myFragmentIndex = 0;
    private int myOffsetInsideRun = 0;
    private Fragment myFragment = new Fragment();

    @Override
    public boolean hasNext() {
      return myRunIndex < myBidiRunsInVisualOrder.length && myFragmentIndex < myBidiRunsInVisualOrder[myRunIndex].fragments.size();
    }

    @Override
    public Fragment next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      BidiRun run = myBidiRunsInLogicalOrder[myRunIndex];
      myFragment.isRtl = run.isRtl();
      myFragment.delegate = run.fragments.get(run.isRtl() ? run.fragments.size() - 1 - myFragmentIndex : myFragmentIndex);
      myFragment.startOffset = run.isRtl() ? run.endOffset - myOffsetInsideRun - myFragment.delegate.getLength() 
                                           : run.startOffset + myOffsetInsideRun;
      
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

  static class Fragment implements LineFragment {
    private LineFragment delegate;
    private int startOffset;
    private boolean isRtl;

    int getStartOffset() {
      return startOffset;
    }

    int getEndOffset() {
      return startOffset + getLength();
    }

    int getVisualStartOffset() {
      return isRtl ? getEndOffset() : getStartOffset();
    }

    int getVisualEndOffset() {
      return isRtl ? getStartOffset() : getEndOffset();
    }

    boolean isRtl() {
      return isRtl;
    }

    @Override
    public int getLength() {
      return delegate.getLength();
    }

    @Override
    public int getColumnCount(float startX) {
      return delegate.getColumnCount(startX);
    }

    @Override
    public void draw(Graphics2D g, float x, float y, int startOffset, int endOffset) {
      delegate.draw(g, x, y, startOffset, endOffset);
    }

    @Override
    public float offsetToX(float startX, int startOffset, int offset) {
      return delegate.offsetToX(startX, startOffset, offset);
    }

    @Override
    public int offsetToColumn(float startX, int offset) {
      return delegate.offsetToColumn(startX, offset);
    }

    @Override
    public int columnToOffset(float startX, int column) {
      return delegate.columnToOffset(startX, column);
    }

    @Override
    public int xToColumn(float startX, float x) {
      return delegate.xToColumn(startX, x);
    }

    @Override
    public float columnToX(float startX, int column) {
      return delegate.columnToX(startX, column);
    }
    
    float advance(float startX) {
      return delegate.offsetToX(startX, 0, delegate.getLength()); 
    }

    int absoluteToRelativeOffset(int offset) {
      return isRtl() ? getEndOffset() - offset : offset - getStartOffset();
    }
    
    float absoluteOffsetToX(float startX, int offset) {
      return offsetToX(startX, 0, absoluteToRelativeOffset(offset));
    }
  }
}
