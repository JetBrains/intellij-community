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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
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

public final class IterationState {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.IterationState");
  
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
  private final int[] myVirtualSelectionStarts;
  private final int[] myVirtualSelectionEnds;
  private int myCurrentSelectionIndex = 0;
  private int myCurrentVirtualSelectionIndex = 0;
  private boolean myCurrentLineHasVirtualSelection;
  private int myCurrentPastLineEndBackgroundSegment; // 0 - before selection, 1 - in selection, 2 - after selection
  private Color myCurrentBackgroundColor;

  private final List<RangeHighlighterEx> myCurrentHighlighters = new ArrayList<>();

  private final FoldingModelEx myFoldingModel;

  private FoldRegion myCurrentFold = null;
  private final TextAttributes myFoldTextAttributes;
  private final TextAttributes mySelectionAttributes;
  private final TextAttributes myCaretRowAttributes;
  private final Color myDefaultBackground;
  private final Color myDefaultForeground;
  private final int myDefaultFontType;
  private final int myCaretRowStart;
  private final int myCaretRowEnd;
  private final List<TextAttributes> myCachedAttributesList = new ArrayList<>(5);
  private final DocumentEx myDocument;
  private final EditorEx myEditor;
  private final Color myReadOnlyColor;
  private final boolean myUseOnlyFullLineHighlighters;

  public IterationState(@NotNull EditorEx editor, int start, int end, boolean useCaretAndSelection) {
    this(editor, start, end, useCaretAndSelection, false);
  }

  public IterationState(@NotNull EditorEx editor, int start, int end, boolean useCaretAndSelection, boolean useOnlyFullLineHighlighters) {
    this(editor, start, end, useCaretAndSelection, useCaretAndSelection, useOnlyFullLineHighlighters);
  }
  
  public IterationState(@NotNull EditorEx editor, int start, int end, boolean useCaretAndSelection, boolean useVirtualSelection, boolean useOnlyFullLineHighlighters) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myDocument = editor.getDocument();
    myStartOffset = start;

    myEnd = end;
    myEditor = editor;
    myUseOnlyFullLineHighlighters = useOnlyFullLineHighlighters;

    LOG.assertTrue(myStartOffset <= myEnd);
    myHighlighterIterator = useOnlyFullLineHighlighters ? null : editor.getHighlighter().createIterator(start);

    if (!useCaretAndSelection) {
      mySelectionStarts = ArrayUtilRt.EMPTY_INT_ARRAY;
      mySelectionEnds = ArrayUtilRt.EMPTY_INT_ARRAY;
      myVirtualSelectionStarts = ArrayUtilRt.EMPTY_INT_ARRAY;
      myVirtualSelectionEnds = ArrayUtilRt.EMPTY_INT_ARRAY;
    }
    else {
      List<Caret> carets = editor.getCaretModel().getAllCarets();
      mySelectionStarts = new int[carets.size()];
      mySelectionEnds = new int[carets.size()];
      myVirtualSelectionStarts = new int[carets.size()];
      myVirtualSelectionEnds = new int[carets.size()];
      for (int i = 0; i < carets.size(); i++) {
        Caret caret = carets.get(i);
        mySelectionStarts[i] = caret.getSelectionStart();
        mySelectionEnds[i] = caret.getSelectionEnd();
        if (useVirtualSelection) {
          myVirtualSelectionStarts[i] = caret.getSelectionStartPosition().column - editor.offsetToVisualPosition(mySelectionStarts[i]).column;
          myVirtualSelectionEnds[i] = caret.getSelectionEndPosition().column - editor.offsetToVisualPosition(mySelectionEnds[i]).column;
        }
      }
    }

    myFoldingModel = editor.getFoldingModel();
    myFoldTextAttributes = myFoldingModel.getPlaceholderAttributes();
    mySelectionAttributes = editor.getSelectionModel().getTextAttributes();

    myReadOnlyColor = myEditor.getColorsScheme().getColor(EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR);

    CaretModel caretModel = editor.getCaretModel();
    myCaretRowAttributes = editor.isRendererMode() ? null : caretModel.getTextAttributes();
    myDefaultBackground = editor.getColorsScheme().getDefaultBackground();
    myDefaultForeground = editor.getColorsScheme().getDefaultForeground();
    TextAttributes defaultAttributes = editor.getColorsScheme().getAttributes(HighlighterColors.TEXT);
    myDefaultFontType = defaultAttributes == null ? Font.PLAIN : defaultAttributes.getFontType();

    myCaretRowStart = caretModel.getVisualLineStart();
    myCaretRowEnd = caretModel.getVisualLineEnd();

    MarkupModelEx editorMarkup = editor.getMarkupModel();
    myView = new HighlighterSweep(editorMarkup, start, myEnd, useOnlyFullLineHighlighters);

    MarkupModelEx docMarkup = editor.getFilteredDocumentMarkupModel();
    myDoc = new HighlighterSweep(docMarkup, start, myEnd, useOnlyFullLineHighlighters);

    myEndOffset = myStartOffset;

    advance();
  }

  private class HighlighterSweep {
    private RangeHighlighterEx myNextHighlighter;
    int i;
    private final RangeHighlighterEx[] highlighters;

    private HighlighterSweep(@NotNull MarkupModelEx markupModel, int start, int end, final boolean onlyFullLine) {
      // we have to get all highlighters in advance and sort them by affected offsets
      // since these can be different from the real offsets the highlighters are sorted by in the tree.  (See LINES_IN_RANGE perverts)
      final List<RangeHighlighterEx> list = new ArrayList<>();
      markupModel.processRangeHighlightersOverlappingWith(start, end, new CommonProcessors.CollectProcessor<RangeHighlighterEx>(list) {
        @Override
        protected boolean accept(RangeHighlighterEx ex) {
          return !onlyFullLine || ex.getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE;
        }
      });
      highlighters = list.isEmpty() ? RangeHighlighterEx.EMPTY_ARRAY : list.toArray(new RangeHighlighterEx[list.size()]);
      Arrays.sort(highlighters, RangeHighlighterEx.BY_AFFECTED_START_OFFSET);

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
        if (myNextHighlighter.getAffectedAreaStartOffset() > myStartOffset) {
          return;
        }

        myCurrentHighlighters.add(myNextHighlighter);
        myNextHighlighter = null;
      }

      while (i < highlighters.length) {
        RangeHighlighterEx highlighter = highlighters[i++];
        if (!skipHighlighter(highlighter)) {
          if (highlighter.getAffectedAreaStartOffset() > myStartOffset) {
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
        return myNextHighlighter.getAffectedAreaStartOffset();
      }
      return Integer.MAX_VALUE;
    }
  }

  private boolean skipHighlighter(@NotNull RangeHighlighterEx highlighter) {
    if (!highlighter.isValid() || highlighter.isAfterEndOfLine() || highlighter.getTextAttributes() == null) return true;
    final FoldRegion region = myFoldingModel.getCollapsedRegionAtOffset(highlighter.getAffectedAreaStartOffset());
    if (region != null && region == myFoldingModel.getCollapsedRegionAtOffset(highlighter.getAffectedAreaEndOffset())) return true;
    return false;
  }

  public void advance() {
    myStartOffset = myEndOffset;
    advanceSegmentHighlighters();
    advanceCurrentSelectionIndex();
    advanceCurrentVirtualSelectionIndex();

    if (!myUseOnlyFullLineHighlighters) {
      myCurrentFold = myFoldingModel.getCollapsedRegionAtOffset(myStartOffset);
    }
    if (myCurrentFold != null) {
      myEndOffset = myCurrentFold.getEndOffset();
    }
    else {
      myEndOffset = Math.min(getHighlighterEnd(myStartOffset), getSelectionEnd());
      myEndOffset = Math.min(myEndOffset, getMinSegmentHighlightersEnd());
      myEndOffset = Math.min(myEndOffset, getFoldRangesEnd(myStartOffset));
      myEndOffset = Math.min(myEndOffset, getCaretEnd(myStartOffset));
      myEndOffset = Math.min(myEndOffset, getGuardedBlockEnd(myStartOffset));
    }

    reinit();
  }

  private int getHighlighterEnd(int start) {
    if (myHighlighterIterator == null) {
      return myEnd;
    }
    while (!myHighlighterIterator.atEnd()) {
      int end = myHighlighterIterator.getEnd();
      if (end > start) {
        return end;
      }
      myHighlighterIterator.advance();
    }
    return myEnd;
  }

  private int getCaretEnd(int start) {
    if (myCaretRowStart > start) {
      return myCaretRowStart;
    }

    if (myCaretRowEnd > start) {
      return myCaretRowEnd;
    }

    return myEnd;
  }

  private int getGuardedBlockEnd(int start) {
    if (myUseOnlyFullLineHighlighters) {
      return myEnd;
    }
    List<RangeMarker> blocks = myDocument.getGuardedBlocks();
    int min = myEnd;
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < blocks.size(); i++) {
      RangeMarker block = blocks.get(i);
      if (block.getStartOffset() > start) {
        min = Math.min(min, block.getStartOffset());
      }
      else if (block.getEndOffset() > start) {
        min = Math.min(min, block.getEndOffset());
      }
    }
    return min;
  }

  private void advanceCurrentSelectionIndex() {
    while (myCurrentSelectionIndex < mySelectionEnds.length && myStartOffset >= mySelectionEnds[myCurrentSelectionIndex]) {
      myCurrentSelectionIndex++;
    }
  }

  private void advanceCurrentVirtualSelectionIndex() {
    while (myCurrentVirtualSelectionIndex < mySelectionEnds.length
           && (myStartOffset > mySelectionEnds[myCurrentVirtualSelectionIndex] || myVirtualSelectionEnds[myCurrentVirtualSelectionIndex] <= 0)) {
      myCurrentVirtualSelectionIndex++;
    }
  }

  private int getSelectionEnd() {
    if (myCurrentSelectionIndex >= mySelectionStarts.length) {
      return myEnd;
    }
    if (myStartOffset < mySelectionStarts[myCurrentSelectionIndex]) {
      return mySelectionStarts[myCurrentSelectionIndex];
    }
    return mySelectionEnds[myCurrentSelectionIndex];
  }

  private boolean isInSelection() {
    return myCurrentSelectionIndex < mySelectionStarts.length && myStartOffset >= mySelectionStarts[myCurrentSelectionIndex];
  }

  private void advanceSegmentHighlighters() {
    myDoc.advance();
    myView.advance();

    for (int i = myCurrentHighlighters.size() - 1; i >= 0; i--) {
      RangeHighlighterEx highlighter = myCurrentHighlighters.get(i);
      if (highlighter.getAffectedAreaEndOffset() <= myStartOffset) {
        myCurrentHighlighters.remove(i);
      }
    }
  }

  private int getFoldRangesEnd(int startOffset) {
    if (myUseOnlyFullLineHighlighters) {
      return myEnd;
    }
    int end = myEnd;
    FoldRegion[] topLevelCollapsed = myFoldingModel.fetchTopLevel();
    if (topLevelCollapsed != null) {
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

    return end;
  }

  private int getMinSegmentHighlightersEnd() {
    int end = myEnd;

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < myCurrentHighlighters.size(); i++) {
      RangeHighlighterEx highlighter = myCurrentHighlighters.get(i);
      if (highlighter.getAffectedAreaEndOffset() < end) {
        end = highlighter.getAffectedAreaEndOffset();
      }
    }

    end = Math.min(end, myDoc.getMinSegmentHighlighterEnd());
    end = Math.min(end, myView.getMinSegmentHighlighterEnd());

    return end;
  }

  private void reinit() {
    if (myHighlighterIterator != null && myHighlighterIterator.atEnd()) {
      return;
    }

    boolean isInSelection = isInSelection();
    boolean isInCaretRow = myStartOffset >= myCaretRowStart && myStartOffset < myCaretRowEnd;
    boolean isInGuardedBlock = !myUseOnlyFullLineHighlighters && myDocument.getOffsetGuard(myStartOffset) != null;

    TextAttributes syntax = myHighlighterIterator == null ? null : myHighlighterIterator.getTextAttributes();

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

    int selectionAttributesIndex = -1; // a 'would-be' or real position of selection attributes in attributes list

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < size; i++) {
      RangeHighlighterEx highlighter = myCurrentHighlighters.get(i);
      if (highlighter.getLayer() < HighlighterLayer.SELECTION) {
        if (selectionAttributesIndex < 0) {
          selectionAttributesIndex = cachedAttributes.size();
        }
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

    if (selectionAttributesIndex < 0) {
      selectionAttributesIndex = cachedAttributes.size();
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

    boolean selectionBackgroundIsPotentiallyVisible = cachedAttributes.isEmpty();

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < cachedAttributes.size(); i++) {
      TextAttributes attrs = cachedAttributes.get(i);

      if (fore == null) {
        fore = attrs.getForegroundColor();
      }

      if (back == null) {
        if (isInSelection && i == selectionAttributesIndex || !isInSelection && i >= selectionAttributesIndex) {
          selectionBackgroundIsPotentiallyVisible = true;
        }
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

    myCurrentBackgroundColor = back;
    if (selectionBackgroundIsPotentiallyVisible && myCurrentVirtualSelectionIndex < mySelectionStarts.length && myStartOffset == mySelectionEnds[myCurrentVirtualSelectionIndex]) {
      myCurrentLineHasVirtualSelection = true;
      myCurrentPastLineEndBackgroundSegment = myVirtualSelectionStarts[myCurrentVirtualSelectionIndex] > 0 ? 0 : 1;
    }
    else {
      myCurrentLineHasVirtualSelection = false;
      myCurrentPastLineEndBackgroundSegment = 0;
    }
  }

  public boolean atEnd() {
    return myStartOffset >= myEnd;
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

  public boolean hasPastLineEndBackgroundSegment() {
    return myCurrentLineHasVirtualSelection && myCurrentPastLineEndBackgroundSegment < 2;
  }

  public int getPastLineEndBackgroundSegmentWidth() {
    switch (myCurrentPastLineEndBackgroundSegment) {
      case 0: return myVirtualSelectionStarts[myCurrentVirtualSelectionIndex];
      case 1: return myVirtualSelectionEnds[myCurrentVirtualSelectionIndex] - myVirtualSelectionStarts[myCurrentVirtualSelectionIndex];
      default: return 0;
    }
  }

  @NotNull
  public TextAttributes getPastLineEndBackgroundAttributes() {
    myMergedAttributes.setBackgroundColor(myCurrentPastLineEndBackgroundSegment == 1 ? mySelectionAttributes.getBackgroundColor() : myCurrentBackgroundColor);
    return myMergedAttributes;
  }

  public void advanceToNextPastLineEndBackgroundSegment() {
    myCurrentPastLineEndBackgroundSegment++;
  }

  public boolean hasPastFileEndBackgroundSegments() {
    myCurrentLineHasVirtualSelection = myVirtualSelectionEnds.length > 0
                && myVirtualSelectionEnds[myVirtualSelectionEnds.length - 1] > 0
                && myEndOffset == myEnd
                && mySelectionEnds[mySelectionStarts.length - 1] == myEndOffset;
    if (myCurrentLineHasVirtualSelection) {
      myCurrentVirtualSelectionIndex = myVirtualSelectionStarts.length - 1;
      myCurrentPastLineEndBackgroundSegment = myVirtualSelectionStarts[myCurrentVirtualSelectionIndex] > 0 ? 0 : 1;
      myCurrentBackgroundColor = myEndOffset >= myCaretRowStart ? myCaretRowAttributes.getBackgroundColor() : myDefaultBackground;
    }
    return myCurrentLineHasVirtualSelection;
  }

  @Nullable
  public Color getPastFileEndBackground() {
    boolean isInCaretRow = myEditor.getCaretModel().getLogicalPosition().line >= myDocument.getLineCount() - 1;

    Color caret = isInCaretRow && myCaretRowAttributes != null ? myCaretRowAttributes.getBackgroundColor() : null;

    ContainerUtil.quickSort(myCurrentHighlighters, LayerComparator.INSTANCE);

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < myCurrentHighlighters.size(); i++) {
      RangeHighlighterEx highlighter = myCurrentHighlighters.get(i);
      if (caret != null && highlighter.getLayer() < HighlighterLayer.CARET_ROW) {
        return caret;
      }

      if (highlighter.getTargetArea() != HighlighterTargetArea.LINES_IN_RANGE
          || myDocument.getLineNumber(highlighter.getEndOffset()) < myDocument.getLineCount() - 1) {
        continue;
      }

      TextAttributes textAttributes = highlighter.getTextAttributes();
      if (textAttributes != null) {
        Color backgroundColor = textAttributes.getBackgroundColor();
        if (backgroundColor != null) return backgroundColor;
      }
    }

    return caret;
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
