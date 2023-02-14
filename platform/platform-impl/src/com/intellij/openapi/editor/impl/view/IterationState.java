// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.CommonProcessors;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.JdkConstants;
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
public class IterationState {
  private static final Logger LOG = Logger.getInstance(IterationState.class);

  public static Comparator<RangeHighlighterEx> createByLayerThenByAttributesComparator(EditorColorsScheme scheme) {
    return (o1, o2) -> {
      final int result = LayerComparator.INSTANCE.compare(o1, o2);
      if (result != 0) {
        return result;
      }

      // There is a possible case when more than one highlighter target the same region (e.g. 'identifier under caret' and 'identifier').
      // We want to prefer the one that defines foreground color to the one that doesn't define (has either fore- or background colors
      // while the other one has only foreground color). See IDEA-85697 for concrete example.
      final TextAttributes a1 = o1.getTextAttributes(scheme);
      final TextAttributes a2 = o2.getTextAttributes(scheme);
      if (a1 == null ^ a2 == null) {
        return a1 == null ? 1 : -1;
      }

      if (a1 == null) {
        return result;
      }

      final Color fore1 = a1.getForegroundColor();
      final Color fore2 = a2.getForegroundColor();
      if (fore1 == null ^ fore2 == null) {
        return fore1 == null ? 1 : -1;
      }

      final Color back1 = a1.getBackgroundColor();
      final Color back2 = a2.getBackgroundColor();
      if (back1 == null ^ back2 == null) {
        return back1 == null ? 1 : -1;
      }

      return result;
    };
  }

  private static final Comparator<RangeHighlighterEx> BY_AFFECTED_END_OFFSET_REVERSED =
    (r1, r2) -> r2.getAffectedAreaEndOffset() - r1.getAffectedAreaEndOffset();

  private static final CaretData NULL_CARET_DATA = new CaretData(-1, -1, ArrayUtilRt.EMPTY_INT_ARRAY, ArrayUtilRt.EMPTY_INT_ARRAY);

  private final TextAttributes myMergedAttributes = new TextAttributes();

  @Nullable
  private final HighlighterIterator myHighlighterIterator;
  private final HighlighterSweep myView;
  private final HighlighterSweep myDoc;

  private int myStartOffset;

  private int myEndOffset;
  private final int myEnd;
  private final int myInitialStartOffset;

  private int myCurrentSelectionIndex = 0;
  private Color myCurrentBackgroundColor;
  private Color myLastBackgroundColor;

  private final List<RangeHighlighterEx> myCurrentHighlighters = new ArrayList<>();

  private final FoldingModelEx myFoldingModel;
  private final TextAttributes myFoldTextAttributes;
  private FoldRegion myCurrentFold;

  private final TextAttributes mySelectionAttributes;
  private final TextAttributes myCaretRowAttributes;
  private final Color myDefaultBackground;
  private final Color myDefaultForeground;
  private final int myDefaultFontType;
  private final List<TextAttributes> myCachedAttributesList = new ArrayList<>(5);
  private final DocumentEx myDocument;
  private final EditorEx myEditor;
  private final CaretData myCaretData;
  private final Color myReadOnlyColor;
  private final boolean myUseOnlyFullLineHighlighters;
  private final boolean myReverseIteration;

  private boolean myNextIsFoldRegion;

  public IterationState(@NotNull EditorEx editor, int start, int end, @Nullable CaretData caretData, boolean useOnlyFullLineHighlighters,
                        boolean useOnlyFontOrForegroundAffectingHighlighters, boolean useFoldRegions, boolean iterateBackwards) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myDocument = editor.getDocument();

    assert !DocumentUtil.isInsideSurrogatePair(myDocument, start);
    assert !DocumentUtil.isInsideSurrogatePair(myDocument, end);

    myInitialStartOffset = start;
    myStartOffset = start;
    myEnd = end;
    myEditor = editor;
    myUseOnlyFullLineHighlighters = useOnlyFullLineHighlighters;
    myReverseIteration = iterateBackwards;

    LOG.assertTrue(myReverseIteration ? myStartOffset >= myEnd : myStartOffset <= myEnd);
    myHighlighterIterator = useOnlyFullLineHighlighters ? null : editor.getHighlighter().createIterator(start);

    myCaretData = ObjectUtils.notNull(caretData, NULL_CARET_DATA);

    myFoldingModel = useFoldRegions ? editor.getFoldingModel() : null;
    myFoldTextAttributes = useFoldRegions ? myFoldingModel.getPlaceholderAttributes() : null;
    mySelectionAttributes = editor.getSelectionModel().getTextAttributes();

    myReadOnlyColor = myEditor.getColorsScheme().getColor(EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR);

    CaretModel caretModel = editor.getCaretModel();
    myCaretRowAttributes = editor.isRendererMode() ? null : caretModel.getTextAttributes();
    myDefaultBackground = editor.getColorsScheme().getDefaultBackground();
    myDefaultForeground = editor.getColorsScheme().getDefaultForeground();
    TextAttributes defaultAttributes = editor.getColorsScheme().getAttributes(HighlighterColors.TEXT);
    myDefaultFontType = defaultAttributes == null ? Font.PLAIN : defaultAttributes.getFontType();

    MarkupModelEx editorMarkup = editor.getMarkupModel();
    EditorColorsScheme scheme = editor.getColorsScheme();
    myView = new HighlighterSweep(scheme, editorMarkup, start, myEnd, useOnlyFullLineHighlighters, useOnlyFontOrForegroundAffectingHighlighters);

    MarkupModelEx docMarkup = editor.getFilteredDocumentMarkupModel();
    myDoc = new HighlighterSweep(scheme, docMarkup, start, myEnd, useOnlyFullLineHighlighters, useOnlyFontOrForegroundAffectingHighlighters);

    myEndOffset = myStartOffset;

    advance();
  }

  public static CaretData createCaretData(@NotNull EditorEx editor) {
    Document document = editor.getDocument();
    CaretModel caretModel = editor.getCaretModel();

    int caretRowStart = caretModel.getVisualLineStart();
    int caretRowEnd = caretModel.getVisualLineEnd();
    if (caretRowEnd == document.getTextLength() && document.getLineCount() > 0 &&
        caretRowEnd > document.getLineStartOffset(document.getLineCount() - 1)) {
      caretRowEnd++;
    }

    List<Caret> carets = editor.getCaretModel().getAllCarets();
    int caretCount = carets.size();
    int[] selectionStarts = new int[caretCount];
    int[] selectionEnds = new int[caretCount];
    for (int i = 0; i < caretCount; i++) {
      Caret caret = carets.get(i);
      selectionStarts[i] = caret.getSelectionStart();
      selectionEnds[i] = caret.getSelectionEnd();
    }
    return new CaretData(caretRowStart, caretRowEnd, selectionStarts, selectionEnds);
  }

  public void retreat(int offset) {
    assert !myReverseIteration && myCaretData == NULL_CARET_DATA && // we need only this case at the moment, this can be relaxed if needed
           offset >= myInitialStartOffset && offset <= myStartOffset &&
           !DocumentUtil.isInsideSurrogatePair(myDocument, offset);
    if (offset == myStartOffset) return;
    if (myHighlighterIterator != null) {
      while (myHighlighterIterator.getStart() > offset) myHighlighterIterator.retreat();
    }
    myCurrentHighlighters.clear();
    myDoc.retreat(offset);
    myView.retreat(offset);
    myEndOffset = offset;
    advance();
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
                             final boolean onlyFullLine,
                             final boolean onlyFontOrForegroundAffecting) {
      myColorsScheme = scheme;
      myMarkupModel = markupModel;
      myOnlyFullLine = onlyFullLine;
      myOnlyFontOrForegroundAffecting = onlyFontOrForegroundAffecting;
      // we have to get all highlighters in advance and sort them by affected offsets
      // since these can be different from the real offsets the highlighters are sorted by in the tree.  (See LINES_IN_RANGE perverts)
      final List<RangeHighlighterEx> list = new ArrayList<>();
      markupModel.processRangeHighlightersOverlappingWith(myReverseIteration ? end : start, myReverseIteration ? start : end,
                                                          new CommonProcessors.CollectProcessor<>(list) {
                                                            @Override
                                                            protected boolean accept(RangeHighlighterEx ex) {
                                                              return (!onlyFullLine ||
                                                                      ex.getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE) &&
                                                                     (!onlyFontOrForegroundAffecting ||
                                                                      EditorUtil.attributesImpactFontStyleOrColor(
                                                                        ex.getTextAttributes(myColorsScheme)));
                                                            }
                                                          });
      highlighters = list.isEmpty() ? RangeHighlighterEx.EMPTY_ARRAY : list.toArray(RangeHighlighterEx.EMPTY_ARRAY);
      Arrays.sort(highlighters, myReverseIteration ? BY_AFFECTED_END_OFFSET_REVERSED : RangeHighlighterEx.BY_AFFECTED_START_OFFSET);

      while (i < highlighters.length) {
        RangeHighlighterEx highlighter = highlighters[i++];
        if (!skipHighlighter(highlighter)) {
          myNextHighlighter = highlighter;
          break;
        }
      }
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
          if (myReverseIteration ?
              getAlignedEndOffset(highlighter) < myStartOffset :
              getAlignedStartOffset(highlighter) > myStartOffset) {
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
      myMarkupModel.processRangeHighlightersOverlappingWith(offset, offset, h -> {
        if ((!myOnlyFullLine || h.getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE) &&
            (!myOnlyFontOrForegroundAffecting || EditorUtil.attributesImpactFontStyleOrColor(h.getTextAttributes(myColorsScheme))) &&
            !skipHighlighter(h)) {
          myCurrentHighlighters.add(h);
        }
        return true;
      });
    }

    private int getMinSegmentHighlighterEnd() {
      if (myNextHighlighter != null) {
        return myReverseIteration ? getAlignedEndOffset(myNextHighlighter) : getAlignedStartOffset(myNextHighlighter);
      }
      return myReverseIteration ? Integer.MIN_VALUE : Integer.MAX_VALUE;
    }
  }

  private boolean skipHighlighter(@NotNull RangeHighlighterEx highlighter) {
    if (!highlighter.isValid() || highlighter.isAfterEndOfLine() || highlighter.getTextAttributes(myEditor.getColorsScheme()) == null) return true;
    final FoldRegion region = myFoldingModel == null ? null :
                              myFoldingModel.getCollapsedRegionAtOffset(highlighter.getAffectedAreaStartOffset());
    if (region != null && region == myFoldingModel.getCollapsedRegionAtOffset(highlighter.getAffectedAreaEndOffset())) return true;
    return false;
  }

  public void advance() {
    myNextIsFoldRegion = false;
    myStartOffset = myEndOffset;
    advanceSegmentHighlighters();
    advanceCurrentSelectionIndex();

    if (!myUseOnlyFullLineHighlighters) {
      myCurrentFold = myFoldingModel == null ? null :
                      myFoldingModel.getCollapsedRegionAtOffset(myReverseIteration ? myStartOffset - 1 : myStartOffset);
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
    return getNearestValueAhead(start, myCaretData.caretRowStart, myCaretData.caretRowEnd);
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
    if (myUseOnlyFullLineHighlighters) {
      return myEnd;
    }
    List<RangeMarker> blocks = myDocument.getGuardedBlocks();
    int result = myEnd;
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < blocks.size(); i++) {
      RangeMarker block = blocks.get(i);
      int nearestValue = getNearestValueAhead(start, alignOffset(block.getStartOffset()), alignOffset(block.getEndOffset()));
      result = myReverseIteration ? Math.max(result, nearestValue) : Math.min(result, nearestValue);
    }
    return result;
  }

  private void advanceCurrentSelectionIndex() {
    while (myCurrentSelectionIndex < myCaretData.selectionsSize() && (myReverseIteration ?
                                                                myStartOffset <= myCaretData.selectionStart(myCurrentSelectionIndex, true) :
                                                                myStartOffset >= myCaretData.selectionEnd(myCurrentSelectionIndex, false))) {
      myCurrentSelectionIndex++;
    }
  }

  private int getSelectionEnd() {
    if (myCurrentSelectionIndex >= myCaretData.selectionsSize()) {
      return myEnd;
    }
    return getNearestValueAhead(myStartOffset,
                                myCaretData.selectionStart(myCurrentSelectionIndex, myReverseIteration),
                                myCaretData.selectionEnd(myCurrentSelectionIndex, myReverseIteration));
  }

  private boolean isInSelection(boolean atBreak) {
    return myCurrentSelectionIndex < myCaretData.selectionsSize() &&
           (myReverseIteration ? lessThan(myStartOffset, myCaretData.selectionEnd(myCurrentSelectionIndex, true), !atBreak)
                               : lessThan(myCaretData.selectionStart(myCurrentSelectionIndex, false), myStartOffset, !atBreak));
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

    //noinspection ForLoopReplaceableByForEach
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

  public TextAttributes getBreakAttributes() {
    return getBreakAttributes(false);
  }

  public TextAttributes getBreakAttributes(boolean beforeBreak) {
    TextAttributes attributes = new TextAttributes();
    setAttributes(attributes, true, beforeBreak);
    return attributes;
  }

  private void setAttributes(TextAttributes attributes, boolean atBreak, boolean beforeBreak) {
    boolean isInSelection = isInSelection(atBreak);
    boolean isInCaretRow = isInCaretRow(!myReverseIteration && (!atBreak || !beforeBreak),
                                        myReverseIteration || (atBreak && beforeBreak));
    boolean isInGuardedBlock = false;
    if (!myUseOnlyFullLineHighlighters) {
      RangeMarker guard = myDocument.getOffsetGuard(myReverseIteration ? myStartOffset - 1 : myStartOffset);
      isInGuardedBlock = guard != null && (!atBreak || myReverseIteration ? guard.getEndOffset() > myStartOffset
                                                                          : guard.getStartOffset() < myStartOffset);
    }

    TextAttributes syntax = myHighlighterIterator == null || myHighlighterIterator.atEnd() ? null
                            : (atBreak &&
                               myStartOffset == (myReverseIteration ? myHighlighterIterator.getEnd() : myHighlighterIterator.getStart()))
                              ? null
                              : myHighlighterIterator.getTextAttributes();

    TextAttributes selection = isInSelection ? mySelectionAttributes : null;
    TextAttributes caret = isInCaretRow ? myCaretRowAttributes : null;
    TextAttributes fold = myCurrentFold != null ? myFoldTextAttributes : null;
    TextAttributes guard = isInGuardedBlock
                           ? new TextAttributes(null, myReadOnlyColor, null, EffectType.BOXED, Font.PLAIN)
                           : null;

    final int size = myCurrentHighlighters.size();
    if (size > 1) {
      ContainerUtil.quickSort(myCurrentHighlighters, createByLayerThenByAttributesComparator(myEditor.getColorsScheme()));
    }

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < size; i++) {
      RangeHighlighterEx highlighter = myCurrentHighlighters.get(i);
      if (highlighter.getTextAttributes(myEditor.getColorsScheme()) == TextAttributes.ERASE_MARKER) {
        syntax = null;
      }
    }

    List<TextAttributes> cachedAttributes = myCachedAttributesList;
    if (!cachedAttributes.isEmpty()) cachedAttributes.clear();

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < size; i++) {
      RangeHighlighterEx highlighter = myCurrentHighlighters.get(i);
      if (atBreak && highlighter.getTargetArea() == HighlighterTargetArea.EXACT_RANGE &&
          myStartOffset == (myReverseIteration ? highlighter.getEndOffset() : highlighter.getStartOffset())) continue;
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
    //noinspection ForLoopReplaceableByForEach
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
    return myStartOffset > myCaretData.caretRowStart && myStartOffset < myCaretData.caretRowEnd ||
           includeLineStart && myStartOffset == myCaretData.caretRowStart || includeLineEnd && myStartOffset == myCaretData.caretRowEnd;
  }

  public boolean atEnd() {
    return myReverseIteration ? myStartOffset <= myEnd : myStartOffset >= myEnd;
  }


  public int getStartOffset() {
    return myStartOffset;
  }

  public int getEndOffset() {
    return myEndOffset;
  }

  @NotNull
  public TextAttributes getMergedAttributes() {
    return myMergedAttributes;
  }

  public FoldRegion getCurrentFold() {
    return myCurrentFold;
  }

  public boolean nextIsFoldRegion() {
    return myNextIsFoldRegion;
  }


  @NotNull
  public TextAttributes getPastLineEndBackgroundAttributes() {
    myMergedAttributes.setBackgroundColor(hasSoftWrap() ? getBreakBackgroundColor(true) :
                                          isEditorRightAligned() && myLastBackgroundColor != null ? myLastBackgroundColor :
                                          myCurrentBackgroundColor);
    return myMergedAttributes;
  }

  @NotNull
  public TextAttributes getBeforeLineStartBackgroundAttributes() {
    return isEditorRightAligned() && !hasSoftWrap() ?
           getBreakAttributes() :
           new TextAttributes(null, getBreakBackgroundColor(false), null, null, Font.PLAIN);
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
    return myEditor instanceof EditorImpl && ((EditorImpl)myEditor).isRightAligned();
  }

  private static class LayerComparator implements Comparator<RangeHighlighterEx> {
    private static final LayerComparator INSTANCE = new LayerComparator();
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

  public static final class CaretData {
    private final int caretRowStart;
    private final int caretRowEnd;
    private final int[] selectionStarts;
    private final int[] selectionEnds;

    private CaretData(int caretRowStart,
                     int caretRowEnd,
                     int[] selectionStarts,
                     int[] selectionEnds) {
      this.caretRowStart = caretRowStart;
      this.caretRowEnd = caretRowEnd;
      this.selectionStarts = selectionStarts;
      this.selectionEnds = selectionEnds;
    }

    private int selectionsSize() {
      return selectionStarts.length;
    }

    private int selectionStart(int index, boolean reverse) {
      return selectionStarts[reverse ? selectionStarts.length - 1 - index : index];
    }

    private int selectionEnd(int index, boolean reverse) {
      return selectionEnds[reverse ? selectionStarts.length - 1 - index : index];
    }

    public static CaretData copyOf(CaretData original, boolean omitCaretRowData) {
      if (original == null || !omitCaretRowData) {
        return original;
      }
      else {
        return new CaretData(-1, -1, original.selectionStarts, original.selectionEnds);
      }
    }
  }
}
