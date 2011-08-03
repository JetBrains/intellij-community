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
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.impl.EditorTextRepresentationHelper;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class that is assumed to be used for internal purposes only.
 * <p/>
 * Allows to hold complete information about particular position of document exposed via IJ editor and provides utility
 * methods for working with it.
 *
 * @author Denis Zhdanov
 * @since Sep 1, 2010 12:16:38 PM
 */
class EditorPosition implements Cloneable {

  public int logicalLine;
  public int logicalColumn;
  public int visualLine;
  public int visualColumn;
  public int offset;
  public int softWrapLinesBefore;
  public int softWrapLinesCurrent;
  public int softWrapColumnDiff;
  public int foldedLines;
  public int foldingColumnDiff;
  public int x;
  public char symbol;
  public int symbolWidthInColumns;
  public int symbolWidthInPixels;

  private final Editor myEditor;
  private final EditorTextRepresentationHelper myRepresentationHelper;

  EditorPosition(@NotNull Editor editor, @NotNull EditorTextRepresentationHelper representationHelper) {
    myEditor = editor;
    myRepresentationHelper = representationHelper;
  }

  EditorPosition(@NotNull LogicalPosition logical,
                 int offset,
                 @NotNull Editor editor,
                 @NotNull EditorTextRepresentationHelper representationHelper)
  {
    this(logical, logical.toVisualPosition(), offset, editor, representationHelper);
  }

  EditorPosition(@NotNull LogicalPosition logical,
                 @NotNull VisualPosition visual,
                 int offset,
                 @NotNull Editor editor,
                 @NotNull EditorTextRepresentationHelper representationHelper)
  {
    myEditor = editor;
    myRepresentationHelper = representationHelper;
    logicalLine = logical.line;
    logicalColumn = logical.column;
    softWrapLinesBefore = logical.softWrapLinesBeforeCurrentLogicalLine;
    softWrapLinesCurrent = logical.softWrapLinesOnCurrentLogicalLine;
    softWrapColumnDiff = logical.softWrapColumnDiff;
    foldedLines = logical.foldedLines;
    foldingColumnDiff = logical.foldingColumnDiff;

    visualLine = visual.line;
    visualColumn = visual.column;

    this.offset = offset;
  }

  @NotNull
  public LogicalPosition buildLogicalPosition() {
    return new LogicalPosition(
      logicalLine, logicalColumn, softWrapLinesBefore, softWrapLinesCurrent, softWrapColumnDiff, foldedLines, foldingColumnDiff
    );
  }

  @NotNull
  public VisualPosition buildVisualPosition() {
    return new VisualPosition(visualLine, visualColumn);
  }

  /**
   * Asks current position to change its state assuming that it should point to the start of the next visual line.
   * 
   * @param softWrapAware     flag that indicates if state update should check if current visual line ends with soft wrap
   */
  public void onNewLine(boolean softWrapAware) {
    if (!softWrapAware || myEditor.getSoftWrapModel().getSoftWrap(offset) == null) {
      onNewLine();
      return;
    }
    
    softWrapLinesCurrent++;
    softWrapColumnDiff = -logicalColumn - foldingColumnDiff;
    visualLine++;
    visualColumn = 0;
    x = 0;
  }

  /**
   * Similar as {@link #onNewLine(boolean)} with <code>'false'</code> argument.
   */
  public void onNewLine() {
    softWrapLinesBefore += softWrapLinesCurrent;
    softWrapLinesCurrent = 0;
    softWrapColumnDiff = 0;
    foldingColumnDiff = 0;
    visualLine++;
    visualColumn = 0;
    logicalLine++;
    logicalColumn = 0;
    x = 0;
    offset++;
  }

  /**
   * Calculates width in columns for the collapsed symbols of the given fold region and delegates further processing
   * to {@link #advance(FoldRegion, int)}.
   *
   * @param foldRegion    fold region which end offset should be pointed by the current position
   */
  public void advance(@NotNull FoldRegion foldRegion) {
    Document document = myEditor.getDocument();
    int collapsedSymbolsWidthInColumns = myRepresentationHelper.toVisualColumnSymbolsNumber(
      document.getCharsSequence(), foldRegion.getStartOffset(), foldRegion.getEndOffset(), x
    );
    advance(foldRegion, collapsedSymbolsWidthInColumns);
  }

  /**
   * Updates state of the current processing position in order to point it to the end offset of the given fold region.
   *
   * @param foldRegion                        fold region which end offset should be pointed by the current position
   * @param collapsedSymbolsWidthInColumns    identifies collapsed text width in columns, i.e. width of the last collapsed logical line
   *                                          in columns
   */
  public void advance(@NotNull FoldRegion foldRegion, int collapsedSymbolsWidthInColumns) {
    // We assume that fold region placeholder contains only 'simple' symbols, i.e. symbols that occupy single visual column.
    String placeholder = foldRegion.getPlaceholderText();

    visualColumn += placeholder.length();
    offset = foldRegion.getEndOffset();

    Document document = myEditor.getDocument();
    int endOffsetLogicalLine = document.getLineNumber(foldRegion.getEndOffset());
    if (logicalLine == endOffsetLogicalLine) {
      // Single-line fold region.
      logicalColumn += collapsedSymbolsWidthInColumns;
      foldingColumnDiff += placeholder.length() - collapsedSymbolsWidthInColumns;
    }
    else {
      // Multi-line fold region.
      int linesDiff = endOffsetLogicalLine - logicalLine;
      logicalLine += linesDiff;
      foldedLines += linesDiff;
      logicalColumn = collapsedSymbolsWidthInColumns;
      foldingColumnDiff = visualColumn - logicalColumn - softWrapColumnDiff;
    }
  }

  public void from(@NotNull EditorPosition position) {
    logicalLine = position.logicalLine;
    logicalColumn = position.logicalColumn;
    visualLine = position.visualLine;
    visualColumn = position.visualColumn;
    offset = position.offset;
    softWrapLinesBefore = position.softWrapLinesBefore;
    softWrapLinesCurrent = position.softWrapLinesCurrent;
    softWrapColumnDiff = position.softWrapColumnDiff;
    foldedLines = position.foldedLines;
    foldingColumnDiff = position.foldingColumnDiff;
    x = position.x;
    symbol = position.symbol;
    symbolWidthInColumns = position.symbolWidthInColumns;
    symbolWidthInPixels = position.symbolWidthInPixels;
  }

  @Override
  protected EditorPosition clone() {
    EditorPosition result = new EditorPosition(myEditor, myRepresentationHelper);
    result.logicalLine = logicalLine;
    result.logicalColumn = logicalColumn;
    result.visualLine = visualLine;
    result.visualColumn = visualColumn;
    result.offset = offset;
    result.softWrapLinesBefore = softWrapLinesBefore;
    result.softWrapLinesCurrent = softWrapLinesCurrent;
    result.softWrapColumnDiff = softWrapColumnDiff;
    result.foldedLines = foldedLines;
    result.foldingColumnDiff = foldingColumnDiff;
    result.x = x;
    result.symbol = symbol;
    result.symbolWidthInColumns = symbolWidthInColumns;
    result.symbolWidthInPixels = symbolWidthInPixels;
    return result;
  }

  @Override
  public String toString() {
    return String.format(
      "visual position: (%d; %d); logical position: (%d; %d); offset: %d; soft wraps: before=%d, current=%d, column diff=%d; "
      + "fold regions: lines=%d, column diff=%d", visualLine, visualColumn, logicalLine, logicalColumn, offset, softWrapLinesBefore,
      softWrapLinesCurrent, softWrapColumnDiff, foldedLines, foldingColumnDiff);
  }
}
