/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.TextChange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * //TODO den add doc
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Jun 8, 2010 12:47:32 PM
 */
public class SoftWrapModelImpl implements SoftWrapModel {

  private static final TIntObjectHashMap<TextChange> EMPTY = new TIntObjectHashMap<TextChange>();

  /** Holds mappings like {@code 'soft wrap start offset at document' -> 'soft wrap indent spaces'}. */
  private final TIntObjectHashMap<TextChange> myWraps = new TIntObjectHashMap<TextChange>();
  private final EditorImpl myEditor;
  private int myFirstVisibleSymbolOffset = -1;
  private int myFirstVisibleLine = -1;
  private int myRightEdgeLocation = -1;
  private boolean myInitialized;

  /**
   * There is a possible case that visible area is changed but document representation of shown at viewport is still the same
   * (e.g. the document doesn't contain long lines and viewport width is expanded).
   * <p/>
   * We don't drop previously registered soft wraps during visible area change then but mark them as <code>'dirty'</code> in order to
   * drop when document content is being repainted.
   */
  private boolean myDataIsDirty;

  /** Holds number of 'active' calls, i.e. number of methods calls of the current object within the current call stack. */
  private int myActive;

  public SoftWrapModelImpl(@NotNull EditorImpl editor) {
    myEditor = editor;
  }

  public boolean isSoftWrappingEnabled() {
    return myEditor.getSettings().isUseSoftWraps();
  }

  //TODO den add doc
  public boolean shouldWrap(char[] chars, int start, int end, Point position) {
    if (!isSoftWrappingEnabled() || containsOnlyWhiteSpaces(chars, start, end)) {
      return false;
    }
    initIfNecessary();
    dropDataIfNecessary();

    if (myWraps.contains(start)) {
      return true;
    }

    if (myRightEdgeLocation < 0) {
      assert false;
      return false;
    }

    //TODO den implement
    boolean b = position.x + (end - start) * 7 > myRightEdgeLocation;
    //TODO den remove
    if (b) {
      int i = 1;
    }
    return b;
  }

  private static boolean containsOnlyWhiteSpaces(char[] chars, int start, int end) {
    int i = CharArrayUtil.shiftForward(chars, start, "\n \t");
    return i >= end;
  }

  //TODO den add doc
  public TextChange wrap(int offset) {
    TextChange result = myWraps.get(offset);
    if (result != null) {
      return result;
    }

    dropDataIfNecessary();
    result = new TextChange("\n    ", offset); //TODO den implement indent calculation on formatting options basis.
    myWraps.put(offset, result);
    return result;
  }

  /**
   * Drops information about registered soft wraps if they are marked as <code>'dirty'</code>.
   *
   * @see #myDataIsDirty
   */
  private void dropDataIfNecessary() {
    if (myDataIsDirty) {
      myWraps.clear();
      myDataIsDirty = false;
    }
  }

  public TextChange getSoftWrap(int offset) {
    return myWraps.get(offset);
  }

  public LogicalPosition adjustLogicalPositionIfNecessary(LogicalPosition logical, VisualPosition visual) {
    if (myActive > 0 || !isSoftWrappingEnabled() || myWraps.isEmpty()) {
      return logical;
    }
    myActive++;
    try {
      return doAdjustLogicalPositionIfNecessary(logical, visual);
    }
    finally {
      myActive--;
    }
  }

  @SuppressWarnings({"AssignmentToForLoopParameter"})
  private LogicalPosition doAdjustLogicalPositionIfNecessary(LogicalPosition logical, VisualPosition visual) {
    CharSequence chars = ((DocumentImpl)myEditor.getDocument()).getCharsNoThreadCheck();
    int softWrapIntroducedLines = 0;
    int linesFromCurrentSoftWrap = 0;
    int symbolsOnCurrentLogicalLine = 0;
    int symbolsOnCurrentVisibleLine = 0;
    int softWrapsSymbolsOnCurrentVisibleLine = 0;

    FoldingModel foldingModel = myEditor.getFoldingModel();
    int currentLine = myFirstVisibleLine;
    for (int i = myFirstVisibleSymbolOffset, max = chars.length(); i < max && currentLine <= visual.line; i++) {
      if (currentLine == visual.line) {
        if (symbolsOnCurrentVisibleLine >= visual.column) {
          int softWrapColumns = softWrapsSymbolsOnCurrentVisibleLine > 0
                                ? symbolsOnCurrentVisibleLine - symbolsOnCurrentLogicalLine : 0;
          return new LogicalPosition(
            logical.line - softWrapIntroducedLines, symbolsOnCurrentLogicalLine, softWrapIntroducedLines,
            linesFromCurrentSoftWrap, softWrapColumns
          );
        }
      }

      FoldRegion region = foldingModel.getCollapsedRegionAtOffset(i);
      if (region != null && !region.isExpanded()) {
        // Assuming that folded region placeholder doesn't contain line feed symbols.
        i = region.getEndOffset();
        continue;
      }

      TextChange softWrap = myWraps.get(i);
      if (softWrap != null) {
        CharSequence softWrapText = softWrap.getText();
        for (int j = 0; j < softWrapText.length(); j++) {
          if (currentLine == visual.line && symbolsOnCurrentVisibleLine >= visual.column) {
            return new LogicalPosition(
              logical.line - softWrapIntroducedLines, symbolsOnCurrentLogicalLine, softWrapIntroducedLines,
              linesFromCurrentSoftWrap, softWrapsSymbolsOnCurrentVisibleLine - symbolsOnCurrentLogicalLine
            );
          }

          if (softWrapText.charAt(j) == '\n') {
            if (currentLine == visual.line) {
              return new LogicalPosition(
                logical.line - softWrapIntroducedLines, symbolsOnCurrentLogicalLine, softWrapIntroducedLines,
                linesFromCurrentSoftWrap, visual.column - symbolsOnCurrentLogicalLine
              );
            }
            else {
              softWrapIntroducedLines++;
              linesFromCurrentSoftWrap++;
              currentLine++;
              symbolsOnCurrentVisibleLine = 0;
              softWrapsSymbolsOnCurrentVisibleLine = 0;
            }
          }
          else {
            symbolsOnCurrentVisibleLine++;
            softWrapsSymbolsOnCurrentVisibleLine++;
          }
        }
      }

      if (currentLine == visual.line && symbolsOnCurrentVisibleLine >= visual.column) {
        return new LogicalPosition(
          logical.line - softWrapIntroducedLines, symbolsOnCurrentLogicalLine, softWrapIntroducedLines,
          linesFromCurrentSoftWrap, softWrapsSymbolsOnCurrentVisibleLine - symbolsOnCurrentLogicalLine
        );
      }

      char c = chars.charAt(i);

      // Check if there is a line break at the document.
      if (c == '\n') {
        if (currentLine == visual.line) {
          int columnToUse = symbolsOnCurrentLogicalLine + visual.column - symbolsOnCurrentVisibleLine;
          return new LogicalPosition(
            logical.line - softWrapIntroducedLines, columnToUse, softWrapIntroducedLines,
            linesFromCurrentSoftWrap, visual.column - columnToUse
          );
        }
        else {
          currentLine++;
          linesFromCurrentSoftWrap = 0;
          symbolsOnCurrentVisibleLine = 0;
          symbolsOnCurrentLogicalLine = 0;
          softWrapsSymbolsOnCurrentVisibleLine = 0;
        }
      }
      else {
        symbolsOnCurrentLogicalLine++;
        symbolsOnCurrentVisibleLine++;
      }
    }
    return logical;
  }

  public int getSoftWrapLineFeedsBefore(VisualPosition position) {
    if (myActive > 0 || !isSoftWrappingEnabled() || myWraps.isEmpty()) {
      return 0;
    }
    myActive++;
    try {
      return doGetSoftWrappedLinesFor(position);
    }
    finally {
      myActive--;
    }
  }

  @SuppressWarnings({"AssignmentToForLoopParameter"})
  private int doGetSoftWrappedLinesFor(VisualPosition position) {
    // It's assumed that there are two possible cases when visual position may differ from logical - folding and soft wraps.
    // The main idea is to start from document offset that corresponds to the top left position of visible editor area and count
    // number of occurred soft wraps before the line of given position (avoiding to count soft wraps from folded regions)
    CharSequence chars = ((DocumentImpl)myEditor.getDocument()).getCharsNoThreadCheck();
    int result = 0;
    int processedLinesNumber = 0;
    FoldingModel foldingModel = myEditor.getFoldingModel();
    int maxLinesToCheck = position.line - myFirstVisibleLine; // Number of the first line that shouldn't be checked.
    int charsInRow = 0;
    for (int i = myFirstVisibleSymbolOffset, max = chars.length(); i < max && processedLinesNumber < maxLinesToCheck; i++) {
      // There is a possible situation that there is a soft wrap at the end of the row denoted by given position.
      // We need to avoid counting it then, hence, we stop processing if current line is a line of visual position
      // and we checked necessary number of chars.
      if (processedLinesNumber == maxLinesToCheck && charsInRow >= position.column) {
        break;
      }

      TextChange softWrap = myWraps.get(i);
      if (softWrap != null) {
        CharSequence softWrapText = softWrap.getText();
        for (int j = 0; j < softWrapText.length(); j++) {
          if (softWrapText.charAt(j) == '\n') {          
            processedLinesNumber++;
            charsInRow = -1; // We set this to '-1' assuming that it's incremented in loop's 'update' block
            continue;
          }
          charsInRow += charToVisibleSymbolsNumber(softWrapText.charAt(j));
        }
        continue;
      }

      char c = chars.charAt(i);

      // Check if there is a line break at the document.
      if (c == '\n') {
        processedLinesNumber++;
        charsInRow = -1; // We set this to '-1' assuming that it's incremented in loop's 'update' block
        continue;
      }

      FoldRegion region = foldingModel.getCollapsedRegionAtOffset(i);
      if (region != null && !region.isExpanded()) {
        i = region.getEndOffset();
        // Assuming that folded region is not represented in more than one line.
        charsInRow += region.getPlaceholderText().length() - 1;
        continue;
      }

      charsInRow += charToVisibleSymbolsNumber(c);
    }
    return result;
  }

  private int charToVisibleSymbolsNumber(char c) {
    if (c != '\t') {
      return 1;
    }
    int result = EditorUtil.getTabSize(myEditor);
    return result <= 0 ? 1 : result;
  }

  private void initIfNecessary() {
    if (myInitialized) {
      return;
    }
    myInitialized = true;
    ScrollingModel scrollingModel = myEditor.getScrollingModel();
    scrollingModel.addVisibleAreaListener(new VisibleAreaListener() {
      public void visibleAreaChanged(VisibleAreaEvent e) {
        updateVisibleAreaChange(e.getNewRectangle());
      }
    });
    updateVisibleAreaChange(scrollingModel.getVisibleArea());
  }

  private void updateVisibleAreaChange(@Nullable Rectangle visibleArea) {
    if (visibleArea == null || !isSoftWrappingEnabled()) {
      return;
    }
    myActive++;
    try {
      doUpdateVisibleAreaChange(visibleArea);
    }
    finally {
      myActive--;
    }
  }

  /**
   * Notifies current model that visible rectangle is changed in order for it to update the state accordingly.
   * <p/>
   * This method is introduced mostly for business-logic vs try/finally separation.
   *
   * @param visibleArea   current visible area of the editor managed by the current model
   */
  private void doUpdateVisibleAreaChange(@NotNull Rectangle visibleArea) {
    // Update information about the first visible line.
    LogicalPosition logicalPositionOfVisibleAreaStart = myEditor.xyToLogicalPosition(visibleArea.getLocation());
    myFirstVisibleSymbolOffset = myEditor.logicalPositionToOffset(logicalPositionOfVisibleAreaStart);
    myFirstVisibleLine = myEditor.logicalToVisualPosition(logicalPositionOfVisibleAreaStart).line;

    // Update right edge.
    int currentRightEdgeLocation = visibleArea.x + visibleArea.width;
    if (myRightEdgeLocation != currentRightEdgeLocation) {
      myDataIsDirty = true;
      myRightEdgeLocation = currentRightEdgeLocation;
    }
  }
}
