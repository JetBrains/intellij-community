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
import com.intellij.util.text.CharArrayUtil;
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

  private static final VisualPosition  DUMMY_VISUAL  = new VisualPosition(Integer.MAX_VALUE, Integer.MAX_VALUE);

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
    try {
      return doAdjustLogicalPosition(defaultLogical, visual);
    }
    finally {
      myFontTypeProvider.cleanup();
    }
  }

  @SuppressWarnings({"AssignmentToForLoopParameter"})
  @NotNull
  private LogicalPosition doAdjustLogicalPosition(@NotNull LogicalPosition defaultLogical, @NotNull VisualPosition visual) {
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
      if (!isVisible(softWrap)) {
        continue;
      }

      int currentSoftWrapLineFeeds = StringUtil.countNewLines(softWrap.getText());
      int softWrapLine = document.getLineNumber(softWrap.getStart());
      int visualLineBeforeSoftWrapAppliance = myEditor.logicalToVisualPosition(new LogicalPosition(softWrapLine, 0)).line
                                              + softWrapLinesBeforeCurrentLogicalLine + softWrapLinesOnCurrentLogicalLine;
      if (visualLineBeforeSoftWrapAppliance > visual.line) {
        softWrapLinesBeforeCurrentLogicalLine += softWrapLinesOnCurrentLogicalLine;
        int logicalLine = defaultLogical.line - softWrapLinesBeforeCurrentLogicalLine;
        return new LogicalPosition(
          logicalLine, defaultLogical.column, softWrapLinesBeforeCurrentLogicalLine, 0, 0,
          getFoldedLinesBefore(document.getLineStartOffset(logicalLine)),
          visual.column - defaultLogical.column
        );
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

      int startLineOffset = document.getLineStartOffset(softWrapLine);
      int endLineOffset = document.getLineEndOffset(softWrapLine);
      FoldRegion region = foldingModel.getCollapsedRegionAtOffset(endLineOffset);
      while (region != null) {
        int line = document.getLineNumber(region.getEndOffset());
        endLineOffset = document.getLineEndOffset(line);
        region = foldingModel.getCollapsedRegionAtOffset(endLineOffset);
      }
      CharSequence documentText = document.getCharsSequence();

      // If we're here that means that current soft wrap affects logical line that is matched to the given visual line.
      // We iterate from the logical line start then in order to calculate resulting logical position.
      Context context = new Context(
        visual, softWrapLine, softWrapLinesBeforeCurrentLogicalLine, softWrapLinesOnCurrentLogicalLine,
        visualLineBeforeSoftWrapAppliance, getFoldedLinesBefore(startLineOffset)
      );
      myFontTypeProvider.init(startLineOffset);

      for (int j = startLineOffset; j < endLineOffset; j++) {

        // Process soft wrap at the current offset if any.
        TextChange softWrapToProcess = myStorage.getSoftWrap(j);
        if (softWrapToProcess != null && isVisible(softWrapToProcess)) {
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

        context.fontType = myFontTypeProvider.getFontType(j);

        FoldRegion foldRegion = foldingModel.getCollapsedRegionAtOffset(j);
        if (foldRegion != null) {
          LogicalPosition result = context.onCollapsedFolding(foldRegion);
          if (result != null) {
            return result;
          }
          j = foldRegion.getEndOffset();
        }

        // Process document symbol.
        LogicalPosition result = context.onNonSoftWrapSymbol(documentText.charAt(j));
        if (result != null) {
          return result;
        }
      }

      // If we are here that means that target visual position is located at virtual space after the line end.
      context.logicalColumn += visual.column - context.visualColumn;
      return context.build();     
    }

    // If we are here that means that there is no soft wrap on a logical line that corresponds to the target visual line.
    softWrapLinesBeforeCurrentLogicalLine += softWrapLinesOnCurrentLogicalLine;
    int logicalLine = defaultLogical.line - softWrapLinesBeforeCurrentLogicalLine;
    int foldedLines = getFoldedLinesBefore(document.getLineStartOffset(logicalLine));
    int foldingColumnDiff = visual.column - defaultLogical.column;
    return new LogicalPosition(
      logicalLine, defaultLogical.column, softWrapLinesBeforeCurrentLogicalLine, 0, 0, foldedLines, foldingColumnDiff
    );
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

    int targetLogicalLineStartOffset = myEditor.logicalPositionToOffset(new LogicalPosition(logical.line, 0));
    for (int i = endIndex; i >= 0; i--) {
      TextChange softWrap = softWraps.get(i);
      if (softWrap == null) {
        assert false;
        continue;
      }

      if (!isVisible(softWrap)) {
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

  public LogicalPosition offsetToLogicalPosition(int offset) {
    try {
      return doOffsetToLogicalPosition(offset);
    }
    finally {
      myFontTypeProvider.cleanup();
    }
  }

  @SuppressWarnings({"AssignmentToForLoopParameter"})
  private LogicalPosition doOffsetToLogicalPosition(int offset) {
    FoldingModel foldingModel = myEditor.getFoldingModel();
    Document document = myEditor.getDocument();
    CharSequence text = document.getCharsSequence();
    int line = document.getLineNumber(offset);
    int lineStartOffset = document.getLineStartOffset(line);
    FoldRegion region = foldingModel.getCollapsedRegionAtOffset(lineStartOffset);
    while (region != null && region.getStartOffset() != lineStartOffset) {
      line = document.getLineNumber(region.getStartOffset());
      lineStartOffset = document.getLineStartOffset(line);
      region = foldingModel.getCollapsedRegionAtOffset(lineStartOffset);
    }

    Context context = new Context(line, getSoftWrapIntroducedLinesBefore(lineStartOffset), getFoldedLinesBefore(lineStartOffset));
    myFontTypeProvider.init(lineStartOffset);
    context.fontType = myFontTypeProvider.getFontType(lineStartOffset);
    for (int i = lineStartOffset; i <= offset; i++) {
      TextChangeImpl softWrap = myStorage.getSoftWrap(i);
      if (softWrap != null) {
        context.beforeSoftWrap();
        CharSequence softWrapText = softWrap.getText();
        for (int k = 0; k < softWrapText.length(); k++) {
          context.onSoftWrapSymbol(softWrapText.charAt(k));
        }
        context.afterSoftWrap();
      }

      if (i == offset) {
        // We want to count soft wrap that is registered at target offset if any but not exceeding document symbols.
        break;
      }

      region = foldingModel.getCollapsedRegionAtOffset(i);
      if (region != null) {
        processFoldRegion(context, region, offset);
        if (offset <= region.getEndOffset()) {
          break;
        }
        i = region.getEndOffset();
      }
      context.fontType = myFontTypeProvider.getFontType(i);
      context.onNonSoftWrapSymbol(text.charAt(i));
    }
    return new LogicalPosition(
      context.logicalLine,
      context.logicalColumn,
      context.softWrapLinesBefore,
      context.targetSoftWrapLines,
      context.softWrapColumnDiff,
      getFoldedLinesBefore(offset),
      context.foldingColumnDiff
    );
  }

  /**
   * Processes given collapsed fold region assuming that we need to stop at a target offset.
   * <p/>
   * Processing result is updated state of the given context object.
   *
   * @param context   processing data holder
   * @param region    collapsed fold region to process
   * @param offset    target stop offset
   */
  @SuppressWarnings({"AssignmentToForLoopParameter"})
  private void processFoldRegion(Context context, FoldRegion region, int offset) {
    CharSequence text = myEditor.getDocument().getCharsSequence();
    int max = Math.min(offset, region.getEndOffset());
    boolean multilineFolding = false;
    for (int i = region.getStartOffset(); i < max;) {
      int lineFeedOffset = CharArrayUtil.shiftForwardUntil(text, i, "\n");
      if (lineFeedOffset < max) {
        context.softWrapLinesBefore += context.targetSoftWrapLines;
        context.targetSoftWrapLines = 0;
        context.softWrapColumnDiff = 0;
        context.foldedLines++;
        context.logicalColumn = 0;
        context.foldingColumnDiff = context.visualColumn;
        context.logicalLine++;
        i = lineFeedOffset + 1;
        multilineFolding = true;
      }
      else {
        if (multilineFolding) {
          context.logicalColumn = myTextRepresentationHelper.toVisualColumnSymbolsNumber(text, i, max, 0);
          context.foldingColumnDiff = context.visualColumn - context.logicalColumn;
          break;
        }
        else {
          int foldedColumns = myTextRepresentationHelper.toVisualColumnSymbolsNumber(text, region.getStartOffset(), max, context.x);
          context.logicalColumn += foldedColumns;
          context.foldingColumnDiff -= foldedColumns;
          if (offset >= region.getEndOffset()) {
            context.foldingColumnDiff += region.getPlaceholderText().length();
          }
          return;
        }
      }
    }

    if (offset >= region.getEndOffset()) {
      int foldPlaceholderColumns = region.getPlaceholderText().length();
      context.visualColumn += foldPlaceholderColumns;
      context.foldingColumnDiff += foldPlaceholderColumns;
      context.x += foldPlaceholderColumns * myTextRepresentationHelper.charWidth(' ', context.x, Font.PLAIN);
    }
  }

  /**
   * Allows to answer how many soft wrap-introduced visual lines are located before the given offset.
   *
   * @param offset    target offset
   * @return          number of soft wrap-introduced visual lines are located before the given offset
   */
  private int getSoftWrapIntroducedLinesBefore(int offset) {
    int result = 0;
    List<? extends TextChange> softWraps = myStorage.getSoftWraps();

    // Calculate number of soft wrap-introduced lines before the line that holds target offset.
    int index = myStorage.getSoftWrapIndex(offset);
    if (index < 0) {
      index = -index - 1;
    }
    int max = Math.min(index, softWraps.size());
    for (int j = 0; j < max; j++) {
      TextChange softWrap = softWraps.get(j);
      if (isVisible(softWrap)) {
        result += StringUtil.countNewLines(softWrap.getText());
      }
    }
    return result;
  }

  /**
   * Allows to answer how many folded lines are located before the logical line that contains given offset.
   *
   * @param offset    target offset
   * @return          number of folded lines are located before the logical line that contains given offset.
   */
  private int getFoldedLinesBefore(int offset) {
    Document document = myEditor.getDocument();
    int line = document.getLineNumber(offset);
    int lineStartOffset = document.getLineStartOffset(line);
    int result = 0;
    for (FoldRegion foldRegion : myEditor.getFoldingModel().getAllFoldRegions()) {
      if (foldRegion.getStartOffset() >= lineStartOffset) {
        break;
      }

      if (foldRegion.isExpanded() || !foldRegion.isValid()) {
        continue;
      }

      int foldingStartLine = document.getLineNumber(foldRegion.getStartOffset());
      int foldingEndLine = document.getLineNumber(foldRegion.getEndOffset());
      result += Math.min(line, foldingEndLine) - foldingStartLine;
    }
    return result;
  }

  private boolean isVisible(TextChange softWrap) {
    FoldingModel foldingModel = myEditor.getFoldingModel();
    int start = softWrap.getStart();

    // There is a possible case that folding region starts just after soft wrap, i.e. soft wrap and folding region share the
    // same offset. However, soft wrap is shown, hence, we also check offset just before the target one.
    return !foldingModel.isOffsetCollapsed(start) || !foldingModel.isOffsetCollapsed(start - 1);
  }

  private int toVisualColumnSymbolsNumber(char c, int x) {
    myCharBuffer.clear();
    myCharBuffer.put(c);
    myCharBuffer.flip();
    return myTextRepresentationHelper.toVisualColumnSymbolsNumber(myCharBuffer, 0, 1, x);
  }

  private class Context {

    public final VisualPosition  targetVisualPosition;
    public final int             visualLineBeforeSoftWrapAppliance;
    public final int             softWrapLinesOnCurrentLineBeforeTargetSoftWrap;

    public int logicalLine;
    public int visualLine;
    public int softWrapLinesBefore;
    public int targetSoftWrapLines;
    public int softWrapColumnDiff;
    public int logicalColumn;
    public int visualColumn;
    public int foldedLines;
    public int foldingColumnDiff;
    public int x;
    public int fontType;

    Context(int logicalLine, int softWrapLinesBefore, int foldedLines) {
      this(DUMMY_VISUAL, logicalLine, softWrapLinesBefore, 0, 0, foldedLines);
    }

    Context(VisualPosition targetVisualPosition, int logicalLine, int softWrapLinesBefore,
            int softWrapLinesOnCurrentLineBeforeTargetSoftWrap, int visualLineBeforeSoftWrapAppliance, int foldedLines)
    {
      this.targetVisualPosition = targetVisualPosition;
      this.softWrapLinesBefore = softWrapLinesBefore;
      this.softWrapLinesOnCurrentLineBeforeTargetSoftWrap = softWrapLinesOnCurrentLineBeforeTargetSoftWrap;
      this.visualLineBeforeSoftWrapAppliance = visualLineBeforeSoftWrapAppliance;
      this.foldedLines = foldedLines;
      this.logicalLine = logicalLine;
      visualLine = visualLineBeforeSoftWrapAppliance + targetSoftWrapLines;
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
          softWrapColumnDiff = targetVisualPosition.column - logicalColumn;
          return build();
        }
        else {
          x = 0;
          softWrapColumnDiff = -logicalColumn;
          targetSoftWrapLines++;
          visualLine++;
          visualColumn = 0;
          return null;
        }
      }

      softWrapColumnDiff++;

      // Just update information about tracked symbols number if current visual line is too low.
      if (targetVisualPosition.line > visualLineBeforeSoftWrapAppliance + targetSoftWrapLines) {
        visualColumn += toVisualColumnSymbolsNumber(c, x);
        x += myTextRepresentationHelper.charWidth(c, x, fontType);
        return null;
      }

      // There is a possible case that, for example, target visual column is zero and it points to the soft-wrapped line,
      // i.e. soft wrap are. We shouldn't count symbols then. Hence, we perform this preliminary examination with eager
      // return if necessary.
      if (targetVisualPosition.column <= visualColumn) {
        return build();
      }

      // Process non-line feed inside soft wrap.
      visualColumn++; // Don't expect tabulation to be used inside soft wrap text.
      x += myTextRepresentationHelper.charWidth(c, x, fontType);

      if (targetVisualPosition.column <= visualColumn) {
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
      visualColumn++;
      softWrapColumnDiff++;
    }

    @SuppressWarnings({"AssignmentToForLoopParameter"})
    @Nullable
    public LogicalPosition onCollapsedFolding(FoldRegion region) {
      int visualFoldingPlaceholderWidth = region.getPlaceholderText().length(); // Assuming that no tabs are used as placeholder

      // Process situation when target visual position points to collapsed folding placeholder.
      if (visualLine == targetVisualPosition.line && visualColumn + visualFoldingPlaceholderWidth > targetVisualPosition.column) {
        return build();
      }

      // If control flow reaches this point that means that we should process whole folded region and update current object state.
      CharSequence text = myEditor.getDocument().getCharsSequence();
      boolean multiline = false;
      for (int i = region.getStartOffset(); i < region.getEndOffset();) {
        int lineFeedOffset = CharArrayUtil.shiftForwardUntil(text, i, "\n");
        // Process multiline folded text.
        if (lineFeedOffset < region.getEndOffset()) {
          logicalLine++;
          foldedLines++;
          logicalColumn = 0;
          softWrapLinesBefore += targetSoftWrapLines;
          targetSoftWrapLines = 0;
          softWrapColumnDiff = 0;
          i = lineFeedOffset + 1;
          multiline = true;
        }
        else {
          if (multiline) {
            logicalColumn = myTextRepresentationHelper.toVisualColumnSymbolsNumber(text, i, region.getEndOffset(), 0);
          }
          else {
            logicalColumn += myTextRepresentationHelper.toVisualColumnSymbolsNumber(text, i, region.getEndOffset(), x);
          }
          foldingColumnDiff = visualColumn + visualFoldingPlaceholderWidth - logicalColumn - softWrapColumnDiff;
          i = region.getEndOffset();
        }
      }

      visualColumn += visualFoldingPlaceholderWidth;
      x += visualFoldingPlaceholderWidth * myTextRepresentationHelper.charWidth(' ', x, Font.PLAIN);
      if (visualLine == targetVisualPosition.line && visualColumn == targetVisualPosition.column) {
        return build();
      }
      return null;
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
      if (targetVisualPosition.line > visualLine) {
        int columnsForSymbol = toVisualColumnSymbolsNumber(c, x);
        visualColumn += columnsForSymbol;
        logicalColumn += columnsForSymbol;
        x += myTextRepresentationHelper.charWidth(c, x, fontType);
        return null;
      }

      // There is a possible case that, for example, target visual column is zero. We shouldn't count symbols then.
      // Hence, we perform this preliminary examination with eager return if necessary.
      if (targetVisualPosition.column <= visualColumn) {
        return build();
      }

      int columnsForSymbol = toVisualColumnSymbolsNumber(c, x);
      int diffInColumns = targetVisualPosition.column - visualColumn;
      int incrementToUse = columnsForSymbol;
      if (columnsForSymbol >= diffInColumns) {
        incrementToUse = Math.min(columnsForSymbol, diffInColumns);
      }
      visualColumn += incrementToUse;
      logicalColumn += incrementToUse;
      x += myTextRepresentationHelper.charWidth(c, x, fontType);

      if (targetVisualPosition.column <= visualColumn) {
        return build();
      }
      else {
        return null;
      }
    }

    private LogicalPosition build() {
      return build(foldingColumnDiff);
    }

    private LogicalPosition build(int foldingColumnDiff) {
      return new LogicalPosition(
        logicalLine,
        logicalColumn,
        softWrapLinesBefore,
        softWrapLinesOnCurrentLineBeforeTargetSoftWrap  + targetSoftWrapLines,
        softWrapColumnDiff,
        foldedLines,
        foldingColumnDiff
      );
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
