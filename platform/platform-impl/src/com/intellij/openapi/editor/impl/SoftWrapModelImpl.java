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
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.SoftWrapModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
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
import java.util.Comparator;
import java.util.List;

/**
 * Default {@link SoftWrapModelEx} implementation.
 * <p/>
 * Design principles:
 * <ul>
 *    <li>
 *      remembers width of the visible area used last time; drops all of registered soft wraps on offset/position
 *      recalculation/adjustment request (e.g. {@link #adjustLogicalPosition(LogicalPosition, VisualPosition)}) if current
 *      visible area width differs from the one used last time;
 *    </li>
 *    <li>
 *      performs {@code 'soft wrap' -> 'hard wrap'} conversion if the document is change in a soft wrap-introduced
 *      virtual space (e.g. user start typing inside soft wrap-introduced indent). There is a dedicated method that
 *      does that if necessary - {@link #beforeDocumentChange(VisualPosition)};
 *    </li>
 * </ul>
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Jun 8, 2010 12:47:32 PM
 */
public class SoftWrapModelImpl implements SoftWrapModelEx {

  private static final Comparator<TextChange> SOFT_WRAPS_BY_OFFSET_COMPARATOR = new Comparator<TextChange>() {
    public int compare(TextChange c1, TextChange c2) {
      return c1.getStart() - c2.getEnd();
    }
  };

  private final CharBuffer myCharBuffer = CharBuffer.allocate(1);

  /**
   * Holds lines where soft wraps should be removed.
   * <p/>
   * The general idea is to do the following:
   * <ul>
   *   <li>listen for document changes, mark all soft wraps that belong to modified logical line as <code>'dirty'</code>;</li>
   *   <li>remove soft wraps marked as 'dirty' on repaint;</li>
   * </ul>
   */
  private final TIntHashSet myDirtyLines = new TIntHashSet();

  /** Holds registered soft wraps sorted by offsets in ascending order. */
  private final List<TextChange> myWraps = new ArrayList<TextChange>();
  private final List<TextChange> myWrapsView = Collections.unmodifiableList(myWraps);

  private final EditorImpl myEditor;
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

  @Nullable
  public TextChange getSoftWrap(int offset) {
    if (myActive <= 0) {
      dropDataIfNecessary();
    }
    int i = getSoftWrapIndex(offset);
    return i >= 0 ? myWraps.get(i) : null;
  }

  @Override
  @NotNull
  public List<TextChange> getSoftWrapsForLine(int documentLine) {
    Document document = myEditor.getDocument();
    int start = document.getLineStartOffset(documentLine);
    int end = document.getLineEndOffset(documentLine);

    int i = getSoftWrapIndex(start);
    if (i < 0) {
      i = -i - 1;
    }

    List<TextChange> result = null;
    for (; i < myWraps.size(); i++) {
      TextChange softWrap = myWraps.get(i);
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

  /**
   * Tries to find index of the target soft wrap stored at {@link #myWraps} collection. <code>'Target'</code> soft wrap is the one
   * that starts at the given offset.
   *
   * @param offset    target offset
   * @return          index that conforms to {@link Collections#binarySearch(List, Object)} contract, i.e. non-negative returned
   *                  index points to soft wrap that starts at the given offset; <code>'-(negative value) - 1'</code> points
   *                  to position at {@link #myWraps} collection where soft wrap for the given index should be inserted
   */
  private int getSoftWrapIndex(int offset) {
    TextChange searchKey = new TextChange("", offset);
    return Collections.binarySearch(myWraps, searchKey, SOFT_WRAPS_BY_OFFSET_COMPARATOR);
  }

  private boolean hasSoftWrapAt(int offset) {
    return getSoftWrapIndex(offset) >= 0;
  }

  /**
   * Inserts given soft wrap to {@link #myWraps} collection at the given index.
   *
   * @param softWrap    soft wrap to store
   * @return            previous soft wrap object stored for the same offset if any; <code>null</code> otherwise
   */
  @Nullable
  private TextChange storeSoftWrap(TextChange softWrap) {
    int i = Collections.binarySearch(myWraps, softWrap, SOFT_WRAPS_BY_OFFSET_COMPARATOR);
    if (i >= 0) {
      return myWraps.set(i, softWrap);
    }

    i = -i - 1;
    myWraps.add(i, softWrap);
    return null;
  }

  /**
   * Allows to answer if symbols of the given char array located at <code>[start; end)</code> interval should be soft wrapped,
   * i.e. represented on a next line.
   *
   * @param chars       symbols holder
   * @param start       target symbols sub-sequence start within the given char array (inclusive)
   * @param end         target symbols sub-sequence end within the given char array (exclusive)
   * @param position    current drawing position
   * @return            <code>true</code> if target symbols sub-sequence should be soft-wrapped; <code>false</code> otherwise
   */
  public boolean shouldWrap(char[] chars, int start, int end, Point position) {
    if (!isSoftWrappingEnabled()) {
      return false;
    }
    initIfNecessary();
    dropDataIfNecessary();

    if (hasSoftWrapAt(start)) {
      return true;
    }

    if (myRightEdgeLocation < 0) {
      assert false;
      return false;
    }

    //TODO den implement
    return position.x + (end - start) * 7 > myRightEdgeLocation;
  }

  private static boolean containsOnlyWhiteSpaces(char[] chars, int start, int end) {
    int i = CharArrayUtil.shiftForward(chars, start, "\n \t");
    return i >= end;
  }

  /**
   * Asks current model to perform soft wrapping for the characters sub-sequent identified by the given parameters.
   *
   * @param chars       symbols holder
   * @param start       target symbols sub-sequence start within the given char array (inclusive)
   * @param end         target symbols sub-sequence end within the given char array (exclusive)
   * @return            soft wrap registered for the given offset
   */
  public TextChange wrap(char[] chars, int start, int end) {
    TextChange result = getSoftWrap(start);
    if (result != null) {
      return result;
    }

    dropDataIfNecessary();
    //TODO den implement indent calculation on formatting options
    result = containsOnlyWhiteSpaces(chars, start, end) ? new TextChange("\n", start) : new TextChange("\n    ", start);
    storeSoftWrap(result);
    return result;
  }

  public List<TextChange> getRegisteredSoftWraps() {
    return myWrapsView;
  }

  /**
   * Drops information about registered soft wraps if they are marked as <code>'dirty'</code>.
   * //TODO den add doc about editor repainting
   *
   * @see #myDataIsDirty
   */
  private void dropDataIfNecessary() {
    if (!myEditor.isUnderRepainting()) {
      return;
    }
    Document document = myEditor.getDocument();
    for (TIntIterator it = myDirtyLines.iterator(); it.hasNext();) {
      int line = it.next();
      int start = document.getLineStartOffset(line);
      int end = document.getLineEndOffset(line);

      int startIndex = getSoftWrapIndex(start);
      if (startIndex < 0) {
        startIndex = -startIndex - 1;
      }
      int endIndex = startIndex;
      for (; endIndex < myWraps.size(); endIndex++) {
        TextChange softWrap = myWraps.get(endIndex);
        if (softWrap.getStart() >= end) {
          break;
        }
      }
      myWraps.subList(startIndex, endIndex).clear();
    }
    myDirtyLines.clear();

    if (!myDataIsDirty) {
      return;

    }
    myDataIsDirty = false;
    myWraps.clear();
  }

  @NotNull
  public LogicalPosition adjustLogicalPosition(@NotNull LogicalPosition defaultLogical, @NotNull VisualPosition visual) {
    if (myActive > 0 || !isSoftWrappingEnabled() || myWraps.isEmpty()) {
      return defaultLogical;
    }

    //TODO den check
    //if (defaultLogical.softWrapAware) {
    //  return defaultLogical;
    //}

    updateVisibleAreaIfNecessary();
    myActive++;
    try {
      return doAdjustLogicalPosition(defaultLogical, visual);
    }
    finally {
      myActive--;
    }
  }

  private LogicalPosition doAdjustLogicalPosition(LogicalPosition defaultLogical, VisualPosition visual) {
    DocumentImpl document = (DocumentImpl)myEditor.getDocument();
    int maxOffset = document.getLineEndOffset(Math.min(defaultLogical.line, document.getLineCount() - 1));

    // This index points to registered soft wrap that is guaranteed to be located after the target visual line.
    int endIndex = getSoftWrapIndex(maxOffset + 1);
    if (endIndex < 0) {
      endIndex = -endIndex - 1;
    }

    int softWrapIntroducedLineFeeds = 0;

    FoldingModel foldingModel = myEditor.getFoldingModel();
    int i = 0;
    int max = Math.min(myWraps.size(), endIndex);
    for (; i < max; i++) {
      TextChange softWrap = myWraps.get(i);
      if (foldingModel.isOffsetCollapsed(softWrap.getStart())) {
        continue;
      }

      int currentSoftWrapLineFeeds = StringUtil.countNewLines(softWrap.getText());
      int softWrapLine = document.getLineNumber(softWrap.getStart());
      int visualLineBeforeSoftWrapAppliance
        = myEditor.logicalToVisualPosition(new LogicalPosition(softWrapLine, 0)).line + softWrapIntroducedLineFeeds;
      if (visualLineBeforeSoftWrapAppliance > visual.line) {
        int logicalLine = defaultLogical.line - softWrapIntroducedLineFeeds;
        LogicalPosition foldingUnawarePosition
          = new LogicalPosition(logicalLine, defaultLogical.column, softWrapIntroducedLineFeeds, 0, 0, 0, 0);
        return adjustFoldingData(foldingModel, foldingUnawarePosition);
      }

      int visualLineAfterSoftWrapAppliance = visualLineBeforeSoftWrapAppliance + currentSoftWrapLineFeeds;
      if (visualLineAfterSoftWrapAppliance < visual.line) {
        softWrapIntroducedLineFeeds += currentSoftWrapLineFeeds;
        continue;
      }

      // If we're here that means that current soft wrap affects logical line that is matched to the given visual line.
      // We iterate from the logical line start then in order to calculate resulting logical position.
      Context context = new Context(defaultLogical, visual, softWrapIntroducedLineFeeds, visualLineBeforeSoftWrapAppliance, foldingModel);
      int startLineOffset = document.getLineStartOffset(softWrapLine);
      int endLineOffset = document.getLineEndOffset(softWrapLine);
      CharSequence documentText = document.getCharsNoThreadCheck();
      for (int j = startLineOffset; j < endLineOffset; j++) {

        // Process soft wrap at the current offset if any.
        if (j == softWrap.getStart()) {
          CharSequence softWrapText = softWrap.getText();
          for (int k = 0; k < softWrapText.length(); k++) {
            LogicalPosition result = context.onSoftWrapSymbol(softWrapText.charAt(k));
            if (result != null) {
              return result;
            }
          }
        }

        // Process document symbol.
        LogicalPosition result = context.onNonSoftWrapSymbol(documentText.charAt(j));
        if (result != null) {
          return result;
        }
      }

      // If we are here that means that target visual position is located at virtual space after the line end.
      int logicalLine = defaultLogical.line - softWrapIntroducedLineFeeds - context.lineFeedsFromCurrentSoftWrap;
      int logicalColumn = context.symbolsOnCurrentLogicalLine + visual.column - context.symbolsOnCurrentVisualLine;
      int softWrapColumnDiff = visual.column - logicalColumn;
      LogicalPosition foldingUnawarePosition = new LogicalPosition(
        logicalLine, logicalColumn, softWrapIntroducedLineFeeds + context.lineFeedsFromCurrentSoftWrap,
        context.lineFeedsFromCurrentSoftWrap, softWrapColumnDiff, 0, 0
      );
      return adjustFoldingData(foldingModel, foldingUnawarePosition);
    }

    // If we are here that means that there is no soft wrap on a logical line that corresponds to the target visual line.
    int logicalLine = defaultLogical.line - softWrapIntroducedLineFeeds;
    LogicalPosition foldingUnaware = new LogicalPosition(logicalLine, defaultLogical.column, softWrapIntroducedLineFeeds, 0, 0, 0, 0);
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
    DocumentImpl document = (DocumentImpl)myEditor.getDocument();
    CharSequence text = document.getCharsNoThreadCheck();
    for (FoldRegion foldRegion : foldingModel.getAllFoldRegions()) {
      if (foldRegion.getStartOffset() >= offset) {
        break;
      }

      if (foldRegion.isExpanded() || !foldRegion.isValid()) {
        continue;
      }

      int foldingStartLine = document.getLineNumber(foldRegion.getStartOffset());
      int foldingEndLine = document.getLineNumber(foldRegion.getEndOffset());
      foldedLines += foldingEndLine - foldingStartLine;

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
        if (position.column > endOffsetOfLineWithFoldingEnd) {
          columnsBetweenFoldingEndAndOffset += position.column - endOffsetOfLineWithFoldingEnd;
        }
        int visualFoldingStartColumn = calculateVisualFoldingStartColumn(foldRegion);
        int foldingPlaceholderWidth = toVisualColumnSymbolsNumber(foldRegion.getPlaceholderText());
        int visual = columnsBetweenFoldingEndAndOffset + visualFoldingStartColumn + foldingPlaceholderWidth;
        foldColumnDiff = visual - position.column;
        break;
      }
    }

    return new LogicalPosition(
      position.line, position.column, position.softWrapLines, position.linesFromActiveSoftWrap,
      softWrapColumnDiff, foldedLines, foldColumnDiff
    );
  }

  private int calculateVisualFoldingStartColumn(FoldRegion region) {
    DocumentImpl document = (DocumentImpl)myEditor.getDocument();
    int foldingStartOffset = region.getStartOffset();
    int logicalLine = document.getLineNumber(foldingStartOffset);
    int logicalLineStartOffset = document.getLineStartOffset(logicalLine);

    int softWrapIndex = getSoftWrapIndex(logicalLineStartOffset);
    if (softWrapIndex < 0) {
      softWrapIndex = -softWrapIndex - 1;
    }

    int startOffsetOfVisualLineWithFoldingStart = logicalLineStartOffset;
    for (; softWrapIndex < myWraps.size(); softWrapIndex++) {
      TextChange softWrap = myWraps.get(softWrapIndex);
      if (softWrap.getStart() >= foldingStartOffset) {
        break;
      }

      startOffsetOfVisualLineWithFoldingStart = softWrap.getStart();
    }

    assert startOffsetOfVisualLineWithFoldingStart <= foldingStartOffset;
    return toVisualColumnSymbolsNumber(document.getCharsNoThreadCheck(), startOffsetOfVisualLineWithFoldingStart, foldingStartOffset);
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
    DocumentImpl document = (DocumentImpl)myEditor.getDocument();
    CharSequence chars = document.getCharsNoThreadCheck();

    int targetLine = document.getLineNumber(offset);
    int targetLineStartOffset = document.getLineStartOffset(targetLine);

    updateVisibleAreaIfNecessary();

    int softWrapIntroducedLines = 0;
    int linesFromCurrentSoftWrap = 0;
    int symbolsOnCurrentLogicalLine = 0;
    int symbolsOnCurrentVisibleLine = 0;

    // Retrieve information about logical position that is soft-wraps unaware.
    LogicalPosition rawLineStartLogicalPosition = myEditor.offsetToLogicalPosition(targetLineStartOffset, false);

    // Calculate number of soft wrap-introduced lines before the line that holds target offset.
    int index = getSoftWrapIndex(targetLineStartOffset);
    if (index < 0) {
      index = -index - 1;
    }
    int max = Math.min(index, myWraps.size());
    for (int j = 0; j < max; j++) {
      softWrapIntroducedLines += StringUtil.countNewLines(myWraps.get(j).getText());
    }

    FoldingModel foldingModel = myEditor.getFoldingModel();

    // Return eagerly if there are no soft wraps before the target offset on a line that contains it.
    if (max >= myWraps.size() || myWraps.get(max).getStart() > offset) {
      LogicalPosition foldingUnawarePosition = new LogicalPosition(
        rawLineStartLogicalPosition.line, offset - targetLineStartOffset, softWrapIntroducedLines, 0, 0, 0, 0
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
            softWrapIntroducedLines++;
            linesFromCurrentSoftWrap++;
            symbolsOnCurrentVisibleLine = 0;
          }
          else {
            symbolsOnCurrentVisibleLine++;
          }
        }
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
      rawLineStartLogicalPosition.line, symbolsOnCurrentLogicalLine, softWrapIntroducedLines, linesFromCurrentSoftWrap,
      symbolsOnCurrentVisibleLine - symbolsOnCurrentLogicalLine, 0, 0
    );
    return adjustFoldingData(foldingModel, foldingUnawarePosition);
  }

  @NotNull
  public VisualPosition adjustVisualPosition(@NotNull LogicalPosition logical, @NotNull VisualPosition defaultVisual) {
    if (myActive > 0 || !isSoftWrappingEnabled() || myWraps.isEmpty()) {
      return defaultVisual;
    }

    if (logical.visualPositionAware) {
      // We don't need to recalculate logical position adjustments because given object already has them.
      return logical.toVisualPosition();
    }

    updateVisibleAreaIfNecessary();
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
    // Check if there are registered soft wraps before the target logical position.
    int maxOffset = myEditor.logicalPositionToOffset(logical);
    int endIndex = getSoftWrapIndex(maxOffset);
    if (endIndex < 0) {
      endIndex = -endIndex - 2; // We subtract '2' instead of '1' here in order to point to offset of the first soft wrap the
                                // is located before the given logical position.
    } 

    // Return eagerly if no soft wraps are registered before the target offset.
    if (endIndex < 0 || endIndex >= myWraps.size()) {
      return visual;
    }

    int lineDiff = 0;
    int column = -1;

    FoldingModel foldingModel = myEditor.getFoldingModel();
    int targetLogicalLineStartOffset = myEditor.logicalPositionToOffset(new LogicalPosition(logical.line, 0));
    for (int i = endIndex; i >= 0; i--) {
      TextChange softWrap = myWraps.get(i);
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

  public void beforeDocumentChange(@NotNull VisualPosition visualPosition) {
    LogicalPosition logicalPosition = myEditor.visualToLogicalPosition(visualPosition);
    int offset = myEditor.logicalPositionToOffset(logicalPosition);
    int i = getSoftWrapIndex(offset);
    if (i < 0 || i >= myWraps.size()) {
      return;
    }

    TextChange softWrap = myWraps.get(i);

    VisualPosition visualCaretPosition = myEditor.getCaretModel().getVisualPosition();

    // Consider given visual position to belong to soft wrap-introduced virtual space if visual position for the target offset
    // differs from the given.
    if (!visualPosition.equals(myEditor.offsetToVisualPosition(offset))) {
      myEditor.getDocument().replaceString(softWrap.getStart(), softWrap.getEnd(), softWrap.getText());
    }

    // Restore caret position.
    myEditor.getCaretModel().moveToVisualPosition(visualCaretPosition);
    myWraps.remove(i);
  }

  /**
   * //TODO den add doc
   *
   * @param change    change introduced to the document
   */
  private void updateRegisteredSoftWraps(TextChange change) {
    int softWrapIndex = getSoftWrapIndex(change.getStart());
    if (softWrapIndex < 0) {
      softWrapIndex = -softWrapIndex - 1;
    }

    if (softWrapIndex >= myWraps.size()) {
      return;
    }

    Document document = myEditor.getDocument();
    int firstChangedLine = document.getLineNumber(change.getStart());
    int lastChangedLine = Math.max(document.getLineNumber(change.getEnd()), firstChangedLine + StringUtil.countNewLines(change.getText()));
    for (int i = firstChangedLine; i <= lastChangedLine; i++) {
      myDirtyLines.add(i);
    }

    // Collect soft wraps which offsets should be modified.
    List<TextChange> modified = myWraps.subList(softWrapIndex, myWraps.size());
    List<TextChange> toModify = new ArrayList<TextChange>(modified);
    modified.clear();

    // Add modified soft wraps.
    for (TextChange softWrap : toModify) {
      myWraps.add(softWrap.advance(change.getDiff()));
    }
  }

  private void initIfNecessary() {
    if (myInitialized) {
      return;
    }
    myInitialized = true;

    // Subscribe for visible area changes notifications.
    ScrollingModel scrollingModel = myEditor.getScrollingModel();
    scrollingModel.addVisibleAreaListener(new VisibleAreaListener() {
      public void visibleAreaChanged(VisibleAreaEvent e) {
        updateVisibleAreaIfNecessary(e.getNewRectangle());
      }
    });
    updateVisibleAreaIfNecessary(scrollingModel.getVisibleArea());

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

  private void updateVisibleAreaIfNecessary() {
    updateVisibleAreaIfNecessary(myEditor.getScrollingModel().getVisibleArea());
  }

  private void updateVisibleAreaIfNecessary(@Nullable Rectangle visibleArea) {
    if (visibleArea == null || myActive > 0 || !isSoftWrappingEnabled()) {
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
    // Update right edge.
    int currentRightEdgeLocation = visibleArea.x + visibleArea.width;
    if (myRightEdgeLocation != currentRightEdgeLocation) {
      myDataIsDirty = true;
      myRightEdgeLocation = currentRightEdgeLocation;
    }
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

    public final FoldingModel foldingModel;
    public final LogicalPosition softWrapUnawareLogicalPosition;
    public final VisualPosition targetVisualPosition;
    public final int softWrapIntroducedLines;
    public final int visualLineBeforeSoftWrapAppliance;
    public int lineFeedsFromCurrentSoftWrap;
    public int symbolsOnCurrentLogicalLine;
    public int symbolsOnCurrentVisualLine;

    Context(LogicalPosition softWrapUnawareLogicalPosition, VisualPosition targetVisualPosition, int softWrapIntroducedLines,
                    int visualLineBeforeSoftWrapAppliance, FoldingModel foldingModel)
    {
      this.softWrapUnawareLogicalPosition = softWrapUnawareLogicalPosition;
      this.targetVisualPosition = targetVisualPosition;
      this.softWrapIntroducedLines = softWrapIntroducedLines;
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
        if (targetVisualPosition.line == visualLineBeforeSoftWrapAppliance + lineFeedsFromCurrentSoftWrap) {
          return build(targetVisualPosition.column - symbolsOnCurrentLogicalLine);
        }
        else {
          lineFeedsFromCurrentSoftWrap++;
          symbolsOnCurrentVisualLine = 0;
          return null;
        }
      }

      // Just update information about tracked symbols number if current visual line is too low.
      if (targetVisualPosition.line > visualLineBeforeSoftWrapAppliance + lineFeedsFromCurrentSoftWrap) {
        symbolsOnCurrentVisualLine += toVisualColumnSymbolsNumber(c);
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
      if (targetVisualPosition.line > visualLineBeforeSoftWrapAppliance + lineFeedsFromCurrentSoftWrap) {
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
      int logicalLine = softWrapUnawareLogicalPosition.line - softWrapIntroducedLines - lineFeedsFromCurrentSoftWrap;
      LogicalPosition foldingUnawareResult = new LogicalPosition(
        logicalLine, symbolsOnCurrentLogicalLine, softWrapIntroducedLines + lineFeedsFromCurrentSoftWrap, lineFeedsFromCurrentSoftWrap,
        softWrapColumnDiff, 0, 0
      );
      return adjustFoldingData(foldingModel, foldingUnawareResult);
    }
  }
}
