/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.bidi.BidiRegionsSeparator;
import com.intellij.openapi.editor.bidi.LanguageBidiRegionsSeparator;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.FontFallbackIterator;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.BitUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.text.CharArrayUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.text.Bidi;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;

/**
 * Layout of a single line of document text. Consists of a series of BidiRuns, which, in turn, consist of TextFragments.
 * TextFragments within BidiRun are grouped into Chunks for performance reasons, glyph layout is performed per-Chunk, and only 
 * for required Chunks.
 */
abstract class LineLayout {
  private static final Logger LOG = Logger.getInstance(LineLayout.class);

  private LineLayout() {}
  
  /**
   * Creates a layout for a fragment of text from editor.
   */
  @NotNull
  static LineLayout create(@NotNull EditorView view, int line, boolean skipBidiLayout) {
    List<BidiRun> runs = createFragments(view, line, skipBidiLayout);
    return createLayout(view, runs, null, line);
  }

  /**
   * Creates a layout for an arbitrary piece of text (using a common font style).
   */
  @NotNull
  static LineLayout create(@NotNull EditorView view, @NotNull CharSequence text, @JdkConstants.FontStyle int fontStyle) {
    List<BidiRun> runs = createFragments(view, text, fontStyle);
    LineLayout delegate = createLayout(view, runs, text, 0);
    return new WithSize(delegate);
  }

  private static LineLayout createLayout(@NotNull EditorView view, @NotNull List<BidiRun> runs, @Nullable CharSequence text, int line) {
    if (runs.isEmpty()) return new SingleChunk(null);
    if (runs.size() == 1) {
      BidiRun run = runs.get(0);
      if (run.level == 0 && run.getChunkCount() == 1) {
        Chunk chunk = run.chunks == null ? new Chunk(0, run.endOffset) : run.chunks[0];
        return new SingleChunk(chunk);
      }
    }
    BidiRun[] runArray = new BidiRun[runs.size()];
    int prevColumn = 0;
    for (int i = 0; i < runs.size(); i++) {
      BidiRun run = runs.get(i);
      assert i == 0 || run.startOffset == runs.get(i - 1).endOffset;
      int startColumn = prevColumn;
      int endColumn = text == null
                      ? view.getLogicalPositionCache().offsetToLogicalColumn(line, run.endOffset)
                      : LogicalPositionCache.calcColumn(text, run.startOffset, prevColumn, run.endOffset, view.getTabSize());
      run.visualStartLogicalColumn = run.isRtl() ? endColumn : startColumn;
      prevColumn = endColumn;
      runArray[i] = run;
    }
    return new MultiChunk(runArray);
  }

  // runs are supposed to be in logical order initially
  private static void reorderRunsVisually(BidiRun[] bidiRuns) {
    byte[] levels = new byte[bidiRuns.length];
    for (int i = 0; i < bidiRuns.length; i++) {
      levels[i] = bidiRuns[i].level;
    }
    Bidi.reorderVisually(levels, 0, bidiRuns, 0, levels.length);
  }

  static boolean isBidiLayoutRequired(@NotNull CharSequence text) {
    char[] chars = CharArrayUtil.fromSequence(text);
    return Bidi.requiresBidi(chars, 0, chars.length);
  }
  
  private static List<BidiRun> createFragments(@NotNull EditorView view, int line, boolean skipBidiLayout) {
    Document document = view.getEditor().getDocument();
    int lineStartOffset = document.getLineStartOffset(line);
    int lineEndOffset = document.getLineEndOffset(line);
    if (lineEndOffset <= lineStartOffset) return Collections.emptyList();
    if (skipBidiLayout) return Collections.singletonList(new BidiRun(lineEndOffset - lineStartOffset));
    CharSequence text = document.getImmutableCharSequence().subSequence(lineStartOffset, lineEndOffset);
    char[] chars = CharArrayUtil.fromSequence(text);
    return createRuns(view, chars, lineStartOffset);
  }

  private static List<BidiRun> createFragments(@NotNull EditorView view, @NotNull CharSequence text, 
                                                @JdkConstants.FontStyle int fontStyle) {
    if (text.length() == 0) return Collections.emptyList();

    FontFallbackIterator ffi = new FontFallbackIterator()
      .setPreferredFonts(view.getEditor().getColorsScheme().getFontPreferences())
      .setFontStyle(fontStyle)
      .setFontRenderContext(view.getFontRenderContext());
    
    char[] chars = CharArrayUtil.fromSequence(text);
    List<BidiRun> runs = createRuns(view, chars, -1);
    for (BidiRun run : runs) {
      for (Chunk chunk : run.getChunks(text, 0)) {
        chunk.fragments = new ArrayList<>();
        addFragments(run, chunk, chars, chunk.startOffset, chunk.endOffset, null, ffi);
      }
    }
    return runs;
  }

  private static List<BidiRun> createRuns(EditorView view, char[] text, int startOffsetInEditor) {
    int textLength = text.length;
    if (view.getEditor().myDisableRtl || !Bidi.requiresBidi(text, 0, textLength)) {
      return Collections.singletonList(new BidiRun(textLength));
    }
    List<BidiRun> runs = new ArrayList<>();
    int flags = view.getBidiFlags();
    if (startOffsetInEditor >= 0) {
      // skipping indent
      int relLastOffset = 0;
      while (relLastOffset < text.length && text[relLastOffset] == ' ' || text[relLastOffset] == '\t') relLastOffset++;
      addRuns(runs, text, 0, relLastOffset, flags);
      // running bidi algorithm separately for text fragments corresponding to different lexer tokens
      IElementType lastToken = null;
      HighlighterIterator iterator = view.getEditor().getHighlighter().createIterator(startOffsetInEditor + relLastOffset);
      while (!iterator.atEnd() && iterator.getStart() - startOffsetInEditor < textLength) {
        int iteratorRelStart = alignToCodePointBoundary(text, iterator.getStart() - startOffsetInEditor);
        int iteratorRelEnd = alignToCodePointBoundary(text, iterator.getEnd() - startOffsetInEditor);
        IElementType currentToken = iterator.getTokenType();
        int relStartOffset = Math.max(relLastOffset, iteratorRelStart);
        String lcPrefix = getLineCommentPrefix(currentToken);
        // for line comments we process prefix and following text separately
        if (!StringUtil.isEmpty(lcPrefix) && lcPrefix.length() <= iteratorRelEnd - iteratorRelStart &&
            CharArrayUtil.regionMatches(text, relStartOffset, relStartOffset + lcPrefix.length(), lcPrefix) &&
            !isInsideSurrogatePair(text, relStartOffset + lcPrefix.length())) {
          addRuns(runs, text, relLastOffset, relStartOffset, flags);
          int textStartOffset = Math.min(textLength, Math.min(iteratorRelEnd,
                                                              CharArrayUtil.shiftForward(text, relStartOffset + lcPrefix.length(), " \t")));
          relLastOffset = Math.min(iteratorRelEnd, textLength);
          lastToken = null;
          addRuns(runs, text, relStartOffset, textStartOffset, flags);
          addRuns(runs, text, textStartOffset, relLastOffset, flags);
        }
        else if (distinctTokens(lastToken, currentToken)) {
          addRuns(runs, text, relLastOffset, relStartOffset, flags);
          lastToken = currentToken;
          relLastOffset = relStartOffset;
        }
        iterator.advance();
      }
      addRuns(runs, text, relLastOffset, textLength, flags);
    }
    else {
      addRuns(runs, text, 0, textLength, flags);
    }
    for (BidiRun run : runs) {
      assert !isInsideSurrogatePair(text, run.startOffset);
      assert !isInsideSurrogatePair(text, run.endOffset);
    }
    return runs;
  }

  private static boolean isInsideSurrogatePair(char[] text, int offset) {
    return offset > 0 && offset < text.length && Character.isHighSurrogate(text[offset - 1]) && Character.isLowSurrogate(text[offset]);
  }

  private static int alignToCodePointBoundary(char[] text, int offset) {
    return isInsideSurrogatePair(text, offset) ? offset - 1 : offset;
  }

  private static int alignToCodePointBoundary(CharSequence text, int offset) {
    return offset > 0 && offset < text.length() &&
           Character.isHighSurrogate(text.charAt(offset - 1)) && Character.isLowSurrogate(text.charAt(offset)) ? offset - 1 : offset;
  }

  private static String getLineCommentPrefix(IElementType token) {
    if (token == null) return null;
    Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(token.getLanguage());
    return commenter instanceof CodeDocumentationAwareCommenter &&
           token.equals(((CodeDocumentationAwareCommenter)commenter).getLineCommentTokenType()) ?
           commenter.getLineCommentPrefix() : null;
  }

  private static boolean distinctTokens(@Nullable IElementType token1, @Nullable IElementType token2) {
    if (token1 == token2) return false;
    if (token1 == null || token2 == null) return true;
    if (StringEscapesTokenTypes.STRING_LITERAL_ESCAPES.contains(token1) ||
        StringEscapesTokenTypes.STRING_LITERAL_ESCAPES.contains(token2)) return false;
    if (token1 != TokenType.WHITE_SPACE && token2 != TokenType.WHITE_SPACE && !token1.getLanguage().is(token2.getLanguage())) return true;
    Language language = token1.getLanguage();
    if (language == Language.ANY) language = token2.getLanguage();
    BidiRegionsSeparator separator = LanguageBidiRegionsSeparator.INSTANCE.forLanguage(language);
    return separator.createBorderBetweenTokens(token1, token2);
  }
  
  private static void addRuns(List<BidiRun> runs, char[] text, int start, int end, int flags) {
    if (start < end && !Bidi.requiresBidi(text, start, end)) {
      addOrMergeRun(runs, new BidiRun((byte)0, start, end));
      return;
    }
    int afterLastTabPosition = start;
    for (int i = start; i < end; i++) {
      if (text[i] == '\t') {
        addRunsNoTabs(runs, text, afterLastTabPosition, i, flags);
        afterLastTabPosition = i + 1;
        addOrMergeRun(runs, new BidiRun((byte)0, i, i + 1));
      }
    }
    addRunsNoTabs(runs, text, afterLastTabPosition, end, flags);
  }

  private static void addRunsNoTabs(List<BidiRun> runs, char[] text, int start, int end, int flags) {
    if (start >= end) return;
    Bidi bidi = new Bidi(text, start, null, 0, end - start, flags);
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
  
  @SuppressWarnings("AssignmentToForLoopParameter")
  private static void addFragments(BidiRun run, Chunk chunk, char[] text, int start, int end, @Nullable TabFragment tabFragment, 
                                   FontFallbackIterator it) {
    assert start < end;
    if (tabFragment == null) {
      addFragmentsNoTabs(run, chunk, text, start, end, it);
    }
    else {
      int last = start;
      for (int i = start; i < end; i++) {
        if (text[i] == '\t') {
          assert run.level == 0;
          addFragmentsNoTabs(run, chunk, text, last, i, it);
          chunk.fragments.add(tabFragment);
          last = i + 1;
        }
      }
      addFragmentsNoTabs(run, chunk, text, last, end, it);
    }
    assert !chunk.fragments.isEmpty();
  }
  
  private static void addFragmentsNoTabs(BidiRun run, Chunk chunk, char[] text, int start, int end, FontFallbackIterator it) {
    if (start < end) {
      it.start(text, start, end);
      while (!it.atEnd()) {
        addTextFragmentIfNeeded(chunk, text, it.getStart(), it.getEnd(), it.getFontInfo(), run.isRtl());
        it.advance();
      }
    }
  }

  private static void addTextFragmentIfNeeded(Chunk chunk, char[] chars, int from, int to, FontInfo fontInfo, boolean isRtl) {
    if (to > from) {
      assert fontInfo != null;
      chunk.fragments.add(TextFragmentFactory.createTextFragment(chars, from, to, isRtl, fontInfo));
    }
  }
  
  Iterable<VisualFragment> getFragmentsInVisualOrder(final float startX) {
    return () -> new VisualOrderIterator(null, 0, startX, 0, 0, getRunsInVisualOrder());
  }

  /**
   * If {@code quickEvaluationListener} is provided, quick approximate iteration becomes enabled, listener will be invoked
   * if approximation will in fact be used during width calculation.
   */
  Iterator<VisualFragment> getFragmentsInVisualOrder(@NotNull final EditorView view,
                                                     final int line,
                                                     final float startX,
                                                     final int startVisualColumn,
                                                     final int startOffset,
                                                     int endOffset,
                                                     @Nullable Runnable quickEvaluationListener) {
    assert startOffset <= endOffset;
    Document document = view.getEditor().getDocument();
    int lineStartOffset = document.getLineStartOffset(line);
    assert !DocumentUtil.isInsideSurrogatePair(document, lineStartOffset + startOffset);
    assert !DocumentUtil.isInsideSurrogatePair(document, lineStartOffset + endOffset);

    final BidiRun[] runs;
    if (startOffset == endOffset) {
      runs = BidiRun.EMPTY_ARRAY;
    }
    else {
      List<BidiRun> runList = new ArrayList<>();
      for (BidiRun run : getRunsInLogicalOrder()) {
        if (run.endOffset <= startOffset) continue;
        if (run.startOffset >= endOffset) break;
        runList.add(run.subRun(view, line, startOffset, endOffset, quickEvaluationListener));
      }
      runs = runList.toArray(new BidiRun[runList.size()]);
      if (runs.length > 1) {
        reorderRunsVisually(runs);
      }
    }
    return new VisualOrderIterator(view, line, startX, startVisualColumn, startOffset, runs);
  }

  abstract Stream<Chunk> getChunksInLogicalOrder();

  float getWidth() {
    throw new RuntimeException("This LineLayout instance doesn't have precalculated width");
  }

  abstract boolean isLtr();
  
  abstract boolean isRtlLocation(int offset, boolean leanForward);

  abstract int findNearestDirectionBoundary(int offset, boolean lookForward);
  
  abstract BidiRun[] getRunsInLogicalOrder();

  abstract BidiRun[] getRunsInVisualOrder();
  
  private static class SingleChunk extends LineLayout {
    private final Chunk myChunk;

    private SingleChunk(Chunk chunk) {
      myChunk = chunk;
    }

    @Override
    Stream<Chunk> getChunksInLogicalOrder() {
      return myChunk == null ? Stream.empty() : Stream.of(myChunk);
    }

    @Override
    boolean isLtr() {
      return true;
    }
    
    @Override
    boolean isRtlLocation(int offset, boolean leanForward) {
      return false;
    }

    @Override
    int findNearestDirectionBoundary(int offset, boolean lookForward) {
      return -1;
    }

    @Override
    BidiRun[] getRunsInLogicalOrder() {
      return createRuns();
    }

    @Override
    BidiRun[] getRunsInVisualOrder() {
      return createRuns();
    }
    
    private BidiRun[] createRuns() {
      if (myChunk == null) return BidiRun.EMPTY_ARRAY;
      BidiRun run = new BidiRun(myChunk.endOffset);
      run.chunks = new Chunk[] {myChunk};
      return new BidiRun[] {run};
    }
  }
  
  private static class MultiChunk extends LineLayout {
    private final BidiRun[] myBidiRunsInLogicalOrder;
    private final BidiRun[] myBidiRunsInVisualOrder;

    private MultiChunk(BidiRun[] bidiRunsInLogicalOrder) {
      myBidiRunsInLogicalOrder = bidiRunsInLogicalOrder;
      if (bidiRunsInLogicalOrder.length > 1) {
        myBidiRunsInVisualOrder = myBidiRunsInLogicalOrder.clone();
        reorderRunsVisually(myBidiRunsInVisualOrder);
      }
      else {
        myBidiRunsInVisualOrder = bidiRunsInLogicalOrder;
      }
    }

    @Override
    Stream<Chunk> getChunksInLogicalOrder() {
      return Stream.of(myBidiRunsInLogicalOrder).flatMap((BidiRun r) -> r.chunks == null ? Stream.empty() : Stream.of(r.chunks));
    }

    @Override
    boolean isLtr() {
      return myBidiRunsInLogicalOrder.length == 0 || myBidiRunsInLogicalOrder.length == 1 && !myBidiRunsInLogicalOrder[0].isRtl();
    }

    @Override
    boolean isRtlLocation(int offset, boolean leanForward) {
      if (offset == 0 && !leanForward) return false;
      for (BidiRun run : myBidiRunsInLogicalOrder) {
        if (offset < run.endOffset || offset == run.endOffset && !leanForward) return run.isRtl();
      }
      return false;
    }
    
    @Override
    int findNearestDirectionBoundary(int offset, boolean lookForward) {
      if (lookForward) {
        byte originLevel = -1;
        for (BidiRun run : myBidiRunsInLogicalOrder) {
          if (originLevel >= 0) {
            if (run.level != originLevel) return run.startOffset;
          }
          else if (run.endOffset > offset) {
            originLevel = run.level;
          }
        }
        return originLevel > 0 ? myBidiRunsInLogicalOrder[myBidiRunsInLogicalOrder.length - 1].endOffset : -1;
      }
      else {
        byte originLevel = -1;
        for (int i = myBidiRunsInLogicalOrder.length - 1; i >= 0; i--) {
          BidiRun run = myBidiRunsInLogicalOrder[i];
          if (originLevel >= 0) {
            if (run.level != originLevel) return run.endOffset;
          }
          else if (run.startOffset < offset) {
            originLevel = run.level;

          }
        }
        return originLevel > 0 ? 0 : -1;
      }
    }

    @Override
    BidiRun[] getRunsInLogicalOrder() {
      return myBidiRunsInLogicalOrder;
    }

    @Override
    BidiRun[] getRunsInVisualOrder() {
      return myBidiRunsInVisualOrder;
    }
  }
  
  private static class WithSize extends LineLayout {
    private final LineLayout myDelegate;
    private final float myWidth;
    
    private WithSize(@NotNull LineLayout delegate) {
      myDelegate = delegate;
      myWidth = calculateWidth();
    }

    private float calculateWidth() {
      float x = 0;
      for (VisualFragment fragment : getFragmentsInVisualOrder(x)) {
        x = fragment.getEndX();
      }
      return x;
    }

    @Override
    Stream<Chunk> getChunksInLogicalOrder() {
      return myDelegate.getChunksInLogicalOrder();
    }

    @Override
    float getWidth() {
      return myWidth;
    }

    @Override
    boolean isLtr() {
      return myDelegate.isLtr();
    }

    @Override
    boolean isRtlLocation(int offset, boolean leanForward) {
      return myDelegate.isRtlLocation(offset, leanForward);
    }

    @Override
    int findNearestDirectionBoundary(int offset, boolean lookForward) {
      return myDelegate.findNearestDirectionBoundary(offset, lookForward);
    }

    @Override
    BidiRun[] getRunsInLogicalOrder() {
      return myDelegate.getRunsInLogicalOrder();
    }

    @Override
    BidiRun[] getRunsInVisualOrder() {
      return myDelegate.getRunsInVisualOrder();
    }
  }

  private static class BidiRun {
    public static final BidiRun[] EMPTY_ARRAY = new BidiRun[0];
    private static final int CHUNK_CHARACTERS = 1024;
    
    private final byte level;
    private final int startOffset;
    private final int endOffset;
    private int visualStartLogicalColumn;
    private Chunk[] chunks; // in logical order

    private BidiRun(int length) {
      this((byte)0, 0, length);
    }
    
    private BidiRun(byte level, int startOffset, int endOffset) {
      this.level = level;
      this.startOffset = startOffset;
      this.endOffset = endOffset;
    }
    
    private boolean isRtl() {
      return BitUtil.isSet(level, 1);
    }
    
    private Chunk[] getChunks(CharSequence text, int startOffsetInText) {
      if (chunks == null) {
        int chunkCount = getChunkCount();
        chunks = new Chunk[chunkCount];
        for (int i = 0; i < chunkCount; i++) {
          int from = startOffset + i * CHUNK_CHARACTERS;
          int to = i == chunkCount - 1 ? endOffset : from + CHUNK_CHARACTERS;
          Chunk chunk = new Chunk(alignToCodePointBoundary(text, from + startOffsetInText) - startOffsetInText,
                                  alignToCodePointBoundary(text, to + startOffsetInText) - startOffsetInText);
          chunks[i] = chunk;
        }
      }
      return chunks;
    }

    private int getChunkCount() {
      return (endOffset - startOffset + CHUNK_CHARACTERS - 1) / CHUNK_CHARACTERS;
    }

    private BidiRun subRun(@NotNull EditorView view, int line, int targetStartOffset, int targetEndOffset, 
                           @Nullable Runnable quickEvaluationListener) {
      assert targetStartOffset < endOffset;
      assert targetEndOffset > startOffset;
      int start = Math.max(startOffset, targetStartOffset);
      int end = Math.min(endOffset, targetEndOffset);
      BidiRun subRun = new BidiRun(level, start, end);
      List<Chunk> subChunks = new ArrayList<>();
      Document document = view.getEditor().getDocument();
      for (Chunk chunk : getChunks(document.getImmutableCharSequence(), document.getLineStartOffset(line))) {
        if (chunk.endOffset <= start) continue;
        if (chunk.startOffset >= end) break;
        subChunks.add(chunk.subChunk(view, this, line, start, end, quickEvaluationListener));
      }
      subRun.chunks = subChunks.toArray(new Chunk[subChunks.size()]);
      subRun.visualStartLogicalColumn = (subRun.isRtl() ? end == endOffset : start == startOffset) ? visualStartLogicalColumn :
                                        view.getLogicalPositionCache().offsetToLogicalColumn(line, subRun.isRtl() ? end : start);
      return subRun;
    }
  }
  
  static class Chunk {
    List<LineFragment> fragments; // in logical order
    private final int startOffset;
    private final int endOffset;

    private Chunk(int startOffset, int endOffset) {
      this.startOffset = startOffset;
      this.endOffset = endOffset;
    }

    private void ensureLayout(@NotNull EditorView view, BidiRun run, int line) {
      if (isReal()) {
        view.getTextLayoutCache().onChunkAccess(this);
      }
      if (fragments != null) return;
      assert isReal();
      fragments = new ArrayList<>();
      int lineStartOffset = view.getEditor().getDocument().getLineStartOffset(line);
      int start = lineStartOffset + startOffset;
      int end = lineStartOffset + endOffset;
      if (LOG.isDebugEnabled()) LOG.debug("Text layout for " + view.getEditor().getVirtualFile() + " (" + start + "-" + end + ")");
      IterationState it = new IterationState(view.getEditor(), start, end, null, false, true, false, false);
      
      FontFallbackIterator ffi = new FontFallbackIterator()
        .setPreferredFonts(view.getEditor().getColorsScheme().getFontPreferences())
        .setFontRenderContext(view.getFontRenderContext());
      
      char[] chars = CharArrayUtil.fromSequence(view.getEditor().getDocument().getImmutableCharSequence(), start, end);
      int currentFontType = 0;
      Color currentColor = null;
      int currentStart = start;
      while (!it.atEnd()) {
        int fontType = it.getMergedAttributes().getFontType();
        Color color = it.getMergedAttributes().getForegroundColor();
        if (fontType != currentFontType || !color.equals(currentColor)) {
          int tokenStart = it.getStartOffset();
          if (tokenStart > currentStart) {
            addFragments(run, this, chars, currentStart - start, tokenStart - start, view.getTabFragment(), ffi);
          }
          currentStart = tokenStart;
          currentColor = color;
          ffi.setFontStyle(currentFontType = fontType);
        }
        it.advance();
      }
      if (end > currentStart) {
        addFragments(run, this, chars, currentStart - start, end - start, view.getTabFragment(), ffi);
      }
      view.getSizeManager().textLayoutPerformed(start, end);
      assert !fragments.isEmpty();
    }
    
    private Chunk subChunk(EditorView view, BidiRun run, int line, int targetStartOffset, int targetEndOffset,
                           @Nullable Runnable quickEvaluationListener) {
      assert isReal();
      assert targetStartOffset < endOffset;
      assert targetEndOffset > startOffset;
      int start = Math.max(startOffset, targetStartOffset);
      int end = Math.min(endOffset, targetEndOffset);
      if (quickEvaluationListener != null && fragments == null) {
        quickEvaluationListener.run();
        Chunk chunk = new SyntheticChunk(start, end);
        int startColumn = view.getLogicalPositionCache().offsetToLogicalColumn(line, start);
        int endColumn = view.getLogicalPositionCache().offsetToLogicalColumn(line, end);
        chunk.fragments = Collections.singletonList(new ApproximationFragment(end - start, endColumn - startColumn,
                                                                              view.getMaxCharWidth()));
        return chunk;
      }
      if (start == startOffset && end == endOffset) {
        return this;
      }
      ensureLayout(view, run, line);
      Chunk chunk = new SyntheticChunk(start, end);
      chunk.fragments = new ArrayList<>();
      int offset = startOffset;
      for (LineFragment fragment : fragments) {
        if (end <= offset) break;
        int endOffset = offset + fragment.getLength();
        if (start < endOffset) {
          chunk.fragments.add(fragment.subFragment(Math.max(start, offset) - offset, Math.min(end, endOffset) - offset));
        }
        offset = endOffset;
      }
      return chunk;
    }
    
    boolean isReal() {
      return true;
    }

    void clearCache() {
      fragments = null;
    }
  }

  private static class SyntheticChunk extends Chunk {
    private SyntheticChunk(int startOffset, int endOffset) {
      super(startOffset, endOffset);
    }

    @Override
    boolean isReal() {
      return false;
    }
  }
  
  private static class VisualOrderIterator implements Iterator<VisualFragment> {
    private final EditorView myView;
    private final CharSequence myText;
    private final int myLine;
    private final int myLineStartOffset;
    private final BidiRun[] myRuns;
    private int myRunIndex;
    private int myChunkIndex;
    private int myFragmentIndex;
    private int myOffsetInsideRun;
    private final VisualFragment myFragment = new VisualFragment();

    private VisualOrderIterator(EditorView view, int line, 
                                float startX, int startVisualColumn, int startOffset, BidiRun[] runsInVisualOrder) {
      myView = view;
      myText = view == null ? null : view.getEditor().getDocument().getImmutableCharSequence();
      myLine = line;
      myLineStartOffset = view == null ? 0 : view.getEditor().getDocument().getLineStartOffset(line);
      myRuns = runsInVisualOrder;
      myFragment.startX = startX;
      myFragment.startVisualColumn = startVisualColumn;
      myFragment.startOffset = startOffset;
    }

    @Override
    public boolean hasNext() {
      if (myRunIndex >= myRuns.length) return false;
      BidiRun run = myRuns[myRunIndex];
      Chunk[] chunks = run.getChunks(myText, myLineStartOffset);
      if (myChunkIndex >= chunks.length) return false;
      Chunk chunk = chunks[run.isRtl() ? chunks.length - 1 - myChunkIndex : myChunkIndex];
      if (myView != null) {
        chunk.ensureLayout(myView, run, myLine);
      }
      return myFragmentIndex < chunk.fragments.size();
    }

    @Override
    public VisualFragment next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      BidiRun run = myRuns[myRunIndex];

      if (myRunIndex == 0 && myChunkIndex == 0 && myFragmentIndex == 0) {
        myFragment.startLogicalColumn = run.visualStartLogicalColumn;
      }
      else {
        myFragment.startLogicalColumn = myChunkIndex == 0 && myFragmentIndex == 0 ?
                                        run.visualStartLogicalColumn :
                                        myFragment.getEndLogicalColumn();
        myFragment.startVisualColumn = myFragment.getEndVisualColumn();
        myFragment.startX = myFragment.getEndX();
      }
      
      myFragment.isRtl = run.isRtl();
      Chunk[] chunks = run.getChunks(myText, myLineStartOffset);
      Chunk chunk = chunks[run.isRtl() ? chunks.length - 1 - myChunkIndex : myChunkIndex];
      myFragment.delegate = chunk.fragments.get(run.isRtl() ? chunk.fragments.size() - 1 - myFragmentIndex : myFragmentIndex);
      myFragment.startOffset = run.isRtl() ? run.endOffset - myOffsetInsideRun : run.startOffset + myOffsetInsideRun;
      
      myOffsetInsideRun += myFragment.getLength();
      myFragmentIndex++;
      if (myFragmentIndex >= chunk.fragments.size()) {
        myFragmentIndex = 0;
        myChunkIndex++;
          if (myChunkIndex >= chunks.length) {
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
      // there's no need to calculate start column for RTL case - it makes sense only for TabFragment, which cannot be part of RTL  run
      return delegate.getLogicalColumnCount(isRtl ? 0 : getMinLogicalColumn());
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
