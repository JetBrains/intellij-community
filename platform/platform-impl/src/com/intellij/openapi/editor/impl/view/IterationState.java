// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.view;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.SoftWrapModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.DocumentText;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.FontFamilies;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.markup.TextAttributesEffectsBuilder;
import com.intellij.util.CommonProcessors;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.JdkConstants.FontStyle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Iterator over editor's text contents. Each iteration step corresponds to a text fragment having common graphical attributes
 * (font style, foreground and background color, effect type and color).
 */
//@ApiStatus.Internal
public final class IterationState {

  private static final Logger LOG = Logger.getInstance(IterationState.class);
  private static final Comparator<RangeHighlighterEx> BY_AFFECTED_END_OFFSET_REVERSED = (r1, r2) -> r2.getAffectedAreaEndOffset() - r1.getAffectedAreaEndOffset();

  @Contract(pure = true)
  public static @NotNull Comparator<RangeHighlighterEx> createByLayerThenByAttributesComparator(@NotNull EditorColorsScheme scheme) {
    return (o1, o2) -> {
      int result = LayerComparator.HIGHER_FIRST.compare(o1, o2);
      if (result != 0) {
        return result;
      }

      // There is a possible case when more than one highlighter target the same region (e.g. 'identifier under caret' and 'identifier').
      // We want to prefer the one that defines foreground color to the one that doesn't define (has either fore- or background colors
      // while the other one has only foreground color). See IDEA-85697 for concrete example.
      TextAttributes a1 = o1.getTextAttributes(scheme);
      TextAttributes a2 = o2.getTextAttributes(scheme);
      if (a1 == null ^ a2 == null) {
        return a1 == null ? 1 : -1;
      }

      if (a1 != null) {
        Color fore1 = a1.getForegroundColor();
        Color fore2 = a2.getForegroundColor();
        if (fore1 == null ^ fore2 == null) {
          return fore1 == null ? 1 : -1;
        }

        Color back1 = a1.getBackgroundColor();
        Color back2 = a2.getBackgroundColor();
        if (back1 == null ^ back2 == null) {
          return back1 == null ? 1 : -1;
        }
      }
      return compareByHighlightInfoSeverity(o1, o2);
    };
  }

  private final int myInitialStartOffset;
  private final int myEnd;
  private final EditorColorsScheme myColorsScheme;
  private final @FontStyle int myDefaultFontType;
  private final @Nullable HighlighterIterator myHighlighterIterator;
  private final HighlighterSweep myEditorHighlighters;
  private final HighlighterSweep myDocumentHighlighters;
  private final FoldingModelEx myFoldingModel;
  private final SoftWrapModel mySoftWrapModel;
  private final TextAttributes myFoldTextAttributes;
  private final TextAttributes mySelectionAttributes;
  private final TextAttributes myCaretRowAttributes;
  private final TextAttributes myMergedAttributes = new TextAttributes();
  private final Color myDefaultBackground;
  private final Color myDefaultForeground;
  private final Color myReadOnlyColor;
  private final DocumentText myDocument;
  private final CharSequence myChars;
  private final CaretData myCaretData;
  private final boolean myUseOnlyFullLineHighlighters;
  private final boolean myUseOnlyFontOrForegroundAffectingHighlighters;
  private final boolean myStickyLinesPainting;
  private final boolean myEditorRightAligned;
  private final boolean myRoundedHighlighting;
  private final boolean myHuggingHighlighting;
  private final boolean myReverseIteration;
  private final List<RangeHighlighterEx> myCurrentHighlighters = new ArrayList<>();
  private final List<TextAttributes> myCachedAttributesList = new ArrayList<>(5);
  private final List<BackgroundLayer> myStepLayers = new ArrayList<>(5);
  private final List<BackgroundLayer> myStepLayersView = Collections.unmodifiableList(myStepLayers);
  private final List<BackgroundLayer> myBreakLayers = new ArrayList<>(5);
  private final List<BackgroundLayer> myBreakLayersView = Collections.unmodifiableList(myBreakLayers);
  private final TextAttributes myBreakAttributes = new TextAttributes();
  private final GuardedBlocksIndex myGuardedBlocks;

  private int myStartOffset;
  private int myEndOffset;
  private int myCurrentSelectionIndex;
  private List<BackgroundLayer> myBackgroundLayers;
  private List<BackgroundLayer> myLastBackgroundLayers;
  private BackgroundLayer mySelectionLayer;
  private BackgroundLayer myFoldLayer;
  private BackgroundLayer myGuardLayer;
  private BackgroundLayer myCaretRowLayer;
  private BackgroundLayer mySyntaxLayer;
  private TextAttributes myGuardAttributes;
  private TextAttributes mySyntaxAttributes;
  private FoldRegion myCurrentFold;
  private boolean myNextIsFoldRegion;
  /**
   * Says that {@link #myBreakAttributes} and {@link #myBreakLayers} hold the values at a line break of the current step,
   * for the argument {@link #myBreakComputedBeforeBreak}. {@link #advance()} clears it.
   */
  private boolean myBreakComputed;
  private boolean myBreakComputedBeforeBreak;
  /**
   * The start of the syntax run that holds the current step, see {@link #getHighlighterEnd(int)}.
   */
  private int mySyntaxRunStart;
  /**
   * The end of the syntax run that holds the current step.
   */
  private int mySyntaxRunEnd;
  /**
   * The lexer attributes of the syntax run that holds the current step, or null when the step has no lexer attributes.
   */
  private @Nullable TextAttributes mySyntaxRunAttributes;

  @ApiStatus.Internal
  public IterationState(
    @NotNull EditorEx editor,
    int start,
    int end,
    @Nullable CaretData caretData,
    boolean useOnlyFullLineHighlighters,
    boolean useOnlyFontOrForegroundAffectingHighlighters,
    boolean useFoldRegions,
    boolean iterateBackwards
  ) {
    if (!EDT.isCurrentThreadEdt()) {
      ThreadingAssertions.assertReadAccess();
    }
    LOG.assertTrue(iterateBackwards ? start >= end : start <= end);

    DocumentEx document = getDocument(editor);
    myDocument = document.getDocumentText();
    myChars = myDocument.luckyChars();
    assert !isInsideSurrogatePair(start);
    assert !isInsideSurrogatePair(end);

    myColorsScheme = editor.getColorsScheme();
    myInitialStartOffset = start;
    myStartOffset = start;
    myEnd = end;
    myUseOnlyFullLineHighlighters = useOnlyFullLineHighlighters;
    myUseOnlyFontOrForegroundAffectingHighlighters = useOnlyFontOrForegroundAffectingHighlighters;
    myStickyLinesPainting = editor instanceof EditorImpl impl && impl.isStickyLinePainting();
    myEditorRightAligned = editor instanceof EditorImpl impl && impl.isRightAligned();
    myRoundedHighlighting = editor instanceof EditorImpl impl && impl.isRoundedHighlighting();
    myHuggingHighlighting = editor instanceof EditorImpl impl && impl.isHuggingHighlighting();
    myReverseIteration = iterateBackwards;
    myHighlighterIterator = useOnlyFullLineHighlighters ? null : getHighlighter(editor).createIterator(start);
    myCaretData = ObjectUtils.notNull(caretData, CaretData.getNullCaret());
    myFoldingModel = !useFoldRegions ? null : getFoldingModel(editor);
    mySoftWrapModel = getSoftWrapModel(editor);
    myFoldTextAttributes = useFoldRegions ? myFoldingModel.getPlaceholderAttributes() : null;
    mySelectionAttributes = getSelectionModel(editor).getTextAttributes();
    myReadOnlyColor = myColorsScheme.getColor(EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR);
    myCaretRowAttributes = editor.isRendererMode() ? null : getCaretModel(editor).getTextAttributes();
    myDefaultBackground = myColorsScheme.getDefaultBackground();
    myDefaultForeground = myColorsScheme.getDefaultForeground();
    TextAttributes defaultAttributes = myColorsScheme.getAttributes(HighlighterColors.TEXT);
    myDefaultFontType = defaultAttributes == null ? Font.PLAIN : defaultAttributes.getFontType();
    myEditorHighlighters = createSweep(getEditorMarkupModel(editor));
    myDocumentHighlighters = createSweep(getDocumentMarkupModel(editor));
    myGuardedBlocks = useOnlyFullLineHighlighters ? null : buildGuardedBlocks(document.getGuardedBlocks(), start, end);
    myEndOffset = myStartOffset;
    advance();
  }

  @ApiStatus.Internal
  public void retreat(int offset) {
    assert !myReverseIteration && // we need only this case at the moment, this can be relaxed if needed
           myCaretData == CaretData.getNullCaret() &&
           offset >= myInitialStartOffset &&
           offset <= myStartOffset &&
           !isInsideSurrogatePair(offset);
    if (offset == myStartOffset) return;
    if (myHighlighterIterator != null) {
      while (myHighlighterIterator.getStart() > offset) {
        myHighlighterIterator.retreat();
      }
      mySyntaxRunAttributes = null;
    }
    myCurrentHighlighters.clear();
    myDocumentHighlighters.retreat(offset);
    myEditorHighlighters.retreat(offset);
    myEndOffset = offset;
    advance();
  }

  /**
   * Moves the iteration forward to the offset. The offset must not be inside a collapsed fold region.
   * The highlighters collected at the start stay in use, so a jump costs no new markup search.
   */
  @ApiStatus.Internal
  public void advanceTo(int offset) {
    assert !myReverseIteration &&
           myCaretData == CaretData.getNullCaret() &&
           offset >= myStartOffset &&
           offset <= myEnd &&
           !isInsideSurrogatePair(offset);
    if (offset == myStartOffset) return;
    myEndOffset = offset;
    advance();
  }

  @ApiStatus.Internal
  public void advance() {
    myNextIsFoldRegion = false;
    myBreakComputed = false;
    myStartOffset = myEndOffset;
    advanceSegmentHighlighters();
    advanceCurrentSelectionIndex();

    if (!myUseOnlyFullLineHighlighters) {
      myCurrentFold = myFoldingModel == null
                      ? null
                      : myFoldingModel.getCollapsedRegionAtOffset(myReverseIteration ? myStartOffset - 1 : myStartOffset);
    }
    if (myCurrentFold != null) {
      myEndOffset = myReverseIteration ? myCurrentFold.getStartOffset() : myCurrentFold.getEndOffset();
      assert !isInsideSurrogatePair(myEndOffset);
    }
    else {
      int highlighterEnd = getHighlighterEnd(myStartOffset);
      int selectionEnd = getSelectionEnd();
      int minSegmentHighlightersEnd = getMinSegmentHighlightersEnd();
      int foldRangesEnd = getFoldRangesEnd(myStartOffset);
      int caretEnd = getCaretEnd(myStartOffset);
      int guardedBlockEnd = getGuardedBlockEnd(myStartOffset);

      myEndOffset = highlighterEnd;
      setEndOffsetIfCloser(selectionEnd);
      setEndOffsetIfCloser(minSegmentHighlightersEnd);
      setEndOffsetIfCloser(foldRangesEnd);
      setEndOffsetIfCloser(caretEnd);
      setEndOffsetIfCloser(guardedBlockEnd);

      myNextIsFoldRegion = myEndOffset == foldRangesEnd && myEndOffset < myEnd;

      assert !isInsideSurrogatePair(myEndOffset) :
        "caret: " + isInsideSurrogatePair(caretEnd) +
        ", selection: " + isInsideSurrogatePair(selectionEnd) +
        ", guarded block: " + isInsideSurrogatePair(guardedBlockEnd) +
        ", folding: " + isInsideSurrogatePair(foldRangesEnd) +
        ", lexer: " + isInsideSurrogatePair(highlighterEnd) +
        ", highlighters: " + isInsideSurrogatePair(minSegmentHighlightersEnd);
    }

    reinit();
  }

  @ApiStatus.Internal
  public @NotNull TextAttributes getBreakAttributes() {
    return getBreakAttributes(false);
  }

  /**
   * Gives the attributes at a line break.
   * The attributes are read-only, and they are valid as long as the list of {@link #getBreakBackgroundLayers(boolean)}.
   */
  @ApiStatus.Internal
  public @NotNull TextAttributes getBreakAttributes(boolean beforeBreak) {
    computeAtBreak(beforeBreak);
    return myBreakAttributes;
  }

  @ApiStatus.Internal
  public @NotNull TextAttributes getMergedAttributes() {
    return myMergedAttributes;
  }

  /**
   * Gives the background layers of the current step, from the top layer down.
   * The list is read-only, and it is valid until the next {@link #advance()}. A caller that keeps it longer must copy it.
   */
  @ApiStatus.Internal
  public @NotNull List<BackgroundLayer> getBackgroundLayers() {
    return myBackgroundLayers;
  }

  /**
   * Gives the background layers at a line break, from the top layer down.
   * The list is read-only. It is valid until the next {@link #advance()}, or until a call of this method or of
   * {@link #getBreakAttributes(boolean)} with the other argument.
   */
  @ApiStatus.Internal
  public @NotNull List<BackgroundLayer> getBreakBackgroundLayers(boolean beforeBreak) {
    computeAtBreak(beforeBreak);
    return myBreakLayers.isEmpty() ? Collections.emptyList() : myBreakLayersView;
  }

  /**
   * Computes the attributes and the layers at a line break, once for each step and argument.
   * Only the result of the last argument stays.
   */
  private void computeAtBreak(boolean beforeBreak) {
    if (myBreakComputed && myBreakComputedBeforeBreak == beforeBreak) {
      return;
    }
    setAttributes(myBreakAttributes, true, beforeBreak);
    myBreakComputed = true;
    myBreakComputedBeforeBreak = beforeBreak;
  }

  @ApiStatus.Internal
  public boolean atEnd() {
    return myReverseIteration ? myStartOffset <= myEnd : myStartOffset >= myEnd;
  }

  @ApiStatus.Internal
  public int getStartOffset() {
    return myStartOffset;
  }

  @ApiStatus.Internal
  public int getEndOffset() {
    return myEndOffset;
  }

  @ApiStatus.Internal
  public FoldRegion getCurrentFold() {
    return myCurrentFold;
  }

  @ApiStatus.Internal
  public boolean nextIsFoldRegion() {
    return myNextIsFoldRegion;
  }

  @ApiStatus.Internal
  public @NotNull TextAttributes getPastLineEndBackgroundAttributes() {
    List<BackgroundLayer> layers = getPastLineEndBackgroundLayers();
    BackgroundLayer layer = ContainerUtil.find(layers, BackgroundLayer::getForcesBackground);
    if (layer == null) {
      layer = ContainerUtil.getFirstItem(layers);
    }
    myMergedAttributes.setBackgroundColor(layer == null ? myDefaultBackground : layer.getColor());
    return myMergedAttributes;
  }

  /**
   * Gives the layers that fill the editor after the line end. A hugging layer is absent, because it stops at the text.
   */
  @ApiStatus.Internal
  public @NotNull List<BackgroundLayer> getPastLineEndBackgroundLayers() {
    List<BackgroundLayer> layers;
    if (hasSoftWrap()) {
      layers = getBreakBackgroundLayers(true);
    }
    else if (myEditorRightAligned && myLastBackgroundLayers != null) {
      layers = myLastBackgroundLayers;
    }
    else {
      layers = myBackgroundLayers;
    }
    return getFillingLayers(layers);
  }

  @ApiStatus.Internal
  public boolean hasPastLineEndExtension() {
    return isInSelection(hasSoftWrap());
  }

  /**
   * Gives the layers that fill the editor before the line start.
   * In a right-aligned editor, this area is the empty left side of the line, also at a soft wrap. A hugging layer is
   * absent there. In a left-aligned editor, this area is the indent after a soft wrap. A hugging layer stays there,
   * because the hugging selection covers that indent.
   */
  @ApiStatus.Internal
  public @NotNull List<BackgroundLayer> getBeforeLineStartBackgroundLayers() {
    List<BackgroundLayer> layers = getBreakBackgroundLayers(false);
    return myEditorRightAligned ? getFillingLayers(layers) : layers;
  }

  /**
   * Gives the layers without the hugging ones, in the same order.
   * A hugging layer stops at the text, so it never fills the editor.
   * When no layer hugs, the result is the given list, and it is valid as long as the given list.
   */
  static @NotNull List<BackgroundLayer> getFillingLayers(@NotNull List<BackgroundLayer> layers) {
    for (int i = 0; i < layers.size(); i++) {
      if (layers.get(i).getHugs()) {
        return ContainerUtil.filter(layers, layer -> !layer.getHugs());
      }
    }
    return layers;
  }

  private @NotNull HighlighterSweep createSweep(MarkupModelEx markupModel) {
    return new HighlighterSweep(
      myColorsScheme,
      markupModel,
      myStartOffset,
      myEnd,
      myUseOnlyFullLineHighlighters,
      myUseOnlyFontOrForegroundAffectingHighlighters
    );
  }

  private void setEndOffsetIfCloser(int offset) {
    if (myReverseIteration ? offset > myEndOffset : offset < myEndOffset) {
      myEndOffset = offset;
    }
  }

  /**
   * Gives the offset after the start where the lexer attributes can change, and keeps the attributes of the step.
   * A syntax run is one lexer token, or adjacent lexer tokens whose attributes add nothing to a step.
   * Only the ends of the syntax runs limit the steps.
   */
  private int getHighlighterEnd(int start) {
    HighlighterIterator iterator = myHighlighterIterator;
    if (iterator == null) {
      return myEnd;
    }
    if (mySyntaxRunAttributes != null && isAhead(mySyntaxRunEnd, start)) {
      return mySyntaxRunEnd;
    }
    mySyntaxRunAttributes = null;
    while (!iterator.atEnd()) {
      int end = alignOffset(tokenEnd(iterator));
      if (isAhead(end, start)) {
        mySyntaxRunStart = tokenStart(iterator);
        mySyntaxRunAttributes = iterator.getTextAttributes();
        mySyntaxRunEnd = addsNothing(mySyntaxRunAttributes) ? extendSyntaxRun(iterator, end) : end;
        return mySyntaxRunEnd;
      }
      step(iterator);
    }
    return myEnd;
  }

  /**
   * Moves the iterator over the next lexer tokens whose attributes add nothing to a step, and gives the end of the last of them.
   * The given end is the end of the current token. The result does not pass the first token end that reaches the end of the iteration.
   */
  private int extendSyntaxRun(@NotNull HighlighterIterator iterator, int end) {
    int runEnd = end;
    while (isAhead(myEnd, runEnd)) {
      step(iterator);
      if (iterator.atEnd() || !addsNothing(iterator.getTextAttributes())) {
        break;
      }
      runEnd = alignOffset(tokenEnd(iterator));
    }
    return runEnd;
  }

  /**
   * Says whether the attributes add nothing to a merge: no color, no effect, the plain font, and no font families.
   */
  private static boolean addsNothing(@Nullable TextAttributes attributes) {
    return attributes != null && attributes.isEmpty() && !attributes.hasEffects();
  }

  /**
   * Gives the offset where the current lexer token starts in the direction of the iteration.
   */
  private int tokenStart(@NotNull HighlighterIterator iterator) {
    return myReverseIteration ? iterator.getEnd() : iterator.getStart();
  }

  /**
   * Gives the offset where the current lexer token ends in the direction of the iteration.
   */
  private int tokenEnd(@NotNull HighlighterIterator iterator) {
    return myReverseIteration ? iterator.getStart() : iterator.getEnd();
  }

  /**
   * Moves the iterator to the next lexer token in the direction of the iteration.
   */
  private void step(@NotNull HighlighterIterator iterator) {
    if (myReverseIteration) {
      iterator.retreat();
    } else {
      iterator.advance();
    }
  }

  /**
   * Says whether the offset comes after the other offset in the direction of the iteration.
   */
  private boolean isAhead(int offset, int other) {
    return myReverseIteration ? offset < other : offset > other;
  }

  private int getCaretEnd(int start) {
    return getNearestValueAhead(start, myCaretData.caretRowStart(), myCaretData.caretRowEnd());
  }

  private int getNearestValueAhead(int offset, int rangeStart, int rangeEnd) {
    if (myReverseIteration) {
      if (rangeEnd < offset) {
        return rangeEnd;
      }
      if (rangeStart < offset) {
        return rangeStart;
      }
    }
    else {
      if (rangeStart > offset) {
        return rangeStart;
      }
      if (rangeEnd > offset) {
        return rangeEnd;
      }
    }

    return myEnd;
  }

  private boolean isInsideSurrogatePair(int offset) {
    CharSequence chars = myChars;
    return 0 < offset &&
           offset < chars.length() &&
           Character.isHighSurrogate(chars.charAt(offset - 1)) &&
           Character.isLowSurrogate(chars.charAt(offset));
  }

  private int getGuardedBlockEnd(int start) {
    int end = myEnd;
    assert myReverseIteration && start >= end || !myReverseIteration && start <= end;

    if (myGuardedBlocks == null) {
      return end;
    }

    if (myReverseIteration) {
      int nearest = myGuardedBlocks.nearestLeft(start - 1);
      return (nearest != -1 && nearest > end) ? nearest : end;
    }

    int nearest = myGuardedBlocks.nearestRight(start + 1);
    return (nearest != -1 && nearest < end) ? nearest : end;
  }

  private void advanceCurrentSelectionIndex() {
    while (myCurrentSelectionIndex < myCaretData.selectionsSize() &&
           (myReverseIteration
            ? myStartOffset <= myCaretData.selectionStart(myCurrentSelectionIndex, true)
            : myStartOffset >= myCaretData.selectionEnd(myCurrentSelectionIndex, false))) {
      myCurrentSelectionIndex++;
    }
  }

  private int getSelectionEnd() {
    if (myCurrentSelectionIndex >= myCaretData.selectionsSize()) {
      return myEnd;
    }
    return getNearestValueAhead(
      myStartOffset,
      myCaretData.selectionStart(myCurrentSelectionIndex, myReverseIteration),
      myCaretData.selectionEnd(myCurrentSelectionIndex, myReverseIteration)
    );
  }

  boolean isInSelection(boolean atBreak) {
    return myCurrentSelectionIndex < myCaretData.selectionsSize() &&
           (myReverseIteration ? lessThan(myStartOffset, myCaretData.selectionEnd(myCurrentSelectionIndex, true), !atBreak)
                               : lessThan(myCaretData.selectionStart(myCurrentSelectionIndex, false), myStartOffset, !atBreak));
  }

  private @Nullable GuardedBlocksIndex buildGuardedBlocks(@NotNull List<RangeMarker> guardedBlocks, int start, int end) {
    if (guardedBlocks.isEmpty()) {
      return null;
    }
    var guardedBlocksIndex = new GuardedBlocksIndex.DocumentBuilder(myDocument, guardedBlocks);
    return myReverseIteration
           ? guardedBlocksIndex.build(end, start)
           : guardedBlocksIndex.build(start, end);
  }

  private boolean isInDocumentGuardedBlock(boolean atBreak, boolean beforeBreak) {
    if (myGuardedBlocks == null || (atBreak && beforeBreak)) {
      return false;
    }
    if (myReverseIteration) {
      return myGuardedBlocks.isGuarded(myStartOffset - 1);
    }
    return myGuardedBlocks.isGuarded(myStartOffset);
  }

  private static boolean lessThan(int x, int y, boolean orEquals) {
    return x < y || orEquals && x == y;
  }

  private void advanceSegmentHighlighters() {
    myDocumentHighlighters.advance();
    myEditorHighlighters.advance();

    boolean fileEnd = myStartOffset == myDocument.length();
    for (int i = myCurrentHighlighters.size() - 1; i >= 0; i--) {
      RangeHighlighterEx highlighter = myCurrentHighlighters.get(i);
      if (myReverseIteration ?
          getAlignedStartOffset(highlighter) >= myStartOffset :
          fileEnd && highlighter.getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE ?
          getAlignedEndOffset(highlighter) < myStartOffset :
          getAlignedEndOffset(highlighter) <= myStartOffset) {
        myCurrentHighlighters.remove(i);
      }
    }
  }

  private int getFoldRangesEnd(int startOffset) {
    if (myUseOnlyFullLineHighlighters || myFoldingModel == null) {
      return myEnd;
    }
    int end = myEnd;
    FoldRegion[] topLevelCollapsed = myFoldingModel.fetchTopLevel();
    if (topLevelCollapsed != null) {
      if (myReverseIteration) {
        for (int i = myFoldingModel.getLastCollapsedRegionBefore(startOffset);
             i >= 0 && i < topLevelCollapsed.length;
             i--) {
          FoldRegion range = topLevelCollapsed[i];
          if (!range.isValid()) continue;

          int rangeEnd = range.getEndOffset();
          if (rangeEnd < startOffset) {
            if (rangeEnd > end) {
              end = rangeEnd;
            }
            else {
              break;
            }
          }
        }
      }
      else {
        for (int i = myFoldingModel.getLastCollapsedRegionBefore(startOffset) + 1;
             i >= 0 && i < topLevelCollapsed.length;
             i++) {
          FoldRegion range = topLevelCollapsed[i];
          if (!range.isValid()) continue;

          int rangeEnd = range.getStartOffset();
          if (rangeEnd > startOffset) {
            if (rangeEnd < end) {
              end = rangeEnd;
            }
            else {
              break;
            }
          }
        }
      }
    }

    return end;
  }

  private int getMinSegmentHighlightersEnd() {
    int end = myEnd;

    for (int i = 0; i < myCurrentHighlighters.size(); i++) {
      RangeHighlighterEx highlighter = myCurrentHighlighters.get(i);
      if (myReverseIteration) {
        end = Math.max(end, getAlignedStartOffset(highlighter));
      }
      else {
        end = Math.min(end, getAlignedEndOffset(highlighter));
      }
    }

    end = myReverseIteration ? Math.max(end, myDocumentHighlighters.getMinSegmentHighlighterEnd()) : Math.min(end, myDocumentHighlighters.getMinSegmentHighlighterEnd());
    end = myReverseIteration ? Math.max(end, myEditorHighlighters.getMinSegmentHighlighterEnd()) : Math.min(end, myEditorHighlighters.getMinSegmentHighlighterEnd());

    return end;
  }

  private void reinit() {
    if (myEditorRightAligned && myBackgroundLayers != null) {
      // a right-aligned editor fills after the line end with the layers of the previous step
      myLastBackgroundLayers = List.copyOf(myBackgroundLayers);
    }
    myBackgroundLayers = setAttributes(myMergedAttributes, false, false);
  }

  private void addAttributes(
    @NotNull List<BackgroundLayer> layers,
    @NotNull TextAttributes attributes,
    @Nullable BackgroundLayer layer
  ) {
    myCachedAttributesList.add(attributes);
    if (layer != null) {
      layers.add(layer);
    }
  }

  /**
   * Gives a background layer of this color, or null when the color is absent or the iteration reads no background.
   */
  private @Nullable BackgroundLayer createLayer(
    @Nullable Color color,
    int layer,
    boolean rounded,
    boolean hugs,
    boolean forcesBackground,
    int span
  ) {
    if (color == null || myUseOnlyFontOrForegroundAffectingHighlighters) {
      return null;
    }
    return new BackgroundLayer(color, layer, rounded, hugs, forcesBackground, span);
  }

  /**
   * Gives the layer of the selection, or null when the selection sets no background.
   */
  private @Nullable BackgroundLayer selectionLayer(@NotNull TextAttributes selection) {
    if (mySelectionLayer == null) {
      mySelectionLayer = createLayer(
        selection.getBackgroundColor(),
        HighlighterLayer.SELECTION,
        myRoundedHighlighting,
        myHuggingHighlighting,
        false,
        Integer.MAX_VALUE
      );
    }
    return mySelectionLayer;
  }

  /**
   * Gives the layer of the fold placeholder, or null when the placeholder sets no background.
   */
  private @Nullable BackgroundLayer foldLayer(@NotNull TextAttributes fold) {
    if (myFoldLayer == null) {
      myFoldLayer = createLayer(
        fold.getBackgroundColor(),
        HighlighterLayer.GUARDED_BLOCKS,
        myRoundedHighlighting,
        false,
        false,
        Integer.MAX_VALUE
      );
    }
    return myFoldLayer;
  }

  /**
   * Gives the layer of a guarded block, or null when the scheme sets no read-only color.
   */
  private @Nullable BackgroundLayer guardLayer() {
    if (myGuardLayer == null) {
      myGuardLayer = createLayer(
        myReadOnlyColor,
        HighlighterLayer.GUARDED_BLOCKS,
        myRoundedHighlighting,
        false,
        true,
        Integer.MAX_VALUE
      );
    }
    return myGuardLayer;
  }

  /**
   * Gives the layer of the caret row, or null when the caret row sets no background.
   */
  private @Nullable BackgroundLayer caretRowLayer(@NotNull TextAttributes caret) {
    if (myCaretRowLayer == null) {
      myCaretRowLayer = createLayer(
        caret.getBackgroundColor(),
        HighlighterLayer.CARET_ROW,
        false,
        false,
        false,
        Integer.MAX_VALUE
      );
    }
    return myCaretRowLayer;
  }

  /**
   * Gives the layer of these syntax attributes, or null when they set no background.
   */
  private @Nullable BackgroundLayer syntaxLayer(@NotNull TextAttributes syntax) {
    if (mySyntaxAttributes != syntax) {
      mySyntaxAttributes = syntax;
      mySyntaxLayer = createLayer(
        syntax.getBackgroundColor(),
        HighlighterLayer.SYNTAX,
        false,
        false,
        false,
        Integer.MAX_VALUE
      );
    }
    return mySyntaxLayer;
  }

  /**
   * Gives the attributes of a guarded block of this iteration.
   */
  private @NotNull TextAttributes guardAttributes() {
    if (myGuardAttributes == null) {
      myGuardAttributes = new TextAttributes(null, myReadOnlyColor, null, EffectType.BOXED, Font.PLAIN);
    }
    return myGuardAttributes;
  }

  private @NotNull List<BackgroundLayer> setAttributes(@NotNull TextAttributes attributes, boolean atBreak, boolean beforeBreak) {
    List<BackgroundLayer> layers;
    List<BackgroundLayer> layersView;
    if (atBreak) {
      layers = myBreakLayers;
      layersView = myBreakLayersView;
    } else {
      layers = myStepLayers;
      layersView = myStepLayersView;
    }
    boolean isInSelection = isInSelection(atBreak);
    boolean isInCaretRow = isInCaretRow(
      !myReverseIteration && (!atBreak || !beforeBreak),
      myReverseIteration || (atBreak && beforeBreak)
    );
    boolean isInGuardedBlock = isInDocumentGuardedBlock(atBreak, beforeBreak);

    TextAttributes syntax = atBreak && myStartOffset == mySyntaxRunStart ? null : mySyntaxRunAttributes;
    TextAttributes selection = getSelectionAttributes(isInSelection);
    TextAttributes caret = getCaretRowAttributes(isInCaretRow);
    TextAttributes fold = myCurrentFold != null ? myFoldTextAttributes : null;
    TextAttributes guard = isInGuardedBlock ? guardAttributes() : null;

    int size = myCurrentHighlighters.size();
    if (size > 1) {
      ContainerUtil.quickSort(myCurrentHighlighters, createByLayerThenByAttributesComparator(myColorsScheme));
    }

    for (int i = 0; i < size; i++) {
      RangeHighlighterEx highlighter = myCurrentHighlighters.get(i);
      if (highlighter.getTextAttributes(myColorsScheme) == TextAttributes.ERASE_MARKER) {
        syntax = null;
      }
    }

    List<TextAttributes> cachedAttributes = myCachedAttributesList;
    if (!cachedAttributes.isEmpty()) cachedAttributes.clear();
    if (!layers.isEmpty()) layers.clear();

    for (int i = 0; i < size; i++) {
      RangeHighlighterEx highlighter = myCurrentHighlighters.get(i);
      if (atBreak &&
          highlighter.getTargetArea() == HighlighterTargetArea.EXACT_RANGE &&
          myStartOffset == (myReverseIteration ? highlighter.getEndOffset() : highlighter.getStartOffset())) {
        continue;
      }
      if (highlighter.getLayer() < HighlighterLayer.SELECTION) {
        if (selection != null) {
          addAttributes(layers, selection, selectionLayer(selection));
          selection = null;
        }
      }

      if (fold != null && highlighter.getLayer() < HighlighterLayer.GUARDED_BLOCKS) {
        addAttributes(layers, fold, foldLayer(fold));
        fold = null;
      }

      if (guard != null && highlighter.getLayer() < HighlighterLayer.GUARDED_BLOCKS) {
        addAttributes(layers, guard, guardLayer());
        guard = null;
      }

      if (caret != null && highlighter.getLayer() < HighlighterLayer.CARET_ROW) {
        addAttributes(layers, caret, caretRowLayer(caret));
        caret = null;
      }

      if (syntax != null && highlighter.getLayer() < HighlighterLayer.SYNTAX) {
        addAttributes(layers, syntax, syntaxLayer(syntax));
        syntax = null;
      }

      TextAttributes textAttributes = highlighter.getTextAttributes(myColorsScheme);
      if (textAttributes != null && textAttributes != TextAttributes.ERASE_MARKER) {
        BackgroundLayer layer = createLayer(
          textAttributes.getBackgroundColor(),
          highlighter.getLayer(),
          BackgroundLayer.isRounded(highlighter.getTargetArea(), myRoundedHighlighting),
          false,
          false,
          highlighter.getAffectedAreaEndOffset() - highlighter.getAffectedAreaStartOffset()
        );
        addAttributes(layers, textAttributes, layer);
      }
    }

    if (selection != null) addAttributes(layers, selection, selectionLayer(selection));
    if (fold != null) addAttributes(layers, fold, foldLayer(fold));
    if (guard != null) addAttributes(layers, guard, guardLayer());
    if (caret != null) addAttributes(layers, caret, caretRowLayer(caret));
    if (syntax != null) addAttributes(layers, syntax, syntaxLayer(syntax));

    Color fore = null;
    Color back = isInGuardedBlock ? myReadOnlyColor : null;
    @FontStyle int fontType = Font.PLAIN;
    FontFamilies fontFamilies = null;

    TextAttributesEffectsBuilder effectsBuilder = null;
    for (int i = 0; i < cachedAttributes.size(); i++) {
      TextAttributes attrs = cachedAttributes.get(i);

      if (fore == null) {
        fore = attrs.getForegroundColor();
      }

      if (back == null) {
        back = attrs.getBackgroundColor();
      }

      if (fontType == Font.PLAIN) {
        fontType = attrs.getFontType();
      }

      if (fontFamilies == null) {
        fontFamilies = attrs.getFontFamilies();
      }

      if (attrs.hasEffects()) {
        if (effectsBuilder == null) {
          effectsBuilder = TextAttributesEffectsBuilder.create();
        }
        effectsBuilder.slipUnder(attrs);
      }
    }

    if (fore == null) fore = myDefaultForeground;
    if (back == null) back = myDefaultBackground;
    if (fontType == Font.PLAIN) fontType = myDefaultFontType;

    attributes.setAttributesNoCache(fore, back, null, null, null, fontType, fontFamilies);
    if (effectsBuilder != null) {
      effectsBuilder.applyTo(attributes);
    }
    return layers.isEmpty() ? Collections.emptyList() : layersView;
  }

  private boolean isInCaretRow(boolean includeLineStart, boolean includeLineEnd) {
    return myCaretData.caretRowStart() < myStartOffset && myStartOffset < myCaretData.caretRowEnd() ||
           includeLineStart && myStartOffset == myCaretData.caretRowStart() ||
           includeLineEnd && myStartOffset == myCaretData.caretRowEnd();
  }

  private @Nullable TextAttributes getSelectionAttributes(boolean isInSelection) {
    return isInSelection ? mySelectionAttributes : null;
  }

  private @Nullable TextAttributes getCaretRowAttributes(boolean isInCaretRow) {
    if (myStickyLinesPainting) {
      // suppress a caret row background on the sticky lines panel
      return null;
    }
    return isInCaretRow ? myCaretRowAttributes : null;
  }

  private boolean hasSoftWrap() {
    return mySoftWrapModel.getSoftWrap(myStartOffset) != null;
  }

  private int alignOffset(int offset) {
    return isInsideSurrogatePair(offset) ? offset - 1 : offset;
  }

  private int getAlignedStartOffset(@NotNull RangeHighlighterEx highlighter) {
    return alignOffset(highlighter.getAffectedAreaStartOffset());
  }

  private int getAlignedEndOffset(@NotNull RangeHighlighterEx highlighter) {
    return alignOffset(highlighter.getAffectedAreaEndOffset());
  }

  private final class HighlighterSweep {
    private final MarkupModelEx myMarkupModel;
    private final EditorColorsScheme myColorsScheme;
    private final boolean myOnlyFullLine;
    private final boolean myOnlyFontOrForegroundAffecting;
    private RangeHighlighterEx myNextHighlighter;
    int i;
    private final RangeHighlighterEx[] highlighters;

    private HighlighterSweep(@NotNull EditorColorsScheme scheme,
                             @NotNull MarkupModelEx markupModel,
                             int start,
                             int end,
                             boolean onlyFullLine,
                             boolean onlyFontOrForegroundAffecting) {
      myColorsScheme = scheme;
      myMarkupModel = markupModel;
      myOnlyFullLine = onlyFullLine;
      myOnlyFontOrForegroundAffecting = onlyFontOrForegroundAffecting;
      highlighters = collectHighlighters(
        myReverseIteration ? end : start,
        myReverseIteration ? start : end,
        myReverseIteration ? BY_AFFECTED_END_OFFSET_REVERSED : RangeHighlighterEx.BY_AFFECTED_START_OFFSET
      );
      myNextHighlighter = firstAdvance();
    }

    private RangeHighlighterEx[] collectHighlighters(int start, int end, Comparator<RangeHighlighterEx> comparator) {
      // we have to get all highlighters in advance and sort them by affected offsets
      // since these can be different from the real offsets the highlighters are sorted by in the tree.
      // (See LINES_IN_RANGE perverts)
      var processor = new CommonProcessors.CollectProcessor<RangeHighlighterEx>() {
        @Override
        public boolean accept(RangeHighlighterEx h) {
          return acceptHighlighter(h); // TODO: refactor to skipHighlighter here
        }
      };
      myMarkupModel.processRangeHighlightersOverlappingWith(start, end, processor);
      RangeHighlighterEx[] highlights = processor.getResults().isEmpty()
                                        ? RangeHighlighterEx.EMPTY_ARRAY
                                        : processor.toArray(RangeHighlighterEx.EMPTY_ARRAY);
      Arrays.sort(highlights, comparator);
      return highlights;
    }

    private @Nullable RangeHighlighterEx firstAdvance() {
      while (i < highlighters.length) {
        RangeHighlighterEx highlighter = highlighters[i++];
        if (!skipHighlighter(highlighter)) {
          return highlighter;
        }
      }
      return null;
    }

    private void advance() {
      if (myNextHighlighter != null) {
        if (myReverseIteration ?
            getAlignedEndOffset(myNextHighlighter) < myStartOffset :
            getAlignedStartOffset(myNextHighlighter) > myStartOffset) {
          return;
        }
        myCurrentHighlighters.add(myNextHighlighter);
        myNextHighlighter = null;
      }

      while (i < highlighters.length) {
        RangeHighlighterEx highlighter = highlighters[i++];
        if (!skipHighlighter(highlighter)) {
          if (myReverseIteration
              ? getAlignedEndOffset(highlighter) < myStartOffset
              : getAlignedStartOffset(highlighter) > myStartOffset) {
            myNextHighlighter = highlighter;
            break;
          }
          else {
            myCurrentHighlighters.add(highlighter);
          }
        }
      }
    }

    private void retreat(int offset) {
      for (int j = i - 2; j >= 0; j--) {
        RangeHighlighterEx highlighter = highlighters[j];
        if (skipHighlighter(highlighter)) continue;
        if (getAlignedStartOffset(highlighter) > offset) {
          myNextHighlighter = highlighter;
          i = j + 1;
        }
        else {
          break;
        }
      }
      myMarkupModel.processRangeHighlightersOverlappingWith(
        offset,
        offset,
        h -> {
          if (acceptHighlighter(h) && !skipHighlighter(h)) {
            myCurrentHighlighters.add(h);
          }
          return true;
        }
      );
    }

    private boolean acceptHighlighter(RangeHighlighterEx highlighter) {
      return (!myOnlyFullLine || highlighter.getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE) &&
             (!myOnlyFontOrForegroundAffecting || EditorUtil.attributesImpactFontStyleOrColor(highlighter.getTextAttributes(myColorsScheme)));
    }

    private boolean skipHighlighter(@NotNull RangeHighlighterEx highlighter) {
      if (!highlighter.isValid() || highlighter.isAfterEndOfLine() || highlighter.getTextAttributes(myColorsScheme) == null) {
        return true;
      }
      FoldRegion region = myFoldingModel == null ? null : myFoldingModel.getCollapsedRegionAtOffset(highlighter.getAffectedAreaStartOffset());
      return region != null && region == myFoldingModel.getCollapsedRegionAtOffset(highlighter.getAffectedAreaEndOffset());
    }

    private int getMinSegmentHighlighterEnd() {
      if (myNextHighlighter != null) {
        return myReverseIteration ? getAlignedEndOffset(myNextHighlighter) : getAlignedStartOffset(myNextHighlighter);
      }
      return myReverseIteration ? Integer.MIN_VALUE : Integer.MAX_VALUE;
    }
  }

  // region boilerplate

  private static DocumentEx getDocument(@NotNull EditorEx editor) {
    if (editor instanceof EditorImpl editorImpl) {
      return editorImpl.getEditorModel().getDocument();
    }
    return editor.getDocument();
  }

  private static FoldingModelEx getFoldingModel(@NotNull EditorEx editor) {
    if (editor instanceof EditorImpl editorImpl) {
      return editorImpl.getEditorModel().getFoldingModel();
    }
    return editor.getFoldingModel();
  }

  private static MarkupModelEx getEditorMarkupModel(@NotNull EditorEx editor) {
    if (editor instanceof EditorImpl editorImpl) {
      return editorImpl.getEditorModel().getEditorMarkupModel();
    }
    return editor.getMarkupModel();
  }

  private static MarkupModelEx getDocumentMarkupModel(@NotNull EditorEx editor) {
    if (editor instanceof EditorImpl editorImpl) {
      return editorImpl.getEditorModel().getDocumentMarkupModel();
    }
    return editor.getFilteredDocumentMarkupModel();
  }

  private static CaretModel getCaretModel(@NotNull EditorEx editor) {
    if (editor instanceof EditorImpl editorImpl) {
      return editorImpl.getEditorModel().getCaretModel();
    }
    return editor.getCaretModel();
  }

  private static EditorHighlighter getHighlighter(@NotNull EditorEx editor) {
    if (editor instanceof EditorImpl editorImpl) {
      return editorImpl.getEditorModel().getHighlighter();
    }
    return editor.getHighlighter();
  }

  private static SoftWrapModel getSoftWrapModel(@NotNull EditorEx editor) {
    if (editor instanceof EditorImpl editorImpl) {
      return editorImpl.getEditorModel().getSoftWrapModel();
    }
    return editor.getSoftWrapModel();
  }

  private static SelectionModel getSelectionModel(@NotNull EditorEx editor) {
    if (editor instanceof EditorImpl editorImpl) {
      return editorImpl.getEditorModel().getSelectionModel();
    }
    return editor.getSelectionModel();
  }

  // endregion

  private static int compareByHighlightInfoSeverity(@NotNull RangeHighlighterEx o1, @NotNull RangeHighlighterEx o2) {
    HighlightInfo info1 = HighlightInfo.fromRangeHighlighter(o1);
    HighlightInfo info2 = HighlightInfo.fromRangeHighlighter(o2);
    HighlightSeverity severity1 = info1 == null ? null : info1.getSeverity();
    HighlightSeverity severity2 = info2 == null ? null : info2.getSeverity();
    if (severity1 != null && severity2 != null) {
      // higher severity should win
      return severity2.compareTo(severity1);
    }
    // having severity has more priority than no severity
    return Boolean.compare(severity1 == null, severity2 == null);
  }

  private static final class LayerComparator implements Comparator<RangeHighlighterEx> {
    private static final LayerComparator HIGHER_FIRST = new LayerComparator();
    @Override
    public int compare(RangeHighlighterEx o1, RangeHighlighterEx o2) {
      int layerDiff = o2.getLayer() - o1.getLayer();
      if (layerDiff != 0) {
        return layerDiff;
      }
      // prefer more specific region
      // It should be synced with c.i.execution.impl.EditorHyperlinkSupport.findLinkRangeAt().
      int o1Length = o1.getAffectedAreaEndOffset() - o1.getAffectedAreaStartOffset();
      int o2Length = o2.getAffectedAreaEndOffset() - o2.getAffectedAreaStartOffset();
      return o1Length - o2Length;
    }
  }
}
