/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.ContainerUtil;
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
// This class should replace com.intellij.openapi.editor.impl.IterationState when new editor rendering engine will become default
public class IterationState {
  private static final Logger LOG = Logger.getInstance(IterationState.class);

  private static final Comparator<RangeHighlighterEx> BY_LAYER_THEN_ATTRIBUTES = (o1, o2) -> {
    final int result = LayerComparator.INSTANCE.compare(o1, o2);
    if (result != 0) {
      return result;
    }

    // There is a possible case when more than one highlighter target the same region (e.g. 'identifier under caret' and 'identifier').
    // We want to prefer the one that defines foreground color to the one that doesn't define (has either fore- or background colors
    // while the other one has only foreground color). See IDEA-85697 for concrete example.
    final TextAttributes a1 = o1.getTextAttributes();
    final TextAttributes a2 = o2.getTextAttributes();
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

  private static final Comparator<RangeHighlighterEx> BY_AFFECTED_END_OFFSET_REVERSED =
    (r1, r2) -> r2.getAffectedAreaEndOffset() - r1.getAffectedAreaEndOffset();

  private final TextAttributes myMergedAttributes = new TextAttributes();

  @Nullable
  private final HighlighterIterator myHighlighterIterator;
  private final HighlighterSweep myView;
  private final HighlighterSweep myDoc;

  private int myStartOffset;

  private int myEndOffset;
  private final int myEnd;

  private final int[] mySelectionStarts;
  private final int[] mySelectionEnds;
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
  private final int myCaretRowStart;
  private final int myCaretRowEnd;
  private final boolean myCaretRowStartsWithSoftWrap;
  private final boolean myCaretRowEndsWithSoftWrap;
  private final List<TextAttributes> myCachedAttributesList = new ArrayList<>(5);
  private final DocumentEx myDocument;
  private final EditorEx myEditor;
  private final Color myReadOnlyColor;
  private final boolean myUseOnlyFullLineHighlighters;
  private final boolean myReverseIteration;

  public IterationState(@NotNull EditorEx editor, int start, int end, boolean useCaretAndSelection, boolean useOnlyFullLineHighlighters,
                        boolean useOnlyFontOrForegroundAffectingHighlighters, boolean useFoldRegions, boolean iterateBackwards) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myDocument = editor.getDocument();
    myStartOffset = start;

    myEnd = end;
    myEditor = editor;
    myUseOnlyFullLineHighlighters = useOnlyFullLineHighlighters;
    myReverseIteration = iterateBackwards;

    LOG.assertTrue(myReverseIteration ? myStartOffset >= myEnd : myStartOffset <= myEnd);
    myHighlighterIterator = useOnlyFullLineHighlighters ? null : editor.getHighlighter().createIterator(start);

    if (!useCaretAndSelection) {
      mySelectionStarts = ArrayUtilRt.EMPTY_INT_ARRAY;
      mySelectionEnds = ArrayUtilRt.EMPTY_INT_ARRAY;
    }
    else {
      List<Caret> carets = editor.getCaretModel().getAllCarets();
      int caretCount = carets.size();
      mySelectionStarts = new int[caretCount];
      mySelectionEnds = new int[caretCount];
      for (int i = 0; i < caretCount; i++) {
        Caret caret = carets.get(i);
        mySelectionStarts[iterateBackwards ? caretCount - i - 1 : i] = caret.getSelectionStart();
        mySelectionEnds[iterateBackwards ? caretCount - i - 1 : i] = caret.getSelectionEnd();
      }
    }

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

    myCaretRowStart = useCaretAndSelection ? caretModel.getVisualLineStart() : -1;
    int visualLineEnd = caretModel.getVisualLineEnd();
    if (visualLineEnd == myDocument.getTextLength() && myDocument.getLineCount() > 0 && 
        visualLineEnd > myDocument.getLineStartOffset(myDocument.getLineCount() - 1)) {
      visualLineEnd++;
    }
    myCaretRowEnd = useCaretAndSelection ? visualLineEnd : -1;
    myCaretRowStartsWithSoftWrap = editor.getSoftWrapModel().getSoftWrap(myCaretRowStart) != null;
    myCaretRowEndsWithSoftWrap = editor.getSoftWrapModel().getSoftWrap(myCaretRowEnd) != null;

    MarkupModelEx editorMarkup = editor.getMarkupModel();
    myView = new HighlighterSweep(editorMarkup, start, myEnd, useOnlyFullLineHighlighters, useOnlyFontOrForegroundAffectingHighlighters);

    MarkupModelEx docMarkup = editor.getFilteredDocumentMarkupModel();
    myDoc = new HighlighterSweep(docMarkup, start, myEnd, useOnlyFullLineHighlighters, useOnlyFontOrForegroundAffectingHighlighters);

    myEndOffset = myStartOffset;

    advance();
  }

  private class HighlighterSweep {
    private RangeHighlighterEx myNextHighlighter;
    int i;
    private final RangeHighlighterEx[] highlighters;

    private HighlighterSweep(@NotNull MarkupModelEx markupModel, int start, int end, 
                             final boolean onlyFullLine, final boolean onlyFontOrForegroundAffecting) {
      // we have to get all highlighters in advance and sort them by affected offsets
      // since these can be different from the real offsets the highlighters are sorted by in the tree.  (See LINES_IN_RANGE perverts)
      final List<RangeHighlighterEx> list = new ArrayList<>();
      markupModel.processRangeHighlightersOverlappingWith(myReverseIteration ? end : start, myReverseIteration ? start : end,
                                                          new CommonProcessors.CollectProcessor<RangeHighlighterEx>(list) {
                                                            @Override
                                                            protected boolean accept(RangeHighlighterEx ex) {
                                                              return (!onlyFullLine || 
                                                                      ex.getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE) && 
                                                                     (!onlyFontOrForegroundAffecting ||
                                                                      EditorUtil.attributesImpactFontStyleOrColor(ex.getTextAttributes()));
                                                            }
                                                          });
      highlighters = list.isEmpty() ? RangeHighlighterEx.EMPTY_ARRAY : list.toArray(new RangeHighlighterEx[list.size()]);
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
            myNextHighlighter.getAffectedAreaEndOffset() < myStartOffset :
            myNextHighlighter.getAffectedAreaStartOffset() > myStartOffset) {
          return;
        }

        myCurrentHighlighters.add(myNextHighlighter);
        myNextHighlighter = null;
      }

      while (i < highlighters.length) {
        RangeHighlighterEx highlighter = highlighters[i++];
        if (!skipHighlighter(highlighter)) {
          if (myReverseIteration ?
              highlighter.getAffectedAreaEndOffset() < myStartOffset :
              highlighter.getAffectedAreaStartOffset() > myStartOffset) {
            myNextHighlighter = highlighter;
            break;
          }
          else {
            myCurrentHighlighters.add(highlighter);
          }
        }
      }
    }

    private int getMinSegmentHighlighterEnd() {
      if (myNextHighlighter != null) {
        return myReverseIteration ? myNextHighlighter.getAffectedAreaEndOffset(): myNextHighlighter.getAffectedAreaStartOffset();
      }
      return myReverseIteration ? Integer.MIN_VALUE : Integer.MAX_VALUE;
    }
  }

  private boolean skipHighlighter(@NotNull RangeHighlighterEx highlighter) {
    if (!highlighter.isValid() || highlighter.isAfterEndOfLine() || highlighter.getTextAttributes() == null) return true;
    final FoldRegion region = myFoldingModel == null ? null :
                              myFoldingModel.getCollapsedRegionAtOffset(highlighter.getAffectedAreaStartOffset());
    if (region != null && region == myFoldingModel.getCollapsedRegionAtOffset(highlighter.getAffectedAreaEndOffset())) return true;
    return false;
  }

  public void advance() {
    myStartOffset = myEndOffset;
    advanceSegmentHighlighters();
    advanceCurrentSelectionIndex();

    if (!myUseOnlyFullLineHighlighters) {
      myCurrentFold = myFoldingModel == null ? null :
                      myFoldingModel.getCollapsedRegionAtOffset(myReverseIteration ? myStartOffset - 1 : myStartOffset);
    }
    if (myCurrentFold != null) {
      myEndOffset = myReverseIteration ? myCurrentFold.getStartOffset() : myCurrentFold.getEndOffset();
    }
    else {
      myEndOffset = getHighlighterEnd(myStartOffset);
      setEndOffsetIfCloser(getSelectionEnd());
      setEndOffsetIfCloser(getMinSegmentHighlightersEnd());
      setEndOffsetIfCloser(getFoldRangesEnd(myStartOffset));
      setEndOffsetIfCloser(getCaretEnd(myStartOffset));
      setEndOffsetIfCloser(getGuardedBlockEnd(myStartOffset));
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
      int end = myReverseIteration ? myHighlighterIterator.getStart() : myHighlighterIterator.getEnd();
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
    return getNearestValueAhead(start, myCaretRowStart, myCaretRowEnd);
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
      int nearestValue = getNearestValueAhead(start, block.getStartOffset(), block.getEndOffset());
      result = myReverseIteration ? Math.max(result, nearestValue) : Math.min(result, nearestValue);
    }
    return result;
  }

  private void advanceCurrentSelectionIndex() {
    while (myCurrentSelectionIndex < mySelectionEnds.length && (myReverseIteration ?
                                                                myStartOffset <= mySelectionStarts[myCurrentSelectionIndex] :
                                                                myStartOffset >= mySelectionEnds[myCurrentSelectionIndex])) {
      myCurrentSelectionIndex++;
    }
  }

  private int getSelectionEnd() {
    if (myCurrentSelectionIndex >= mySelectionStarts.length) {
      return myEnd;
    }
    return getNearestValueAhead(myStartOffset, mySelectionStarts[myCurrentSelectionIndex], mySelectionEnds[myCurrentSelectionIndex]);
  }

  private boolean isInSelection() {
    return myCurrentSelectionIndex < mySelectionStarts.length &&
           (myReverseIteration ?
            myStartOffset <= mySelectionEnds[myCurrentSelectionIndex] :
            myStartOffset >= mySelectionStarts[myCurrentSelectionIndex]);
  }

  private void advanceSegmentHighlighters() {
    myDoc.advance();
    myView.advance();

    boolean fileEnd = myStartOffset == myDocument.getTextLength();
    for (int i = myCurrentHighlighters.size() - 1; i >= 0; i--) {
      RangeHighlighterEx highlighter = myCurrentHighlighters.get(i);
      if (myReverseIteration ?
          highlighter.getAffectedAreaStartOffset() >= myStartOffset :
          fileEnd && highlighter.getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE ? 
          highlighter.getAffectedAreaEndOffset() < myStartOffset : 
          highlighter.getAffectedAreaEndOffset() <= myStartOffset) {
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
        if (highlighter.getAffectedAreaStartOffset() > end) {
          end = highlighter.getAffectedAreaStartOffset();
        }
      }
      else {
        if (highlighter.getAffectedAreaEndOffset() < end) {
          end = highlighter.getAffectedAreaEndOffset();
        }
      }
    }

    end = myReverseIteration ? Math.max(end, myDoc.getMinSegmentHighlighterEnd()) : Math.min(end, myDoc.getMinSegmentHighlighterEnd());
    end = myReverseIteration ? Math.max(end, myView.getMinSegmentHighlighterEnd()) : Math.min(end, myView.getMinSegmentHighlighterEnd());

    return end;
  }

  private void reinit() {
    boolean isInSelection = isInSelection();
    boolean isInCaretRow = isInCaretRow(!myReverseIteration, myReverseIteration);
    boolean isInGuardedBlock = !myUseOnlyFullLineHighlighters &&
                               myDocument.getOffsetGuard(myReverseIteration ? myStartOffset - 1 : myStartOffset) != null;

    TextAttributes syntax = myHighlighterIterator == null || myHighlighterIterator.atEnd() ? 
                            null : myHighlighterIterator.getTextAttributes();

    TextAttributes selection = isInSelection ? mySelectionAttributes : null;
    TextAttributes caret = isInCaretRow ? myCaretRowAttributes : null;
    TextAttributes fold = myCurrentFold != null ? myFoldTextAttributes : null;
    TextAttributes guard = isInGuardedBlock
                           ? new TextAttributes(null, myReadOnlyColor, null, EffectType.BOXED, Font.PLAIN)
                           : null;

    final int size = myCurrentHighlighters.size();
    if (size > 1) {
      ContainerUtil.quickSort(myCurrentHighlighters, BY_LAYER_THEN_ATTRIBUTES);
    }

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < size; i++) {
      RangeHighlighterEx highlighter = myCurrentHighlighters.get(i);
      if (highlighter.getTextAttributes() == TextAttributes.ERASE_MARKER) {
        syntax = null;
      }
    }

    List<TextAttributes> cachedAttributes = myCachedAttributesList;
    cachedAttributes.clear();

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < size; i++) {
      RangeHighlighterEx highlighter = myCurrentHighlighters.get(i);
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

      TextAttributes textAttributes = highlighter.getTextAttributes();
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
    Color effect = null;
    EffectType effectType = null;
    int fontType = 0;

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

      if (effect == null) {
        effect = attrs.getEffectColor();
        effectType = attrs.getEffectType();
      }
    }

    if (fore == null) fore = myDefaultForeground;
    if (back == null) back = myDefaultBackground;
    if (effectType == null) effectType = EffectType.BOXED;
    if (fontType == Font.PLAIN) fontType = myDefaultFontType;

    myMergedAttributes.setAttributes(fore, back, effect, null, effectType, fontType);

    myLastBackgroundColor = myCurrentBackgroundColor;
    myCurrentBackgroundColor = back;
  }

  private boolean isInCaretRow(boolean includeLineStart, boolean includeLineEnd) {
    return myStartOffset > myCaretRowStart && myStartOffset < myCaretRowEnd ||
           includeLineStart && myStartOffset == myCaretRowStart || includeLineEnd && myStartOffset == myCaretRowEnd;
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

  @NotNull
  public TextAttributes getPastLineEndBackgroundAttributes() {
    myMergedAttributes.setBackgroundColor(myEditor.getSoftWrapModel().getSoftWrap(myStartOffset) != null ? getBreakBackgroundColor(true) : 
                                          myCurrentBackgroundColor);
    return myMergedAttributes;
  }
  
  @NotNull
  public TextAttributes getBeforeLineStartBackgroundAttributes() {
    return new TextAttributes(null, getBreakBackgroundColor(false), null, null, Font.PLAIN);
  }

  private Color getBreakBackgroundColor(boolean lineEnd) {
    return Comparing.equal(myCurrentBackgroundColor, myLastBackgroundColor) ? myCurrentBackgroundColor : 
           isInCaretRow(!myCaretRowStartsWithSoftWrap || !lineEnd, myCaretRowEndsWithSoftWrap && lineEnd) ? 
           myCaretRowAttributes.getBackgroundColor() : myDefaultBackground;
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
}
