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
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.SoftWrapModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.softwrap.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default {@link SoftWrapModelEx} implementation.
 * <p/>
 * //TODO den add doc
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Jun 8, 2010 12:47:32 PM
 */
public class SoftWrapModelImpl implements SoftWrapModelEx {

  private final CharBuffer myCharBuffer = CharBuffer.allocate(1);

  private final SoftWrapsStorage         myStorage;
  private final SoftWrapPainter          myPainter;
  private final SoftWrapApplianceManager myApplianceManager;

  /**
   * Holds logical lines where soft wraps should be removed.
   * <p/>
   * The general idea is to do the following:
   * <ul>
   *   <li>listen for document changes, mark all soft wraps that belong to modified logical line as <code>'dirty'</code>;</li>
   *   <li>remove soft wraps marked as 'dirty' on repaint;</li>
   * </ul>
   */
  private final TIntHashSet myDirtyLines = new TIntHashSet();

  private final EditorEx myEditor;
  private       boolean  myInitialized;
  /** Holds number of 'active' calls, i.e. number of methods calls of the current object within the current call stack. */
  private       int      myActive;

  public SoftWrapModelImpl(@NotNull EditorEx editor) {
    this(editor, new SoftWrapsStorage(), new CompositeSoftWrapPainter(editor));
  }

  public SoftWrapModelImpl(@NotNull final EditorEx editor, @NotNull SoftWrapsStorage storage, @NotNull SoftWrapPainter painter) {
    this(editor, storage, painter, new DefaultSoftWrapApplianceManager(storage, editor, painter));
  }

  public SoftWrapModelImpl(@NotNull EditorEx editor, @NotNull SoftWrapsStorage storage, @NotNull SoftWrapPainter painter,
                           @NotNull SoftWrapApplianceManager applianceManager)
  {
    myEditor = editor;
    myStorage = storage;
    myPainter = painter;
    myApplianceManager = applianceManager;
  }

  public boolean isSoftWrappingEnabled() {
    return myEditor.getSettings().isUseSoftWraps();
  }

  @Nullable
  public TextChange getSoftWrap(int offset) {
    if (!isSoftWrappingEnabled()) {
      return null;
    }
    if (myActive <= 0) {
      dropDataIfNecessary();
    }
    return myStorage.getSoftWrap(offset);
  }

  @NotNull
  @Override
  public List<TextChange> getSoftWrapsForRange(int start, int end) {
    if (!isSoftWrappingEnabled()) {
      return Collections.emptyList();
    }
    int i = myStorage.getSoftWrapIndex(start);
    if (i < 0) {
      i = -i - 1;
    }

    List<TextChange> softWraps = myStorage.getSoftWraps();
    List<TextChange> result = null;
    for (; i < softWraps.size(); i++) {
      TextChange softWrap = softWraps.get(i);
      if (softWrap.getStart() >= end) {
        break;
      }
      if (result == null) {
        result = new ArrayList<TextChange>();
      }
      result.add(softWrap);
    }
    return result == null ? Collections.<TextChange>emptyList() : result;
  }

  @Override
  @NotNull
  public List<TextChange> getSoftWrapsForLine(int documentLine) {
    if (!isSoftWrappingEnabled()) {
      return Collections.emptyList();
    }
    Document document = myEditor.getDocument();
    int start = document.getLineStartOffset(documentLine);
    int end = document.getLineEndOffset(documentLine);
    return getSoftWrapsForRange(start, end);
  }

  /**
   * @return    total number of soft wrap-introduced new visual lines
   */
  public int getSoftWrapsIntroducedLinesNumber() {
    if (!isSoftWrappingEnabled()) {
      return 0;
    }
    int result = 0;
    for (TextChange myWrap : myStorage.getSoftWraps()) {
      result += StringUtil.countNewLines(myWrap.getText());
    }
    return result;
  }

  public void registerSoftWrapIfNecessary(@NotNull char[] chars, int start, int end, int x, int fontType) {
    if (!isSoftWrappingEnabled()) {
      return;
    }
    initIfNecessary();
    dropDataIfNecessary();

    myActive++;
    try {
      myApplianceManager.registerSoftWrapIfNecessary(chars, start, end, x, fontType);
    }
    finally {
      myActive--;
    }
  }

  public List<TextChange> getRegisteredSoftWraps() {
    if (!isSoftWrappingEnabled()) {
      return Collections.emptyList();
    }
    return myStorage.getSoftWraps();
  }

  private void dropDataIfNecessary() {
    Document document = myEditor.getDocument();
    for (TIntIterator it = myDirtyLines.iterator(); it.hasNext();) {
      int line = it.next();
      int start = document.getLineStartOffset(line);
      int end = document.getLineEndOffset(line);
      myStorage.removeInRange(start, end);
    }
    myDirtyLines.clear();
  }

  @Override
  public int paint(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight) {
    if (!isSoftWrappingEnabled()) {
      return 0;
    }
    return myPainter.paint(g, drawingType, x,  y, lineHeight);
  }

  @Override
  public int getMinDrawingWidth(@NotNull SoftWrapDrawingType drawingType) {
    return myPainter.getMinDrawingWidth(drawingType);
  }

  @NotNull
  public LogicalPosition adjustLogicalPosition(@NotNull LogicalPosition defaultLogical, @NotNull VisualPosition visual) {
    if (myActive > 0 || !isSoftWrappingEnabled() || myStorage.isEmpty() || myEditor.getDocument().getTextLength() <= 0) {
      return defaultLogical;
    }

    if (defaultLogical.visualPositionAware) {
      return defaultLogical;
    }

    myActive++;
    try {
      return doAdjustLogicalPosition(defaultLogical, visual);
    }
    finally {
      myActive--;
    }
  }

  private LogicalPosition doAdjustLogicalPosition(LogicalPosition defaultLogical, VisualPosition visual) {
    Document document = myEditor.getDocument();
    int maxOffset = document.getLineEndOffset(Math.min(defaultLogical.line, document.getLineCount() - 1));

    // This index points to registered soft wrap that is guaranteed to be located after the target visual line.
    int endIndex = myStorage.getSoftWrapIndex(maxOffset + 1);
    if (endIndex < 0) {
      endIndex = -endIndex - 1;
    }

    int softWrapLinesBeforeCurrentLogicalLine = 0;
    int softWrapLinesOnCurrentLogicalLine = 0;
    int lastSoftWrapLogicalLine = -1;

    FoldingModel foldingModel = myEditor.getFoldingModel();
    int i = 0;
    List<TextChange> softWraps = myStorage.getSoftWraps();
    int max = Math.min(softWraps.size(), endIndex);
    for (; i < max; i++) {
      TextChange softWrap = softWraps.get(i);
      if (foldingModel.isOffsetCollapsed(softWrap.getStart())) {
        continue;
      }

      int currentSoftWrapLineFeeds = StringUtil.countNewLines(softWrap.getText());
      int softWrapLine = document.getLineNumber(softWrap.getStart());
      int visualLineBeforeSoftWrapAppliance = myEditor.logicalToVisualPosition(new LogicalPosition(softWrapLine, 0)).line
                                              + softWrapLinesBeforeCurrentLogicalLine + softWrapLinesOnCurrentLogicalLine;
      if (visualLineBeforeSoftWrapAppliance > visual.line) {
        softWrapLinesBeforeCurrentLogicalLine += softWrapLinesOnCurrentLogicalLine;
        int logicalLine = defaultLogical.line - softWrapLinesBeforeCurrentLogicalLine;
        LogicalPosition foldingUnawarePosition = new LogicalPosition(
          logicalLine, defaultLogical.column, softWrapLinesBeforeCurrentLogicalLine, 0, 0, 0, 0
        );
        return adjustFoldingData(foldingModel, foldingUnawarePosition);
      }

      if (lastSoftWrapLogicalLine >= 0 && lastSoftWrapLogicalLine != softWrapLine) {
        softWrapLinesBeforeCurrentLogicalLine += softWrapLinesOnCurrentLogicalLine;
        softWrapLinesOnCurrentLogicalLine = 0;
      }
      lastSoftWrapLogicalLine = softWrapLine;

      int visualLineAfterSoftWrapAppliance = visualLineBeforeSoftWrapAppliance + currentSoftWrapLineFeeds;
      if (visualLineAfterSoftWrapAppliance < visual.line) {
        softWrapLinesOnCurrentLogicalLine += currentSoftWrapLineFeeds;
        continue;
      }

      // If we're here that means that current soft wrap affects logical line that is matched to the given visual line.
      // We iterate from the logical line start then in order to calculate resulting logical position.
      Context context = new Context(
        defaultLogical, visual, softWrapLinesBeforeCurrentLogicalLine, softWrapLinesOnCurrentLogicalLine,
        visualLineBeforeSoftWrapAppliance, foldingModel
      );
      int startLineOffset = document.getLineStartOffset(softWrapLine);
      int endLineOffset = document.getLineEndOffset(softWrapLine);
      CharSequence documentText = document.getCharsSequence();
      for (int j = startLineOffset; j < endLineOffset; j++) {

        // Process soft wrap at the current offset if any.
        TextChange softWrapToProcess = myStorage.getSoftWrap(j);
        if (softWrapToProcess != null && j >= softWrap.getStart()) {
          CharSequence softWrapText = softWrapToProcess.getText();
          for (int k = 0; k < softWrapText.length(); k++) {
            LogicalPosition result = context.onSoftWrapSymbol(softWrapText.charAt(k));
            if (result != null) {
              return result;
            }
          }
          context.onSoftWrapSymbol('a'); // Emulate 'after soft wrap' sign
        }

        // Process document symbol.
        LogicalPosition result = context.onNonSoftWrapSymbol(documentText.charAt(j));
        if (result != null) {
          return result;
        }
      }

      // If we are here that means that target visual position is located at virtual space after the line end.
      int logicalLine = defaultLogical.line - softWrapLinesBeforeCurrentLogicalLine
                        - context.softWrapLinesOnCurrentLineBeforeTargetSoftWrap - context.targetSoftWrapLines;
      int logicalColumn = context.symbolsOnCurrentLogicalLine + visual.column - context.symbolsOnCurrentVisualLine;
      int softWrapColumnDiff = visual.column - logicalColumn;
      LogicalPosition foldingUnawarePosition = new LogicalPosition(
        logicalLine, logicalColumn, softWrapLinesBeforeCurrentLogicalLine,
        context.softWrapLinesOnCurrentLineBeforeTargetSoftWrap + context.targetSoftWrapLines, softWrapColumnDiff, 0, 0
      );
      return adjustFoldingData(foldingModel, foldingUnawarePosition);
    }

    // If we are here that means that there is no soft wrap on a logical line that corresponds to the target visual line.
    softWrapLinesBeforeCurrentLogicalLine += softWrapLinesOnCurrentLogicalLine;
    int logicalLine = defaultLogical.line - softWrapLinesBeforeCurrentLogicalLine;
    LogicalPosition foldingUnaware = new LogicalPosition(
      logicalLine, defaultLogical.column, softWrapLinesBeforeCurrentLogicalLine, 0, 0, 0, 0
    );
    return adjustFoldingData(foldingModel, foldingUnaware);
  }

  /**
   * Builds folding-aware logical position on the basis of the given folding-unaware position and folding model
   *
   * @param foldingModel    folding model to use for retrieving information about folding
   * @param position        folding-unaware logical position
   * @return                folding-aware logical position
   */
  private LogicalPosition adjustFoldingData(FoldingModel foldingModel, LogicalPosition position) {
    int offset = myEditor.logicalPositionToOffset(position);
    int foldedLines = 0;
    int foldColumnDiff = 0;
    int softWrapColumnDiff = position.softWrapColumnDiff;
    Document document = myEditor.getDocument();
    CharSequence text = document.getCharsSequence();
    int targetLine = document.getLineNumber(offset);
    for (FoldRegion foldRegion : foldingModel.getAllFoldRegions()) {
      if (foldRegion.getStartOffset() >= offset) {
        break;
      }

      if (foldRegion.isExpanded() || !foldRegion.isValid()) {
        continue;
      }

      int foldingStartLine = document.getLineNumber(foldRegion.getStartOffset());
      int foldingEndLine = document.getLineNumber(foldRegion.getEndOffset());
      foldedLines += Math.min(targetLine, foldingEndLine) - foldingStartLine;

      // Process situation when target offset is located inside the folded region.
      if (offset >= foldRegion.getStartOffset() && offset < foldRegion.getEndOffset()) {
        // Our purpose is to define folding data in order to point to the visual folding start.
        int visualFoldingStartColumn = calculateVisualFoldingStartColumn(foldRegion);
        foldColumnDiff = visualFoldingStartColumn - position.column - softWrapColumnDiff;
        break;
      }

      if (foldingEndLine != position.line) {
        continue;
      }

      // We know here that offset is at the same line where folding ends and is located after it. Hence, we process that as follows:
      //   1. Check if the folding is single-line;
      //   2.1. Process as follows if the folding is single-line:
      //     3.1. Calculate column difference introduced by the folding;
      //   2.2. Process as follows if the folding is multi-line:
      //     3.2. Calculate visual column of folding start;
      //     4.2. Calculate number of columns between target offset and folding end;
      //     5.1. Calculate folding placeholder width in columns;
      //     6.1. Calculate resulting offset visual column;
      //     7.1. Calculate resulting folding column diff;

      if (foldingStartLine == foldingEndLine) {
        foldColumnDiff = toVisualColumnSymbolsNumber(foldRegion.getPlaceholderText())
                         - toVisualColumnSymbolsNumber(text, foldRegion.getStartOffset(), foldRegion.getEndOffset());
      }
      else {
        int endOffsetOfLineWithFoldingEnd = document.getLineEndOffset(foldingEndLine);
        int columnsBetweenFoldingEndAndOffset = toVisualColumnSymbolsNumber(text, foldRegion.getEndOffset(), endOffsetOfLineWithFoldingEnd);
        LogicalPosition foldingEndLineEndPosition = myEditor.offsetToLogicalPosition(endOffsetOfLineWithFoldingEnd);
        if (position.column > foldingEndLineEndPosition.column) {
          columnsBetweenFoldingEndAndOffset += position.column - foldingEndLineEndPosition.column;
        }
        int visualFoldingStartColumn = calculateVisualFoldingStartColumn(foldRegion);
        int foldingPlaceholderWidth = toVisualColumnSymbolsNumber(foldRegion.getPlaceholderText());
        int visual = columnsBetweenFoldingEndAndOffset + visualFoldingStartColumn + foldingPlaceholderWidth;
        foldColumnDiff = visual - position.column;
        break;
      }
    }

    return new LogicalPosition(
      position.line, position.column, position.softWrapLinesBeforeCurrentLogicalLine, position.softWrapLinesOnCurrentLogicalLine,
      softWrapColumnDiff, foldedLines, foldColumnDiff
    );
  }

  private int calculateVisualFoldingStartColumn(FoldRegion region) {
    Document document = myEditor.getDocument();
    int foldingStartOffset = region.getStartOffset();
    int logicalLine = document.getLineNumber(foldingStartOffset);
    int logicalLineStartOffset = document.getLineStartOffset(logicalLine);

    int softWrapIndex = myStorage.getSoftWrapIndex(logicalLineStartOffset);
    if (softWrapIndex < 0) {
      softWrapIndex = -softWrapIndex - 1;
    }

    List<TextChange> softWraps = myStorage.getSoftWraps();
    int startOffsetOfVisualLineWithFoldingStart = logicalLineStartOffset;
    for (; softWrapIndex < softWraps.size(); softWrapIndex++) {
      TextChange softWrap = softWraps.get(softWrapIndex);
      if (softWrap.getStart() >= foldingStartOffset) {
        break;
      }

      startOffsetOfVisualLineWithFoldingStart = softWrap.getStart();
    }

    assert startOffsetOfVisualLineWithFoldingStart <= foldingStartOffset;
    return toVisualColumnSymbolsNumber(document.getCharsSequence(), startOffsetOfVisualLineWithFoldingStart, foldingStartOffset);
  }

  @NotNull
  public LogicalPosition adjustLogicalPosition(LogicalPosition defaultLogical, int offset) {
    if (myActive > 0 || !isSoftWrappingEnabled()) {
      return defaultLogical;
    }

    myActive++;
    try {
      return offsetToLogicalPosition(offset);
    } finally {
      myActive--;
    }
  }

  @SuppressWarnings({"AssignmentToForLoopParameter"})
  private LogicalPosition offsetToLogicalPosition(int offset) {
    Document document = myEditor.getDocument();
    CharSequence chars = document.getCharsSequence();

    int targetLine = document.getLineNumber(offset);
    int targetLineStartOffset = document.getLineStartOffset(targetLine);

    int softWrapIntroducedLinesBefore = 0;
    int softWrapsOnCurrentLogicalLine = 0;
    int symbolsOnCurrentLogicalLine = 0;
    int symbolsOnCurrentVisibleLine = 0;
    List<TextChange> softWraps = myStorage.getSoftWraps();

    // Retrieve information about logical position that is soft-wraps unaware.
    LogicalPosition rawLineStartLogicalPosition = myEditor.offsetToLogicalPosition(targetLineStartOffset);

    // Calculate number of soft wrap-introduced lines before the line that holds target offset.
    int index = myStorage.getSoftWrapIndex(targetLineStartOffset);
    if (index < 0) {
      index = -index - 1;
    }
    int max = Math.min(index, softWraps.size());
    for (int j = 0; j < max; j++) {
      softWrapIntroducedLinesBefore += StringUtil.countNewLines(softWraps.get(j).getText());
    }

    FoldingModel foldingModel = myEditor.getFoldingModel();

    // Return eagerly if there are no soft wraps before the target offset on a line that contains it.
    if (max >= softWraps.size() || softWraps.get(max).getStart() > offset) {
      int column = toVisualColumnSymbolsNumber(chars, targetLineStartOffset, offset);
      LogicalPosition foldingUnawarePosition = new LogicalPosition(
        rawLineStartLogicalPosition.line, column, softWrapIntroducedLinesBefore, 0, 0, 0, 0
      );
      return adjustFoldingData(foldingModel, foldingUnawarePosition);
    }

    // Calculate number of lines and columns introduced by soft wrap located at the line that holds target offset if any.


    // We add '1' here in order to correctly process situation when there is soft wrap at target offset (it impacts resulting logical
    // position but document symbol at that offset should not be count).
    max = Math.min(chars.length(), offset + 1);

    for (int i = targetLineStartOffset; i < max; i++) {
      FoldRegion region = foldingModel.getCollapsedRegionAtOffset(i);
      if (region != null) {
        // Assuming that folded region placeholder doesn't contain line feed symbols.
        i = region.getEndOffset();
        symbolsOnCurrentVisibleLine += region.getPlaceholderText().length();
        continue;
      }

      TextChange softWrap = getSoftWrap(i);
      if (softWrap != null) {
        CharSequence softWrapText = softWrap.getText();
        for (int j = 0; j < softWrapText.length(); j++) {
          if (softWrapText.charAt(j) == '\n') {
            softWrapsOnCurrentLogicalLine++;
            symbolsOnCurrentVisibleLine = 0;
          }
          else {
            symbolsOnCurrentVisibleLine++;
          }
        }
        symbolsOnCurrentVisibleLine++; // For 'after soft wrap' sign.
      }

      // We don't want to count symbol at target offset.
      if (i == offset) {
        break;
      }

      // Assuming that no line feed is contained before target offset on a line that holds it.
      symbolsOnCurrentLogicalLine++;
      symbolsOnCurrentVisibleLine++;
    }

    LogicalPosition foldingUnawarePosition = new LogicalPosition(
      rawLineStartLogicalPosition.line, symbolsOnCurrentLogicalLine, softWrapIntroducedLinesBefore, softWrapsOnCurrentLogicalLine,
      symbolsOnCurrentVisibleLine - symbolsOnCurrentLogicalLine, 0, 0
    );
    return adjustFoldingData(foldingModel, foldingUnawarePosition);
  }

  @NotNull
  public VisualPosition adjustVisualPosition(@NotNull LogicalPosition logical, @NotNull VisualPosition defaultVisual) {
    if (myActive > 0 || !isSoftWrappingEnabled() || myStorage.isEmpty()) {
      return defaultVisual;
    }

    if (logical.visualPositionAware) {
      // We don't need to recalculate logical position adjustments because given object already has them.
      return logical.toVisualPosition();
    }

    myActive++;
    try {
      return doAdjustVisualPosition(logical, defaultVisual);
    }
    finally {
      myActive--;
    }
  }

  @NotNull
  private VisualPosition doAdjustVisualPosition(LogicalPosition logical, VisualPosition visual) {
    List<TextChange> softWraps = myStorage.getSoftWraps();

    // Check if there are registered soft wraps before the target logical position.
    int maxOffset = myEditor.logicalPositionToOffset(logical);
    int endIndex = myStorage.getSoftWrapIndex(maxOffset);
    if (endIndex < 0) {
      endIndex = -endIndex - 2; // We subtract '2' instead of '1' here in order to point to offset of the first soft wrap the
                                // is located before the given logical position.
    } 

    // Return eagerly if no soft wraps are registered before the target offset.
    if (endIndex < 0 || endIndex >= softWraps.size()) {
      return visual;
    }

    int lineDiff = 0;
    int column = -1;

    FoldingModel foldingModel = myEditor.getFoldingModel();
    int targetLogicalLineStartOffset = myEditor.logicalPositionToOffset(new LogicalPosition(logical.line, 0));
    for (int i = endIndex; i >= 0; i--) {
      TextChange softWrap = softWraps.get(i);
      if (softWrap == null) {
        assert false;
        continue;
      }

      if (foldingModel.isOffsetCollapsed(softWrap.getStart())) {
        continue;
      }

      CharSequence softWrapText = softWrap.getText();
      int softWrapLines = StringUtil.countNewLines(softWrapText);

      // Count lines introduced by the current soft wrap. We assume that the soft wrap is located before target offset,
      // hence, we're free to count all of its line feeds.
      lineDiff += softWrapLines;

      // Count soft wrap column offset only if it's located at the same line as the target offset.
      if (softWrapLines > 0 && softWrap.getStart() >= targetLogicalLineStartOffset) {
        for (int j = softWrapText.length() - 1; j >= 0; j--) {
          if (softWrapText.charAt(j) == '\n') {
            column = maxOffset - softWrap.getStart() - j + 1;
            break;
          }
        }
      }
    }

    int columnToUse = column >= 0 ? column : visual.column;
    return new VisualPosition(visual.line + lineDiff, columnToUse);
  }

  @Override
  public boolean isInsideSoftWrap(@NotNull VisualPosition visual) {
    if (!isSoftWrappingEnabled()) {
      return false;
    }
    LogicalPosition logical = myEditor.visualToLogicalPosition(visual);
    int offset = myEditor.logicalPositionToOffset(logical);
    if (offset <= 0) {
      // Never expect to be here, just a defensive programming.
      return false;
    }

    TextChange softWrap = getSoftWrap(offset);
    if (softWrap == null) {
      return false;
    }

    // We consider visual positions that point after the last symbol before soft wrap and the first symbol after soft wrap to not
    // belong to soft wrap-introduced virtual space.
    VisualPosition visualAfterSoftWrap = myEditor.offsetToVisualPosition(offset);
    if (visualAfterSoftWrap.equals(visual)) {
      return false;
    }

    VisualPosition visualBeforeSoftWrap = myEditor.offsetToVisualPosition(offset - 1);
    return visual.line > visualBeforeSoftWrap.line || visual.column > visualBeforeSoftWrap.column + 1;
  }

  @Override
  public int getSoftWrapIndentWidthInPixels(@NotNull TextChange softWrap) {
    if (!isSoftWrappingEnabled()) {
      return 0;
    }
    char[] chars = softWrap.getChars();
    int result = myPainter.getMinDrawingWidth(SoftWrapDrawingType.AFTER_SOFT_WRAP);

    int start = 0;
    int end = chars.length;

    int i = CharArrayUtil.lastIndexOf(chars, '\n', 0, chars.length);
    if (i >= 0) {
      start = i + 1;
    }

    if (start < end) {
      result += EditorUtil.textWidth(myEditor, chars, start, end, Font.PLAIN);
    }

    return result;
  }

  @Override
  public int getSoftWrapIndentWidthInColumns(@NotNull TextChange softWrap) {
    if (!isSoftWrappingEnabled()) {
      return 0;
    }
    char[] chars = softWrap.getChars();
    int result = 1; // For 'after soft wrap' drawing

    int start = 0;
    int i = CharArrayUtil.lastIndexOf(chars, '\n', 0, chars.length);
    if (i >= 0) {
      start = i + 1;
    }
    result += chars.length - start;

    return result;
  }

  public void beforeDocumentChange(@NotNull VisualPosition visualPosition) {
    List<TextChange> softWraps = myStorage.getSoftWraps();
    LogicalPosition logicalPosition = myEditor.visualToLogicalPosition(visualPosition);
    int offset = myEditor.logicalPositionToOffset(logicalPosition);
    int i = myStorage.getSoftWrapIndex(offset);
    if (i < 0 || i >= softWraps.size()) {
      return;
    }

    TextChange softWrap = softWraps.get(i);

    VisualPosition visualCaretPosition = myEditor.getCaretModel().getVisualPosition();

    // Consider given visual position to belong to soft wrap-introduced virtual space if visual position for the target offset
    // differs from the given.
    if (!visualPosition.equals(myEditor.offsetToVisualPosition(offset))) {
      myEditor.getDocument().replaceString(softWrap.getStart(), softWrap.getEnd(), softWrap.getText());
    }

    // Restore caret position.
    myEditor.getCaretModel().moveToVisualPosition(visualCaretPosition);
    myStorage.removeByIndex(i);
  }

  /**
   * //TODO den add doc
   *
   * @param change    change introduced to the document
   */
  private void updateRegisteredSoftWraps(TextChange change) {
    List<TextChange> softWraps = myStorage.getSoftWraps();
    int softWrapIndex = myStorage.getSoftWrapIndex(change.getStart());
    if (softWrapIndex < 0) {
      softWrapIndex = -softWrapIndex - 1;
    }

    Document document = myEditor.getDocument();

    if (softWrapIndex >= softWraps.size() || document.getTextLength() <= 0 || change.getStart() >= document.getTextLength()) {
      return;
    }
    int firstChangedLine = document.getLineNumber(change.getStart());
    int lastChangedLine = Math.max(document.getLineNumber(change.getEnd()), firstChangedLine + StringUtil.countNewLines(change.getText()));
    for (int i = firstChangedLine; i <= lastChangedLine; i++) {
      myDirtyLines.add(i);
    }

    // Collect soft wraps which offsets should be modified.
    List<TextChange> toModify = new ArrayList<TextChange>(softWraps.subList(softWrapIndex, softWraps.size()));

    // Remove soft wraps that should be modified.
    myStorage.removeInRange(softWrapIndex, softWraps.size());

    // Add modified soft wraps.
    for (TextChange softWrap : toModify) {
      myStorage.storeSoftWrap(softWrap.advance(change.getDiff()));
    }
  }

  private void initIfNecessary() {
    if (myInitialized) {
      return;
    }
    myInitialized = true;

    // Subscribe for document change events.
    myEditor.getDocument().addDocumentListener(new DocumentListener() {
      public void beforeDocumentChange(DocumentEvent event) {
      }

      public void documentChanged(DocumentEvent event) {
        myActive++;
        try {
          updateRegisteredSoftWraps(new TextChange(event.getNewFragment(), event.getOffset(), event.getOffset() + event.getOldLength()));
        }
        finally {
          myActive--;
        }
      }
    });
  }  

  private int toVisualColumnSymbolsNumber(char c) {
    myCharBuffer.clear();
    myCharBuffer.put(c);
    myCharBuffer.flip();
    return toVisualColumnSymbolsNumber(myCharBuffer, 0, 1);
  }

  private int toVisualColumnSymbolsNumber(CharSequence text) {
    return toVisualColumnSymbolsNumber(text, 0, text.length());
  }

  private int toVisualColumnSymbolsNumber(CharSequence text, int start, int end) {
    return EditorUtil.calcColumnNumber(myEditor, text, start, end);
  }

  private class Context {

    public final FoldingModel    foldingModel;
    public final LogicalPosition softWrapUnawareLogicalPosition;
    public final VisualPosition  targetVisualPosition;
    public final int             softWrapLinesBefore;
    public final int             visualLineBeforeSoftWrapAppliance;
    public final int             softWrapLinesOnCurrentLineBeforeTargetSoftWrap;

    public int targetSoftWrapLines;
    public int symbolsOnCurrentLogicalLine;
    public int symbolsOnCurrentVisualLine;

    Context(LogicalPosition softWrapUnawareLogicalPosition, VisualPosition targetVisualPosition, int softWrapLinesBefore,
            int softWrapLinesOnCurrentLineBeforeTargetSoftWrap, int visualLineBeforeSoftWrapAppliance, FoldingModel foldingModel)
    {
      this.softWrapUnawareLogicalPosition = softWrapUnawareLogicalPosition;
      this.targetVisualPosition = targetVisualPosition;
      this.softWrapLinesBefore = softWrapLinesBefore;
      this.softWrapLinesOnCurrentLineBeforeTargetSoftWrap = softWrapLinesOnCurrentLineBeforeTargetSoftWrap;
      this.visualLineBeforeSoftWrapAppliance = visualLineBeforeSoftWrapAppliance;
      this.foldingModel = foldingModel;
    }

    /**
     * Updates current context within the soft wrap symbol.
     *
     * @param c   soft wrap symbol to process
     * @return    logical position that matches target visual position if given symbol processing makes it possible to calculate it;
     *            <code>null</code> otherwise
     */
    @Nullable
    public LogicalPosition onSoftWrapSymbol(char c) {
      // Process line feed inside soft wrap.
      if (c == '\n') {
        if (targetVisualPosition.line == visualLineBeforeSoftWrapAppliance + targetSoftWrapLines) {
          return build(targetVisualPosition.column - symbolsOnCurrentLogicalLine);
        }
        else {
          targetSoftWrapLines++;
          symbolsOnCurrentVisualLine = 0;
          return null;
        }
      }

      // Just update information about tracked symbols number if current visual line is too low.
      if (targetVisualPosition.line > visualLineBeforeSoftWrapAppliance + targetSoftWrapLines) {
        symbolsOnCurrentVisualLine += toVisualColumnSymbolsNumber(c);
        return null;
      }

      // There is a possible case that, for example, target visual column is zero and it points to the soft-wrapped line,
      // i.e. soft wrap are. We shouldn't count symbols then. Hence, we perform this preliminary examination with eager
      // return if necessary.
      if (targetVisualPosition.column <= symbolsOnCurrentVisualLine) {
        return build();
      }

      // Process non-line feed inside soft wrap.
      symbolsOnCurrentVisualLine += toVisualColumnSymbolsNumber(c);
      if (targetVisualPosition.column <= symbolsOnCurrentVisualLine) {
        return build();
      }
      else {
        return null;
      }
    }

    /**
     * Updates current context within the non-soft wrap symbol.
     *
     * @param c   soft wrap symbol to process
     * @return    logical position that matches target visual position if given symbol processing makes it possible to calculate it;
     *            <code>null</code> otherwise
     */
    @Nullable
    public LogicalPosition onNonSoftWrapSymbol(char c) {
      // Don't expect line feed symbol to be delivered to this method in assumption that we process only one logical line here.
      if (c == '\n') {
        assert false;
        return null;
      }

      // Just update information about tracked symbols number if current visual line is too low.
      if (targetVisualPosition.line > visualLineBeforeSoftWrapAppliance + targetSoftWrapLines) {
        symbolsOnCurrentVisualLine += toVisualColumnSymbolsNumber(c);
        symbolsOnCurrentLogicalLine++;
        return null;
      }

      // There is a possible case that, for example, target visual column is zero. We shouldn't count symbols then.
      // Hence, we perform this preliminary examination with eager return if necessary.
      if (targetVisualPosition.column <= symbolsOnCurrentVisualLine) {
        return build();
      }

      symbolsOnCurrentVisualLine += toVisualColumnSymbolsNumber(c);
      symbolsOnCurrentLogicalLine++;

      
      if (targetVisualPosition.column <= symbolsOnCurrentVisualLine) {
        return build();
      }
      else {
        return null;
      }
    }

    private LogicalPosition build() {
      return build(symbolsOnCurrentVisualLine - symbolsOnCurrentLogicalLine);
    }

    private LogicalPosition build(int softWrapColumnDiff) {
      int logicalLine = softWrapUnawareLogicalPosition.line - softWrapLinesBefore
                        - softWrapLinesOnCurrentLineBeforeTargetSoftWrap - targetSoftWrapLines;
      LogicalPosition foldingUnawareResult = new LogicalPosition(
        logicalLine,
        symbolsOnCurrentLogicalLine,
        softWrapLinesBefore,
        softWrapLinesOnCurrentLineBeforeTargetSoftWrap  + targetSoftWrapLines,
        softWrapColumnDiff,
        0,
        0
      );
      return adjustFoldingData(foldingModel, foldingUnawareResult);
    }
  }
}
