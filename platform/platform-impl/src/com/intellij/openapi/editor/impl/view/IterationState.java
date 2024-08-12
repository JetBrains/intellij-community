// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.view;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.*;
import com.intellij.util.CommonProcessors;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
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
  private final int myDefaultFontType;
  private final @Nullable HighlighterIterator myHighlighterIterator;
  private final HighlighterSweep myView;
  private final HighlighterSweep myDoc;
  private final FoldingModelEx myFoldingModel;
  private final TextAttributes myFoldTextAttributes;
  private final TextAttributes mySelectionAttributes;
  private final TextAttributes myCaretRowAttributes;
  private final TextAttributes myMergedAttributes = new TextAttributes();
  private final Color myDefaultBackground;
  private final Color myDefaultForeground;
  private final Color myReadOnlyColor;
  private final DocumentEx myDocument;
  private final EditorEx myEditor;
  private final CaretData myCaretData;
  private final boolean myUseOnlyFullLineHighlighters;
  private final boolean myUseOnlyFontOrForegroundAffectingHighlighters;
  private final boolean myReverseIteration;
  private final List<RangeHighlighterEx> myCurrentHighlighters = new ArrayList<>();
  private final List<TextAttributes> myCachedAttributesList = new ArrayList<>(5);
  private final GuardedBlocksIndex myGuardedBlocks;

  private int myStartOffset;
  private int myEndOffset;
  private int myCurrentSelectionIndex;
  private Color myCurrentBackgroundColor;
  private Color myLastBackgroundColor;
  private FoldRegion myCurrentFold;
  private boolean myNextIsFoldRegion;

  @ApiStatus.Internal
  @RequiresReadLock
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
    assert !DocumentUtil.isInsideSurrogatePair(editor.getDocument(), start);
    assert !DocumentUtil.isInsideSurrogatePair(editor.getDocument(), end);
    LOG.assertTrue(iterateBackwards ? start >= end : start <= end);

    myDocument = editor.getDocument();
    myInitialStartOffset = start;
    myStartOffset = start;
    myEnd = end;
    myEditor = editor;
    myUseOnlyFullLineHighlighters = useOnlyFullLineHighlighters;
    myUseOnlyFontOrForegroundAffectingHighlighters = useOnlyFontOrForegroundAffectingHighlighters;
    myReverseIteration = iterateBackwards;
    myHighlighterIterator = useOnlyFullLineHighlighters ? null : editor.getHighlighter().createIterator(start);
    myCaretData = ObjectUtils.notNull(caretData, CaretData.getNullCaret());
    myFoldingModel = useFoldRegions ? editor.getFoldingModel() : null;
    myFoldTextAttributes = useFoldRegions ? myFoldingModel.getPlaceholderAttributes() : null;
    mySelectionAttributes = editor.getSelectionModel().getTextAttributes();
    myReadOnlyColor = editor.getColorsScheme().getColor(EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR);
    myCaretRowAttributes = editor.isRendererMode() ? null : editor.getCaretModel().getTextAttributes();
    myDefaultBackground = editor.getColorsScheme().getDefaultBackground();
    myDefaultForeground = editor.getColorsScheme().getDefaultForeground();
    TextAttributes defaultAttributes = editor.getColorsScheme().getAttributes(HighlighterColors.TEXT);
    myDefaultFontType = defaultAttributes == null ? Font.PLAIN : defaultAttributes.getFontType();
    myView = createSweep(editor.getMarkupModel());
    myDoc = createSweep(editor.getFilteredDocumentMarkupModel());
    myGuardedBlocks =  buildGuardedBlocks(start, end);
    myEndOffset = myStartOffset;

    advance();
  }

  @ApiStatus.Internal
  public void retreat(int offset) {
    assert !myReverseIteration && // we need only this case at the moment, this can be relaxed if needed
           myCaretData == CaretData.getNullCaret() &&
           offset >= myInitialStartOffset &&
           offset <= myStartOffset &&
           !DocumentUtil.isInsideSurrogatePair(myDocument, offset);
    if (offset == myStartOffset) return;
    if (myHighlighterIterator != null) {
      while (myHighlighterIterator.getStart() > offset) {
        myHighlighterIterator.retreat();
      }
    }
    myCurrentHighlighters.clear();
    myDoc.retreat(offset);
    myView.retreat(offset);
    myEndOffset = offset;
    advance();
  }

  @ApiStatus.Internal
  public void advance() {
    myNextIsFoldRegion = false;
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
      assert !DocumentUtil.isInsideSurrogatePair(myDocument, myEndOffset);
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

      assert !DocumentUtil.isInsideSurrogatePair(myDocument, myEndOffset) :
        "caret: " + DocumentUtil.isInsideSurrogatePair(myDocument, caretEnd) +
        ", selection: " + DocumentUtil.isInsideSurrogatePair(myDocument, selectionEnd) +
        ", guarded block: " + DocumentUtil.isInsideSurrogatePair(myDocument, guardedBlockEnd) +
        ", folding: " + DocumentUtil.isInsideSurrogatePair(myDocument, foldRangesEnd) +
        ", lexer: " + DocumentUtil.isInsideSurrogatePair(myDocument, highlighterEnd) +
        ", highlighters: " + DocumentUtil.isInsideSurrogatePair(myDocument, minSegmentHighlightersEnd);
    }

    reinit();
  }

  @ApiStatus.Internal
  public @NotNull TextAttributes getBreakAttributes() {
    return getBreakAttributes(false);
  }

  @ApiStatus.Internal
  public @NotNull TextAttributes getBreakAttributes(boolean beforeBreak) {
    TextAttributes attributes = new TextAttributes();
    setAttributes(attributes, true, beforeBreak);
    return attributes;
  }

  @ApiStatus.Internal
  public @NotNull TextAttributes getMergedAttributes() {
    return myMergedAttributes;
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
    myMergedAttributes.setBackgroundColor(
      hasSoftWrap()
      ? getBreakBackgroundColor(true)
      : isEditorRightAligned() && myLastBackgroundColor != null
        ? myLastBackgroundColor
        : myCurrentBackgroundColor
    );
    return myMergedAttributes;
  }

  @ApiStatus.Internal
  public @NotNull TextAttributes getBeforeLineStartBackgroundAttributes() {
    return isEditorRightAligned() && !hasSoftWrap()
           ? getBreakAttributes()
           : new TextAttributes(null, getBreakBackgroundColor(false), null, null, Font.PLAIN);
  }

  private @NotNull HighlighterSweep createSweep(MarkupModelEx markupModel) {
    return new HighlighterSweep(
      myEditor.getColorsScheme(),
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

  private int getHighlighterEnd(int start) {
    if (myHighlighterIterator == null) {
      return myEnd;
    }
    while (!myHighlighterIterator.atEnd()) {
      int end = alignOffset(myReverseIteration ? myHighlighterIterator.getStart() : myHighlighterIterator.getEnd());
      if (myReverseIteration ? end < start : end > start) {
        return end;
      }
      if (myReverseIteration) {
        myHighlighterIterator.retreat();
      }
      else {
        myHighlighterIterator.advance();
      }
    }
    return myEnd;
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

  private int getGuardedBlockEnd(int start) {
    int end = myEnd;
    assert myReverseIteration && start >= end || !myReverseIteration && start <= end;

    if (myUseOnlyFullLineHighlighters) {
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

  private boolean isInSelection(boolean atBreak) {
    return myCurrentSelectionIndex < myCaretData.selectionsSize() &&
           (myReverseIteration ? lessThan(myStartOffset, myCaretData.selectionEnd(myCurrentSelectionIndex, true), !atBreak)
                               : lessThan(myCaretData.selectionStart(myCurrentSelectionIndex, false), myStartOffset, !atBreak));
  }

  private GuardedBlocksIndex buildGuardedBlocks(int start, int end) {
    if (myUseOnlyFullLineHighlighters) {
      return null;
    }
    var guardedBlocks = new GuardedBlocksIndex.DocumentBuilder(myDocument);
    return myReverseIteration
           ? guardedBlocks.build(end, start)
           : guardedBlocks.build(start, end);
  }

  private boolean isInDocumentGuardedBlock(boolean atBreak, boolean beforeBreak) {
    if (myUseOnlyFullLineHighlighters || (atBreak && beforeBreak)) {
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
    myDoc.advance();
    myView.advance();

    boolean fileEnd = myStartOffset == myDocument.getTextLength();
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

    end = myReverseIteration ? Math.max(end, myDoc.getMinSegmentHighlighterEnd()) : Math.min(end, myDoc.getMinSegmentHighlighterEnd());
    end = myReverseIteration ? Math.max(end, myView.getMinSegmentHighlighterEnd()) : Math.min(end, myView.getMinSegmentHighlighterEnd());

    return end;
  }

  private void reinit() {
    setAttributes(myMergedAttributes, false, false);
    myLastBackgroundColor = myCurrentBackgroundColor;
    myCurrentBackgroundColor = myMergedAttributes.getBackgroundColor();
  }

  private void setAttributes(TextAttributes attributes, boolean atBreak, boolean beforeBreak) {
    boolean isInSelection = isInSelection(atBreak);
    boolean isInCaretRow = isInCaretRow(
      !myReverseIteration && (!atBreak || !beforeBreak),
      myReverseIteration || (atBreak && beforeBreak)
    );
    boolean isInGuardedBlock = isInDocumentGuardedBlock(atBreak, beforeBreak);

    TextAttributes syntax = myHighlighterIterator == null || myHighlighterIterator.atEnd()
                            ? null
                            : (atBreak && myStartOffset == (myReverseIteration ? myHighlighterIterator.getEnd() : myHighlighterIterator.getStart()))
                              ? null
                              : myHighlighterIterator.getTextAttributes();
    TextAttributes selection = getSelectionAttributes(isInSelection);
    TextAttributes caret = getCaretRowAttributes(isInCaretRow);
    TextAttributes fold = myCurrentFold != null ? myFoldTextAttributes : null;
    TextAttributes guard = isInGuardedBlock
                           ? new TextAttributes(null, myReadOnlyColor, null, EffectType.BOXED, Font.PLAIN)
                           : null;

    int size = myCurrentHighlighters.size();
    if (size > 1) {
      ContainerUtil.quickSort(myCurrentHighlighters, createByLayerThenByAttributesComparator(myEditor.getColorsScheme()));
    }

    for (int i = 0; i < size; i++) {
      RangeHighlighterEx highlighter = myCurrentHighlighters.get(i);
      if (highlighter.getTextAttributes(myEditor.getColorsScheme()) == TextAttributes.ERASE_MARKER) {
        syntax = null;
      }
    }

    List<TextAttributes> cachedAttributes = myCachedAttributesList;
    if (!cachedAttributes.isEmpty()) cachedAttributes.clear();

    for (int i = 0; i < size; i++) {
      RangeHighlighterEx highlighter = myCurrentHighlighters.get(i);
      if (atBreak &&
          highlighter.getTargetArea() == HighlighterTargetArea.EXACT_RANGE &&
          myStartOffset == (myReverseIteration ? highlighter.getEndOffset() : highlighter.getStartOffset())) {
        continue;
      }
      if (highlighter.getLayer() < HighlighterLayer.SELECTION) {
        if (selection != null) {
          cachedAttributes.add(selection);
          selection = null;
        }
      }

      if (fold != null && highlighter.getLayer() < HighlighterLayer.GUARDED_BLOCKS) {
        cachedAttributes.add(fold);
        fold = null;
      }

      if (guard != null && highlighter.getLayer() < HighlighterLayer.GUARDED_BLOCKS) {
        cachedAttributes.add(guard);
        guard = null;
      }

      if (caret != null && highlighter.getLayer() < HighlighterLayer.CARET_ROW) {
        cachedAttributes.add(caret);
        caret = null;
      }

      if (syntax != null && highlighter.getLayer() < HighlighterLayer.SYNTAX) {
        cachedAttributes.add(syntax);
        syntax = null;
      }

      TextAttributes textAttributes = highlighter.getTextAttributes(myEditor.getColorsScheme());
      if (textAttributes != null && textAttributes != TextAttributes.ERASE_MARKER) {
        cachedAttributes.add(textAttributes);
      }
    }

    if (selection != null) cachedAttributes.add(selection);
    if (fold != null) cachedAttributes.add(fold);
    if (guard != null) cachedAttributes.add(guard);
    if (caret != null) cachedAttributes.add(caret);
    if (syntax != null) cachedAttributes.add(syntax);

    Color fore = null;
    Color back = isInGuardedBlock ? myReadOnlyColor : null;
    @JdkConstants.FontStyle int fontType = Font.PLAIN;

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

    attributes.setAttributes(fore, back, null, null, null, fontType);
    if (effectsBuilder != null) {
      effectsBuilder.applyTo(attributes);
    }
  }

  private boolean isInCaretRow(boolean includeLineStart, boolean includeLineEnd) {
    return myCaretData.caretRowStart() < myStartOffset && myStartOffset < myCaretData.caretRowEnd() ||
           includeLineStart && myStartOffset == myCaretData.caretRowStart() ||
           includeLineEnd && myStartOffset == myCaretData.caretRowEnd();
  }

  private @Nullable TextAttributes getSelectionAttributes(boolean isInSelection) {
    if (myEditor instanceof EditorImpl editor) {
      if (editor.isStickyLinePainting()) {
        // suppress caret selection on sticky lines panel
        return null;
      }
    }
    return isInSelection ? mySelectionAttributes : null;
  }

  private @Nullable TextAttributes getCaretRowAttributes(boolean isInCaretRow) {
    if (myEditor instanceof EditorImpl editor && editor.isStickyLinePainting()) {
      // suppress a caret row background on the sticky lines panel
      return null;
    }
    return isInCaretRow ? myCaretRowAttributes : null;
  }

  private Color getBreakBackgroundColor(boolean lineEnd) {
    return getBreakAttributes(lineEnd).getBackgroundColor();
  }

  private boolean hasSoftWrap() {
    return myEditor.getSoftWrapModel().getSoftWrap(myStartOffset) != null;
  }

  private int alignOffset(int offset) {
    return DocumentUtil.alignToCodePointBoundary(myDocument, offset);
  }

  private int getAlignedStartOffset(RangeHighlighterEx highlighter) {
    return alignOffset(highlighter.getAffectedAreaStartOffset());
  }

  private int getAlignedEndOffset(RangeHighlighterEx highlighter) {
    return alignOffset(highlighter.getAffectedAreaEndOffset());
  }

  private boolean isEditorRightAligned() {
    return myEditor instanceof EditorImpl editorImpl && editorImpl.isRightAligned();
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

    private RangeHighlighterEx firstAdvance() {
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
      if (!highlighter.isValid() || highlighter.isAfterEndOfLine() || highlighter.getTextAttributes(myEditor.getColorsScheme()) == null) {
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
      int o1Length = o1.getAffectedAreaEndOffset() - o1.getAffectedAreaStartOffset();
      int o2Length = o2.getAffectedAreaEndOffset() - o2.getAffectedAreaStartOffset();
      return o1Length - o2Length;
    }
  }
}
