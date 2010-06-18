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
import com.intellij.openapi.editor.TextChange;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.nio.CharBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Default {@link SoftWrapModelEx} implementation.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Jun 8, 2010 12:47:32 PM
 */
public class SoftWrapModelImpl implements SoftWrapModelEx {

  private final CharBuffer myCharBuffer = CharBuffer.allocate(1);

  /** Holds mappings like {@code 'soft wrap start offset at document' -> 'soft wrap indent spaces'}. */
  private final TIntObjectHashMap<TextChange> myWraps = new TIntObjectHashMap<TextChange>();

  /** Holds soft wraps offsets in ascending order. */
  private final TIntArrayList myWrapOffsets = new TIntArrayList();

  /** Caches soft wrap-aware logical positions by offset. */
  private final TIntObjectHashMap<LogicalPosition> myLogicalPositionsByOffsets = new TIntObjectHashMap<LogicalPosition>();

  private final Map<VisualPosition, LogicalPosition> myLogicalPositionsByVisual = new HashMap<VisualPosition, LogicalPosition>();

  private final EditorImpl myEditor;
  private Rectangle myLastVisibleArea;
  private VisualPosition myFirstLineVisualPosition = new VisualPosition(0, 0);
  private LogicalPosition myFirstLineLogicalPosition = new LogicalPosition(0, 0);
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
    return myWraps.get(offset);
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
    return position.x + (end - start) * 7 > myRightEdgeLocation;
  }

  private static boolean containsOnlyWhiteSpaces(char[] chars, int start, int end) {
    int i = CharArrayUtil.shiftForward(chars, start, "\n \t");
    return i >= end;
  }

  /**
   * Asks current model to register soft wrap at the current offset of the active editor document.
   *
   * @param offset      target offset of the editor document where soft wrap should be registered
   * @return            soft wrap registered for the given offset
   */
  public TextChange wrap(int offset) {
    TextChange result = myWraps.get(offset);
    if (result != null) {
      return result;
    }

    dropDataIfNecessary();
    result = new TextChange("\n    ", offset); //TODO den implement indent calculation on formatting options basis.
    myWraps.put(offset, result);

    int i = myWrapOffsets.binarySearch(offset);
    if (i < 0) {
      i = -i - 1;

    }
    if (i < myWrapOffsets.size()) {
      myWrapOffsets.insert(i, offset);
    } else {
      myWrapOffsets.add(offset);
    }

    updateCachesOnNewSoftWrapAddition(result);
    return result;
  }

  private void updateCachesOnNewSoftWrapAddition(TextChange softWrap) {
    int lineFeedsAtNewWrap = StringUtil.countNewLines(softWrap.getText());
    if (lineFeedsAtNewWrap <= 0) {
      return;
    }

    Map<VisualPosition, LogicalPosition> updated = new HashMap<VisualPosition, LogicalPosition>();
    boolean shouldUpdate = false;
    for (Map.Entry<VisualPosition, LogicalPosition> entry : myLogicalPositionsByVisual.entrySet()) {
      if (myEditor.logicalPositionToOffset(entry.getValue()) <= softWrap.getStart()) {
        updated.put(entry.getKey(), entry.getValue());
        continue;
      }
      shouldUpdate = true;
      VisualPosition oldVisual = entry.getKey();
      LogicalPosition oldLogical = entry.getValue();

      VisualPosition newVisual = new VisualPosition(oldVisual.line + lineFeedsAtNewWrap, oldVisual.column);
      LogicalPosition newLogical = new LogicalPosition(
        oldLogical.line, oldLogical.column, oldLogical.softWrapLines + lineFeedsAtNewWrap,
        oldLogical.linesFromActiveSoftWrap, oldLogical.softWrapColumns
      );
      updated.put(newVisual, newLogical);
    }

    if (shouldUpdate) {
      myLogicalPositionsByVisual.clear();
      myLogicalPositionsByVisual.putAll(updated);
    }
  }


  /**
   * Drops information about registered soft wraps if they are marked as <code>'dirty'</code>.
   *
   * @see #myDataIsDirty
   */
  private void dropDataIfNecessary() {
    if (!myDataIsDirty) {
      return;

    }

    myDataIsDirty = false;
    myWraps.clear();
    myWrapOffsets.clear();
    myLogicalPositionsByOffsets.clear();
    myLogicalPositionsByVisual.clear();
  }

  @NotNull
  public LogicalPosition adjustLogicalPosition(@NotNull LogicalPosition defaultLogical, @NotNull VisualPosition visual) {
    if (myActive > 0 || !isSoftWrappingEnabled() || myWraps.isEmpty()) {
      return defaultLogical;
    }

    LogicalPosition cached = myLogicalPositionsByVisual.get(visual);
    if (cached != null) {
      return cached;
    }

    updateVisibleAreaIfNecessary();
    myActive++;
    try {
      return doAdjustLogicalPosition(defaultLogical, visual);
    }
    finally {
      myActive--;
    }
  }

  @SuppressWarnings({"AssignmentToForLoopParameter"})
  private LogicalPosition doAdjustLogicalPosition(LogicalPosition defaultLogical, VisualPosition visual) {
    Context context = new Context();
    DocumentImpl document = (DocumentImpl)myEditor.getDocument();
    CharSequence chars = document.getCharsNoThreadCheck();
    FoldingModel foldingModel = myEditor.getFoldingModel();

    // There is a possible case that first line of current visible area points to the line that is soft wrapped.
    // Example:
    //     foo("xxx1", "xxx2", "xxx3", <- soft wrap
    //         "xxx4")                 <- first visible line
    // We start processing from the start of the logical line that corresponds to the first visible line ('foo()' call in our example),
    // hence, we need to skip document and soft wrap symbols that are located outside the visible area.
    // This variable with value over than zero defines that all document and soft wrap symbols should be skipped until necessary
    // number of soft wrap line feeds are encountered.
    int softWrapLinesToSkip = myFirstLineLogicalPosition.linesFromActiveSoftWrap;

    int start = document.getLineStartOffset(myFirstLineLogicalPosition.line);
    for (int i = start, max = chars.length(); i < max && getCurrentVisualLine(context) <= visual.line; i++) {
      if (getCurrentVisualLine(context) == visual.line && context.symbolsOnCurrentVisibleLine >= visual.column) {
        int softWrapColumns = context.softWrapsSymbolsOnCurrentVisibleLine > 0
                              ? context.symbolsOnCurrentVisibleLine - context.symbolsOnCurrentLogicalLine : 0;
        return context.buildLogicalPosition(softWrapColumns);
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
          if (getCurrentVisualLine(context) == visual.line && context.symbolsOnCurrentVisibleLine >= visual.column) {
            return context.buildLogicalPosition(context.softWrapsSymbolsOnCurrentVisibleLine - context.symbolsOnCurrentLogicalLine);
          }

          if (softWrapText.charAt(j) == '\n') {
            if (softWrapLinesToSkip-- > 0) {
              continue;
            }
            if (getCurrentVisualLine(context) == visual.line) {
              return context.buildLogicalPosition(visual.column - context.symbolsOnCurrentLogicalLine);
            }
            else {
              context.onLineFeedInsideSoftWrap();
              updateLogicalByVisualCache(context);
            }
          }
          else {
            context.onNonLineFeedInsideSoftWrap(softWrapText.charAt(j));
          }
        }
      }

      if (softWrapLinesToSkip > 0) {
        continue;
      }

      if (getCurrentVisualLine(context) == visual.line && context.symbolsOnCurrentVisibleLine >= visual.column) {
        return context.buildLogicalPosition(context.softWrapsSymbolsOnCurrentVisibleLine - context.symbolsOnCurrentLogicalLine);
      }

      char c = chars.charAt(i);

      // Check if there is a line break at the document.
      if (c != '\n') {
        context.onNonLineFeedOutsideSoftWrap(c);
        continue;

      }

      if (getCurrentVisualLine(context) != visual.line) {
        context.onLineFeedOutsideSoftWrap();
        updateLogicalByVisualCache(context);
        continue;
      }

      int column = context.symbolsOnCurrentLogicalLine;
      int columnDiff = visual.column - context.symbolsOnCurrentVisibleLine;
      if (columnDiff > 0) {
        column += columnDiff;
      }
      LogicalPosition result = new LogicalPosition(
        myFirstLineLogicalPosition.line + context.visualLineOnCurrentScreen - context.softWrapIntroducedLines,
        column,
        context.softWrapIntroducedLines + myFirstLineLogicalPosition.softWrapLines,
        context.linesFromCurrentSoftWrap,
        visual.column - column
      );
      if (visual.column == 0) {
        myLogicalPositionsByVisual.put(visual, result);
      }
      return result;
    }
    return defaultLogical;
  }

  private int getCurrentVisualLine(Context context) {
    return myFirstLineVisualPosition.line + context.visualLineOnCurrentScreen;
  }

  private void updateLogicalByVisualCache(Context context) {
    VisualPosition visual = new VisualPosition(getCurrentVisualLine(context), context.symbolsOnCurrentVisibleLine);
    LogicalPosition logical = context.buildLogicalPosition(context.symbolsOnCurrentVisibleLine - context.symbolsOnCurrentLogicalLine);
    if (myLogicalPositionsByVisual.containsKey(visual)) {
      return;
    }
    myLogicalPositionsByVisual.put(visual, logical);
  }

  @SuppressWarnings({"AssignmentToForLoopParameter"})
  @NotNull
  public LogicalPosition offsetToLogicalPosition(int offset) {
    DocumentImpl document = (DocumentImpl)myEditor.getDocument();
    CharSequence chars = document.getCharsNoThreadCheck();

    int targetLine = document.getLineNumber(offset);
    int targetLineStartOffset = document.getLineStartOffset(targetLine);

    updateVisibleAreaIfNecessary();

    // Return eagerly if the result is already cached.
    LogicalPosition cached = myLogicalPositionsByOffsets.get(offset);
    if (cached != null) {
      return cached;
    }

    int softWrapIntroducedLines = 0;
    int linesFromCurrentSoftWrap = 0;
    int symbolsOnCurrentLogicalLine = 0;
    int symbolsOnCurrentVisibleLine = 0;

    // Retrieve information about logical position that is soft-wraps unaware.
    int rawColumn = toVisualColumnSymbolsNumber(chars, targetLineStartOffset, offset);
    LogicalPosition rawLineStartLogicalPosition = new LogicalPosition(targetLine, rawColumn);

    // Calculate number of soft wrap-introduced lines before the line that holds target offset.
    int index = myWrapOffsets.binarySearch(targetLineStartOffset);
    if (index < 0) {
      index = -index - 1;
    }
    int max = Math.min(index, myWrapOffsets.size());
    for (int j = 0; j < max; j++) {
      softWrapIntroducedLines += StringUtil.countNewLines(myWraps.get(myWrapOffsets.get(j)).getText());
    }

    // Return eagerly if there is no soft wraps before the target offset on a line that contains it.
    if (max >= myWrapOffsets.size() || myWrapOffsets.get(max) > offset) {
      return new LogicalPosition(rawLineStartLogicalPosition.line, rawLineStartLogicalPosition.column, softWrapIntroducedLines, 0, 0);
    }

    // Calculate number of lines and columns introduced by soft wrap located at the line that holds target offset if any.
    FoldingModel foldingModel = myEditor.getFoldingModel();
    max = Math.min(chars.length(), offset);
    for (int i = targetLineStartOffset; i < max; i++) {
      FoldRegion region = foldingModel.getCollapsedRegionAtOffset(i);
      if (region != null && !region.isExpanded()) {
        // Assuming that folded region placeholder doesn't contain line feed symbols.
        i = region.getEndOffset();
        symbolsOnCurrentLogicalLine += region.getEndOffset() - region.getStartOffset();
        symbolsOnCurrentVisibleLine += region.getPlaceholderText().length();
        continue;
      }

      TextChange softWrap = myWraps.get(i);
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

      // Assuming that no line feed is contained before target offset on a line that holds it.
      symbolsOnCurrentLogicalLine++;
      symbolsOnCurrentVisibleLine++;
    }
    LogicalPosition result = new LogicalPosition(
      rawLineStartLogicalPosition.line, symbolsOnCurrentLogicalLine, softWrapIntroducedLines, linesFromCurrentSoftWrap,
      symbolsOnCurrentVisibleLine - symbolsOnCurrentLogicalLine
    );
    myLogicalPositionsByOffsets.put(offset, result);
    return result;
  }

  @NotNull
  public VisualPosition adjustVisualPosition(@NotNull LogicalPosition logical, @NotNull VisualPosition defaultVisual) {
    if (myActive > 0 || !isSoftWrappingEnabled() || myWraps.isEmpty()) {
      return defaultVisual;
    }

    if (isSoftWrapAware(logical)) {
      // We don't need to recalculate logical position adjustments because given object already has them.
      return new VisualPosition(logical.line + logical.softWrapLines, logical.column + logical.softWrapColumns);
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
    int endIndex = myWrapOffsets.binarySearch(maxOffset);
    if (endIndex < 0) {
      endIndex = -endIndex - 2; // We subtract '2' instead of '1' here in order to point to offset of the first soft wrap the
                                // is located before the given logical position.
    } 

    // Return eagerly if no soft wraps are registered before the target offset.
    if (endIndex < 0 || endIndex >= myWrapOffsets.size()) {
      return visual;
    }

    int lineDiff = 0;
    int column = -1;

    FoldingModel foldingModel = myEditor.getFoldingModel();
    int targetLogicalLineStartOffset = myEditor.logicalPositionToOffset(new LogicalPosition(logical.line, 0));
    for (int i = endIndex; i >= 0; i--) {
      int offset = myWrapOffsets.get(i);

      if (foldingModel.isOffsetCollapsed(offset)) {
        continue;
      }
      TextChange softWrap = myWraps.get(offset);
      if (softWrap == null) {
        assert false;
        continue;
      }

      CharSequence softWrapText = softWrap.getText();
      int softWrapLines = StringUtil.countNewLines(softWrapText);

      // Count lines introduced by the current soft wrap. We assume that the soft wrap is located before target offset,
      // hence, we're free to count all of its line feeds.
      lineDiff += softWrapLines;

      // Count soft wrap column offset only if it's located at the same line as the target offset.
      if (softWrapLines > 0 && offset >= targetLogicalLineStartOffset) {
        for (int j = softWrapText.length() - 1; j >= 0; j--) {
          if (softWrapText.charAt(j) == '\n') {
            column = maxOffset - offset - j + 1;
            break;
          }
        }
      }
    }

    int columnToUse = column >= 0 ? column : visual.column;
    return new VisualPosition(visual.line + lineDiff, columnToUse);
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

    // Subscribe for document change updates.
    myEditor.getDocument().addDocumentListener(new DocumentListener() {
      public void beforeDocumentChange(DocumentEvent event) {
        //// Drop offset-logical position mappings.
        //myLogicalPositionsByOffsets.clear();
        //
        //// Drop all soft wraps from logical line that is being changed.
        //TIntArrayList indices = getSoftWrapIndicesForLogicalLine(event.getOffset());
        //if (indices.isEmpty()) {
        //  return;
        //}
        //for (int i = 0; i < indices.size(); i++) {
        //  myWraps.remove(myWrapOffsets.get(indices.get(i)));
        //}
        //myWrapOffsets.remove(indices.get(0), indices.size());
      }

      public void documentChanged(DocumentEvent event) {
      }
    });
  }

  /**
   * Allows to ask for indices that are used to store soft wraps offsets at {@link #myWrapOffsets} for the line that holds given
   * document offset.
   *
   * @param offset    target document offset
   * @return          collection that contains indices of soft wrap offsets at {@link #myWrapOffsets} collection for the line
   *                  that contains document text at given offset
   */
  private TIntArrayList getSoftWrapIndicesForLogicalLine(int offset) {
    TIntArrayList result = new TIntArrayList();

    Document document = myEditor.getDocument();
    int targetLine = document.getLineNumber(offset);
    int start = document.getLineStartOffset(targetLine);
    int end = document.getLineEndOffset(targetLine);

    int i = myWrapOffsets.binarySearch(start);
    if (i < 0) {
      i = -i - 1;
    }

    for (; i < myWrapOffsets.size(); i++) {
      if (i >= end) {
        break;
      }
      result.add(i);
    }
    return result;
  }

  private void updateVisibleAreaIfNecessary() {
    updateVisibleAreaIfNecessary(myEditor.getScrollingModel().getVisibleArea());
  }

  private void updateVisibleAreaIfNecessary(@Nullable Rectangle visibleArea) {
    if (visibleArea == null || myActive > 0 || !isSoftWrappingEnabled() || visibleArea.equals(myLastVisibleArea)) {
      return;
    }
    myActive++;
    try {
      doUpdateVisibleAreaChange(visibleArea);
    }
    finally {
      myActive--;
    }
    myLastVisibleArea = visibleArea;
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
    myFirstLineVisualPosition = myEditor.xyToVisualPosition(visibleArea.getLocation());
    myFirstLineLogicalPosition = myLogicalPositionsByVisual.get(myFirstLineVisualPosition);
    if (myFirstLineLogicalPosition == null) {
      myFirstLineLogicalPosition = myEditor.visualToLogicalPosition(myFirstLineVisualPosition);
    }

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

  private int toVisualColumnSymbolsNumber(CharSequence text, int start, int end) {
    return EditorUtil.calcColumnNumber(myEditor, text, start, end, EditorUtil.getTabSize(myEditor));
  }

  private static boolean isSoftWrapAware(LogicalPosition position) {
    return position.softWrapLines != 0 || position.softWrapColumns != 0;
  }

  private class Context {

    public int softWrapIntroducedLines;
    public int linesFromCurrentSoftWrap;
    public int visualLineOnCurrentScreen;
    public int symbolsOnCurrentLogicalLine;
    public int symbolsOnCurrentVisibleLine;
    public int softWrapsSymbolsOnCurrentVisibleLine;

    public void onNonLineFeedInsideSoftWrap(char c) {
      symbolsOnCurrentVisibleLine++;
      softWrapsSymbolsOnCurrentVisibleLine += toVisualColumnSymbolsNumber(c);
    }

    public void onLineFeedInsideSoftWrap() {
      softWrapIntroducedLines++;
      linesFromCurrentSoftWrap++;
      visualLineOnCurrentScreen++;
      symbolsOnCurrentVisibleLine = 0;
      softWrapsSymbolsOnCurrentVisibleLine = 0;
    }

    public void onNonLineFeedOutsideSoftWrap(char c) {
      symbolsOnCurrentLogicalLine++;
      symbolsOnCurrentVisibleLine += toVisualColumnSymbolsNumber(c);
    }

    public void onLineFeedOutsideSoftWrap() {
      visualLineOnCurrentScreen++;
      linesFromCurrentSoftWrap = 0;
      symbolsOnCurrentVisibleLine = 0;
      symbolsOnCurrentLogicalLine = 0;
      softWrapsSymbolsOnCurrentVisibleLine = 0;
    }

    public LogicalPosition buildLogicalPosition(int softWrapColumns) {
      return new LogicalPosition(
        myFirstLineLogicalPosition.line + visualLineOnCurrentScreen - softWrapIntroducedLines, symbolsOnCurrentLogicalLine,
        softWrapIntroducedLines + myFirstLineLogicalPosition.softWrapLines, linesFromCurrentSoftWrap, softWrapColumns
      );
    }
  }
}
