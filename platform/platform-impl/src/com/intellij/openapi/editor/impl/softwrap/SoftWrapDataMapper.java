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
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorTextRepresentationHelper;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.nio.CharBuffer;
import java.util.List;

/**
 * Encapsulates logic of various mappings (e.g. {@code 'offset -> logical position'}) and adjustments (e.g. adjust soft wrap unaware
 * logical position for the target visual position).
 *
 * @author Denis Zhdanov
 * @since Jul 7, 2010 2:31:04 PM
 */
public class SoftWrapDataMapper {

  private final CharBuffer myCharBuffer = CharBuffer.allocate(1);

  private final EditorTextRepresentationHelper myTextRepresentationHelper;
  private final EditorEx                       myEditor;
  private final SoftWrapsStorage               myStorage;
  private final SoftWrapPainter                myPainter;
  private final FontTypeProvider               myFontTypeProvider;

  public SoftWrapDataMapper(EditorEx editor,
                            SoftWrapsStorage storage,
                            SoftWrapPainter painter,
                            EditorTextRepresentationHelper textRepresentationHelper)
  {
    this(editor, storage, painter, textRepresentationHelper, new IterationStateFontTypeProvider(editor));
  }

  SoftWrapDataMapper(EditorEx editor,
                            SoftWrapsStorage storage,
                            SoftWrapPainter painter,
                            EditorTextRepresentationHelper textRepresentationHelper,
                            FontTypeProvider fontTypeProvider)
  {
    myEditor = editor;
    myStorage = storage;
    myPainter = painter;
    myTextRepresentationHelper = textRepresentationHelper;
    myFontTypeProvider = fontTypeProvider;
  }

  @NotNull
  public LogicalPosition adjustLogicalPosition(@NotNull LogicalPosition defaultLogical, @NotNull VisualPosition visual) {
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
    List<TextChangeImpl> softWraps = myStorage.getSoftWraps();
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

      myFontTypeProvider.init(startLineOffset);
      context.fontType = myFontTypeProvider.getFontType(startLineOffset);

      for (int j = startLineOffset; j < endLineOffset; j++) {

        // Process soft wrap at the current offset if any.
        TextChange softWrapToProcess = myStorage.getSoftWrap(j);
        if (softWrapToProcess != null) {
          context.beforeSoftWrap();
          if (j >= softWrap.getStart()) {
            CharSequence softWrapText = softWrapToProcess.getText();
            for (int k = 0; k < softWrapText.length(); k++) {
              LogicalPosition result = context.onSoftWrapSymbol(softWrapText.charAt(k));
              if (result != null) {
                return result;
              }
            }
          }
          context.afterSoftWrap();
        }

        context.fontType = myFontTypeProvider.getFontType(startLineOffset);

        // Process document symbol.
        LogicalPosition result = context.onNonSoftWrapSymbol(documentText.charAt(j));
        if (result != null) {
          return result;
        }
      }
      myFontTypeProvider.cleanup();

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

  @NotNull
  public VisualPosition adjustVisualPosition(@NotNull LogicalPosition logical, @NotNull VisualPosition visual) {
    if (logical.visualPositionAware) {
      // We don't need to recalculate logical position adjustments because given object already has them.
      return logical.toVisualPosition();
    }

    List<TextChangeImpl> softWraps = myStorage.getSoftWraps();

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

  @SuppressWarnings({"AssignmentToForLoopParameter"})
  public LogicalPosition offsetToLogicalPosition(int offset) {
    Document document = myEditor.getDocument();
    CharSequence chars = document.getCharsSequence();

    int targetLine = document.getLineNumber(offset);
    int targetLineStartOffset = document.getLineStartOffset(targetLine);

    int softWrapIntroducedLinesBefore = 0;
    int softWrapsOnCurrentLogicalLine = 0;
    int symbolsOnCurrentLogicalLine = 0;
    int symbolsOnCurrentVisibleLine = 0;
    List<TextChangeImpl> softWraps = myStorage.getSoftWraps();

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
      int column = myTextRepresentationHelper.toVisualColumnSymbolsNumber(chars, targetLineStartOffset, offset, 0);
      LogicalPosition foldingUnawarePosition = new LogicalPosition(
        rawLineStartLogicalPosition.line, column, softWrapIntroducedLinesBefore, 0, 0, 0, 0
      );
      return adjustFoldingData(foldingModel, foldingUnawarePosition);
    }

    // Calculate number of lines and columns introduced by soft wrap located at the line that holds target offset if any.


    // We add '1' here in order to correctly process situation when there is soft wrap at target offset (it impacts resulting logical
    // position but document symbol at that offset should not be count).
    max = Math.min(chars.length(), offset + 1);
    int x = 0;
    myFontTypeProvider.init(targetLineStartOffset);
    int fontType = myFontTypeProvider.getFontType(targetLineStartOffset);

    for (int i = targetLineStartOffset; i < max; i++) {
      FoldRegion region = foldingModel.getCollapsedRegionAtOffset(i);
      if (region != null) {
        // Assuming that folded region placeholder doesn't contain line feed symbols.
        i = region.getEndOffset();
        symbolsOnCurrentVisibleLine += region.getPlaceholderText().length();
        continue;
      }

      TextChange softWrap = myStorage.getSoftWrap(i);
      if (softWrap != null) {
        CharSequence softWrapText = softWrap.getText();
        for (int j = 0; j < softWrapText.length(); j++) {
          if (softWrapText.charAt(j) == '\n') {
            softWrapsOnCurrentLogicalLine++;
            symbolsOnCurrentVisibleLine = 0;
            x = 0;
          }
          else {
            symbolsOnCurrentVisibleLine++;
            x += myTextRepresentationHelper.charWidth(softWrapText.charAt(j), x, fontType);
          }
        }
        symbolsOnCurrentVisibleLine++; // For 'after soft wrap' sign.
        x += myPainter.getMinDrawingWidth(SoftWrapDrawingType.AFTER_SOFT_WRAP);
      }

      // We don't want to count symbol at target offset.
      if (i == offset) {
        break;
      }

      fontType = myFontTypeProvider.getFontType(targetLineStartOffset);

      // Assuming that no line feed is contained before target offset on a line that holds it.
      int columnsForSymbol = toVisualColumnSymbolsNumber(chars.charAt(i), x);
      symbolsOnCurrentLogicalLine += columnsForSymbol;
      symbolsOnCurrentVisibleLine += columnsForSymbol;
      x += myTextRepresentationHelper.charWidth(chars.charAt(i), x, fontType);
    }
    myFontTypeProvider.cleanup();

    LogicalPosition foldingUnawarePosition = new LogicalPosition(
      rawLineStartLogicalPosition.line, symbolsOnCurrentLogicalLine, softWrapIntroducedLinesBefore, softWrapsOnCurrentLogicalLine,
      symbolsOnCurrentVisibleLine - symbolsOnCurrentLogicalLine, 0, 0
    );
    return adjustFoldingData(foldingModel, foldingUnawarePosition);
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
    int targetLine = document.getLineNumber(offset);
    int lastFoldEndLogicalLine = -1;
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
      if (offset >= foldRegion.getStartOffset()) {
        if (offset < foldRegion.getEndOffset()) {
          // Our purpose is to define folding data in order to point to the visual folding start.
          int visualFoldingStartColumn = calculateVisualFoldingStartColumn(foldRegion);
          int diff = visualFoldingStartColumn - position.column - softWrapColumnDiff;
          if (lastFoldEndLogicalLine == foldingStartLine) {
            foldColumnDiff += diff;
          }
          else {
            foldColumnDiff = diff;
          }
          return new LogicalPosition(
            position.line, position.column, position.softWrapLinesBeforeCurrentLogicalLine, position.softWrapLinesOnCurrentLogicalLine,
            softWrapColumnDiff, foldedLines, foldColumnDiff
          );
        }

        int diff = getFoldColumnDiff(foldRegion);
        if (lastFoldEndLogicalLine == foldingStartLine) {
          foldColumnDiff += diff;
        }
        else {
          foldColumnDiff = diff;
        }
        lastFoldEndLogicalLine = foldingEndLine;
      }
    }

    if (lastFoldEndLogicalLine != position.line) {
      foldColumnDiff = 0;
    }

    return new LogicalPosition(
      position.line, position.column, position.softWrapLinesBeforeCurrentLogicalLine, position.softWrapLinesOnCurrentLogicalLine,
      softWrapColumnDiff, foldedLines, foldColumnDiff
    );
  }

  private int getFoldColumnDiff(FoldRegion region) {
    int visualFoldingStartColumn = calculateVisualFoldingStartColumn(region);
    LogicalPosition foldEndLogical = myEditor.offsetToLogicalPosition(region.getEndOffset());
    // Assuming that there is no tabulations symbols at placeholder text.
    int foldingPlaceholderWidth = region.getPlaceholderText().length();
    return visualFoldingStartColumn + foldingPlaceholderWidth - foldEndLogical.column;
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

    List<TextChangeImpl> softWraps = myStorage.getSoftWraps();
    int startOffsetOfVisualLineWithFoldingStart = logicalLineStartOffset;
    int softWrapOffsetInColumns = 0;
    for (; softWrapIndex < softWraps.size(); softWrapIndex++) {
      TextChange softWrap = softWraps.get(softWrapIndex);
      if (softWrap.getStart() >= foldingStartOffset) {
        break;
      }

      startOffsetOfVisualLineWithFoldingStart = softWrap.getStart();
      softWrapOffsetInColumns = numberOfSymbolsOnLastVisualLine(softWrap);
    }

    assert startOffsetOfVisualLineWithFoldingStart <= foldingStartOffset;
    int x = softWrapOffsetInColumns * myTextRepresentationHelper.charWidth(' ', 0, Font.PLAIN);
    return myTextRepresentationHelper.toVisualColumnSymbolsNumber(
      document.getCharsSequence(), startOffsetOfVisualLineWithFoldingStart, foldingStartOffset, x
    );
  }

  private static int numberOfSymbolsOnLastVisualLine(TextChange textChange) {
    int result = 0;
    for (int i = textChange.getText().length() - 1; i >= 0; i--) {
      if (i == '\n') {
        return result;
      }
      else {
        result++;
      }
    }
    return result;
  }

  private int toVisualColumnSymbolsNumber(char c, int x) {
    myCharBuffer.clear();
    myCharBuffer.put(c);
    myCharBuffer.flip();
    return myTextRepresentationHelper.toVisualColumnSymbolsNumber(myCharBuffer, 0, 1, x);
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
    public int x;
    public int fontType;

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
          x = 0;
          targetSoftWrapLines++;
          symbolsOnCurrentVisualLine = 0;
          return null;
        }
      }

      // Just update information about tracked symbols number if current visual line is too low.
      if (targetVisualPosition.line > visualLineBeforeSoftWrapAppliance + targetSoftWrapLines) {
        symbolsOnCurrentVisualLine += toVisualColumnSymbolsNumber(c, x);
        x += myTextRepresentationHelper.charWidth(c, x, fontType);
        return null;
      }

      // There is a possible case that, for example, target visual column is zero and it points to the soft-wrapped line,
      // i.e. soft wrap are. We shouldn't count symbols then. Hence, we perform this preliminary examination with eager
      // return if necessary.
      if (targetVisualPosition.column <= symbolsOnCurrentVisualLine) {
        return build();
      }

      // Process non-line feed inside soft wrap.
      symbolsOnCurrentVisualLine++; // Don't expect tabulation to be used inside soft wrap text.
      x += myTextRepresentationHelper.charWidth(c, x, fontType);

      if (targetVisualPosition.column <= symbolsOnCurrentVisualLine) {
        return build();
      }
      else {
        return null;
      }
    }

    public void beforeSoftWrap() {
      x = 0;
    }

    public void afterSoftWrap() {
      x += myPainter.getMinDrawingWidth(SoftWrapDrawingType.AFTER_SOFT_WRAP);
      symbolsOnCurrentVisualLine++;
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
        x = 0;
        assert false;
        return null;
      }

      // Just update information about tracked symbols number if current visual line is too low.
      if (targetVisualPosition.line > visualLineBeforeSoftWrapAppliance + targetSoftWrapLines) {
        int columnsForSymbol = toVisualColumnSymbolsNumber(c, x);
        symbolsOnCurrentVisualLine += columnsForSymbol;
        symbolsOnCurrentLogicalLine += columnsForSymbol;
        x += myTextRepresentationHelper.charWidth(c, x, fontType);
        return null;
      }

      // There is a possible case that, for example, target visual column is zero. We shouldn't count symbols then.
      // Hence, we perform this preliminary examination with eager return if necessary.
      if (targetVisualPosition.column <= symbolsOnCurrentVisualLine) {
        return build();
      }

      int columnsForSymbol = toVisualColumnSymbolsNumber(c, x);
      int diffInColumns = targetVisualPosition.column - symbolsOnCurrentVisualLine;
      int incrementToUse = columnsForSymbol;
      if (columnsForSymbol >= diffInColumns) {
        incrementToUse = Math.min(columnsForSymbol, diffInColumns);
      }
      symbolsOnCurrentVisualLine += incrementToUse;
      symbolsOnCurrentLogicalLine += incrementToUse;
      x += myTextRepresentationHelper.charWidth(c, x, fontType);

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

  /**
   * Strategy interface for providing font type to use during working with editor text.
   * <p/>
   * It's primary purpose is to relief unit testing.
   */
  interface FontTypeProvider {
    void init(int start);
    int getFontType(int offset);
    void cleanup();
  }

  private static class IterationStateFontTypeProvider implements FontTypeProvider {

    private final EditorEx myEditor;

    private IterationState myState;
    private int            myFontType;

    private IterationStateFontTypeProvider(EditorEx editor) {
      myEditor = editor;
    }

    @Override
    public void init(int start) {
      myState = new IterationState(myEditor, start, false);
      myFontType = myState.getMergedAttributes().getFontType();
    }

    @Override
    public int getFontType(int offset) {
      if (offset >= myState.getEndOffset()) {
        myState.advance();
        myFontType = myState.getMergedAttributes().getFontType();
      }
      return myFontType;
    }

    @Override
    public void cleanup() {
      myState = null;
    }
  }
}
