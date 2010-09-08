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

import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorTextRepresentationHelper;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.editor.impl.softwrap.*;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.TIntIntHashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * //TODO den add doc
 *
 * Default {@link SoftWrapApplianceManager} implementation that is built with the following design guide lines:
 * <pre>
 * <ul>
 *   <li>
 *      perform soft wrap processing per-logical line, i.e. every time current manager is asked to process
 *      particular text range, it calculates logical lines that contain all target symbols, checks if they should
 *      be soft-wrapped and registers corresponding soft wraps if necessary;
 *   </li>
 *   <li>
 *      objects of this class remember processed logical lines and perform new processing for them only if visible
 *      area width is changed;
 *   </li>
 *   <li>
 *      {@link SoftWrapsStorage#removeAll() drops all registered soft wraps} if visible area width is changed;
 *   </li>
 * </ul>
 * </pre>
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Jul 5, 2010 10:01:27 AM
 */
public class SoftWrapApplianceManager implements FoldingListener {

  /** Enumerates possible type of soft wrap indents to use. */
  enum IndentType {
    /** Don't apply special indent to soft-wrapped line at all. */
    NONE,

    /**
     * Indent soft wraps for the {@link EditorSettings#getCustomSoftWrapIndent() user-defined number of columns}
     * to the start of the previous visual line.
     */
    CUSTOM
  }

  private final List<SoftWrapAwareDocumentParsingListener> myListeners = new ArrayList<SoftWrapAwareDocumentParsingListener>();
  private final List<TextRange> myDirtyRanges = new ArrayList<TextRange>();

  private final SoftWrapsStorage               myStorage;
  private final EditorEx                       myEditor;
  private final SoftWrapPainter myPainter;
  private final EditorTextRepresentationHelper myRepresentationHelper;

  private LineWrapPositionStrategy myLineWrapPositionStrategy;
  private boolean myCustomIndentUsedLastTime;
  private int myCustomIndentValueUsedLastTime;
  private int myVisibleAreaWidth;

  public SoftWrapApplianceManager(@NotNull SoftWrapsStorage storage,
                                  @NotNull EditorEx editor,
                                  @NotNull SoftWrapPainter painter,
                                  @NotNull EditorTextRepresentationHelper representationHelper)
  {
    myStorage = storage;
    myEditor = editor;
    myPainter = painter;
    myRepresentationHelper = representationHelper;
  }

  public void registerSoftWrapIfNecessary(@NotNull Rectangle clip, int startOffset) {
    //TODO den    implement perform full soft wraps recalculation at background thread, calculate soft wraps only for the target
    //TODO den    visible clip at EDT
    dropDataIfNecessary();
    if (myVisibleAreaWidth <= 0 || myDirtyRanges.isEmpty()) {
      return;
    }
    recalculateSoftWraps();

  }

  public void release() {
    myDirtyRanges.clear();
    myDirtyRanges.add(new TextRange(0, myEditor.getDocument().getTextLength()));
    myLineWrapPositionStrategy = null;
  }

  private void recalculateSoftWraps() {
    for (TextRange dirtyRange : myDirtyRanges) {
      recalculateSoftWraps(dirtyRange);
    }
    myDirtyRanges.clear();
  }

  @SuppressWarnings({"AssignmentToForLoopParameter"})
  private void recalculateSoftWraps(TextRange range) {

    // Define start of the visual line that holds target range start.
    int start = range.getStartOffset();
    int end;
    VisualPosition visual = new VisualPosition(myEditor.offsetToVisualPosition(start).line, 0);
    LogicalPosition logical = myEditor.visualToLogicalPosition(visual);
    start = myEditor.logicalPositionToOffset(logical);
    Document document = myEditor.getDocument();
    CharSequence text = document.getCharsSequence();
    IterationState iterationState = new IterationState(myEditor, start, false);
    TextAttributes attributes = iterationState.getMergedAttributes();
    int fontType = attributes.getFontType();

    ProcessingContext context = new ProcessingContext(logical, start, myEditor, myRepresentationHelper);
    Point point = myEditor.visualPositionToXY(visual);
    context.x = point.x;
    int newX;
    int spaceWidth = EditorUtil.getSpaceWidth(fontType, myEditor);

    LogicalLineData logicalLineData = new LogicalLineData();
    logicalLineData.update(logical.line, spaceWidth, myEditor);

    ProcessingContext startLineContext = context.clone();
    JComponent contentComponent = myEditor.getContentComponent();
    TIntIntHashMap offset2fontType = new TIntIntHashMap();
    TIntIntHashMap offset2widthInPixels = new TIntIntHashMap();
    TIntIntHashMap fontType2spaceWidth = new TIntIntHashMap();
    fontType2spaceWidth.put(fontType, spaceWidth);
    int softWrapStartOffset = startLineContext.offset;

    // Perform soft wraps calculation.
    while (!iterationState.atEnd()) {
      FoldRegion currentFold = iterationState.getCurrentFold();
      if (currentFold != null) {
        String placeholder = currentFold.getPlaceholderText();
        FontInfo fontInfo = EditorUtil.fontForChar(placeholder.charAt(0), fontType, myEditor);
        newX = context.x;
        for (int i = 0; i < placeholder.length(); i++) {
          newX += fontInfo.charWidth(placeholder.charAt(i), contentComponent);
        }
        if (newX >= myVisibleAreaWidth) {
          logicalLineData.update(currentFold.getStartOffset(), spaceWidth);
          SoftWrap softWrap = registerSoftWrap(
            softWrapStartOffset, start, start, logicalLineData.indentInColumns,
            logicalLineData.indentInPixels, spaceWidth
          );
          softWrapStartOffset = softWrap.getStart();
          if (softWrap.getStart() < start) {
            revertListeners(softWrap.getStart(), context.visualLine);
            for (int j = currentFold.getStartOffset() - 1; j >= softWrap.getStart(); j--) {
              int pixelsDiff = offset2widthInPixels.get(j);
              int columnsDiff = calculateWidthInColumns(pixelsDiff, fontType2spaceWidth.get(offset2fontType.get(j)));
              context.offset--;
              context.logicalColumn -= columnsDiff;
              context.visualColumn -= columnsDiff;
            }
            notifyListenersOnBeforeSoftWrap(context);

          }

          context.visualColumn = 0;
          context.softWrapColumnDiff = context.visualColumn - context.foldingColumnDiff - context.logicalColumn;
          context.softWrapLinesCurrent++;
          context.visualLine++;
          notifyListenersOnAfterSoftWrapLineFeed(context);

          context.x = softWrap.getIndentInPixels();
          context.visualColumn = softWrap.getIndentInColumns();
          context.softWrapColumnDiff += softWrap.getIndentInColumns();
          startLineContext.from(context);

          for (int j = softWrap.getStart(); j < start; j++) {
            fontType = offset2fontType.get(j);
            newX = calculateNewX(context, fontType, contentComponent);
            processSymbol(context, startLineContext, logicalLineData, fontType, newX, fontType2spaceWidth, offset2widthInPixels,
                          offset2fontType);
          }
          continue;
        }
        else {
          int visualLineBefore = context.visualLine;
          int logicalColumnBefore = context.logicalColumn;
          context.advance(currentFold);
          context.x = newX;
          int collapsedFoldingWidthInColumns = context.logicalColumn;
          if (context.visualLine <= visualLineBefore) {
            // Single-line fold region.
            collapsedFoldingWidthInColumns = context.logicalColumn - logicalColumnBefore;
          }
          notifyListenersOnFoldRegion(currentFold, collapsedFoldingWidthInColumns, visualLineBefore);
          start = context.offset;
        }
      }

      end = iterationState.getEndOffset();
      for (int i = start; i < end; i++) {
        if (!offset2fontType.contains(i)) {
          offset2fontType.put(i, fontType);
        }
      }
      for (int i = start; i < end; i++) {
        char c = text.charAt(i);
        if (offset2fontType.contains(i)) {
          fontType = offset2fontType.get(i);
        }
        context.symbol = c;
        if (c == '\n') {
          processSymbol(context, startLineContext, logicalLineData, fontType, 0, fontType2spaceWidth, offset2widthInPixels, offset2fontType);
          softWrapStartOffset = startLineContext.offset;
          continue;
        }

        if (offset2widthInPixels.contains(context.offset)) {
          newX = context.x + offset2widthInPixels.get(context.offset);
        }
        else {
          newX = calculateNewX(context, fontType, contentComponent);
        }

        if (newX >= myVisibleAreaWidth) {
          logicalLineData.update(i, spaceWidth);
          SoftWrap softWrap = registerSoftWrap(
            softWrapStartOffset, Math.max(softWrapStartOffset, i - 1), calculateSoftWrapEndOffset(softWrapStartOffset, end),
            logicalLineData.indentInColumns, logicalLineData.indentInPixels, spaceWidth
          );
          int newI = softWrap.getStart();

          // There are two possible options: soft wrap offset is located before/after the current offset (it may be
          // located after offset in situation when it's not possible to wrap in [softWrapStartOffset; currentOffset)
          // interval). We should process that accordingly.
          if (newI < i) {
            revertListeners(newI, context.visualLine);
            for (int j = i - 1; j >= newI; j--) {
              int pixelsDiff = offset2widthInPixels.get(j);
              int columnsDiff = calculateWidthInColumns(pixelsDiff, fontType2spaceWidth.get(offset2fontType.get(j)));
              context.offset--;
              context.logicalColumn -= columnsDiff;
              context.visualColumn -= columnsDiff;
            }
          }
          else if (newI > i) {
            processSymbol(context, startLineContext, logicalLineData, fontType, newX, fontType2spaceWidth, offset2widthInPixels,
                          offset2fontType);
            for (int j = i + 1; j < newI; j++) {
              context.symbol = text.charAt(j);
              newX = calculateNewX(context, fontType, contentComponent);
              processSymbol(context, startLineContext, logicalLineData, fontType, newX, fontType2spaceWidth, offset2widthInPixels,
                            offset2fontType);
            }
          }

          notifyListenersOnBeforeSoftWrap(context);
          softWrapStartOffset = newI;

          context.visualColumn = 0;
          context.softWrapColumnDiff = context.visualColumn - context.foldingColumnDiff - context.logicalColumn;
          context.softWrapLinesCurrent++;
          context.visualLine++;
          notifyListenersOnAfterSoftWrapLineFeed(context);

          context.x = softWrap.getIndentInPixels();
          context.visualColumn = softWrap.getIndentInColumns();
          context.softWrapColumnDiff += softWrap.getIndentInColumns();
          i = newI - 1/* because of loop increment */;
          startLineContext.from(context);
        }
        else {
          processSymbol(context, startLineContext, logicalLineData, fontType, newX, fontType2spaceWidth, offset2widthInPixels,
                        offset2fontType);
        }
      }

      iterationState.advance();
      attributes = iterationState.getMergedAttributes();
      fontType = attributes.getFontType();
      start = iterationState.getStartOffset();
    }
  }

  private int calculateNewX(ProcessingContext context, int fontType, JComponent component) {
    if (context.symbol == '\t') {
      return EditorUtil.nextTabStop(context.x, myEditor);
    }
    else {
      FontInfo fontInfo = EditorUtil.fontForChar(context.symbol, fontType, myEditor);
      return context.x + fontInfo.charWidth(context.symbol, component);
    }
  }

  private int calculateSoftWrapEndOffset(int start, int end) {
    CharSequence text = myEditor.getDocument().getCharsSequence();
    for (int i = start; i < end; i++) {
      char c = text.charAt(i);
      if (c == '\n') {
        return i;
      }
    }
    return end;
  }

  private void processSymbol(ProcessingContext context, ProcessingContext startLineContext, LogicalLineData logicalLineData,
                             int fontType, int newX, TIntIntHashMap fontType2spaceWidth, TIntIntHashMap offset2widthInPixels,
                             TIntIntHashMap offset2fontType)
  {
    int spaceWidth;
    if (fontType2spaceWidth.contains(fontType)) {
      spaceWidth = fontType2spaceWidth.get(fontType);
    }
    else {
      spaceWidth = EditorUtil.getSpaceWidth(fontType, myEditor);
      fontType2spaceWidth.put(fontType, spaceWidth);
    }

    if (context.symbol == '\n') {
      context.symbolWidthInColumns = 0;
      context.symbolWidthInPixels = 0;
      notifyListenersOnProcessedSymbol(context);
      context.offset++;
      context.onNewLine();
      offset2fontType.clear();
      startLineContext.from(context);
      logicalLineData.update(context.logicalLine, spaceWidth, myEditor);
      return;
    }

    context.symbolWidthInPixels = newX - context.x;
    context.symbolWidthInColumns = calculateWidthInColumns(context.symbolWidthInPixels, spaceWidth);
    notifyListenersOnProcessedSymbol(context);
    context.visualColumn += context.symbolWidthInColumns;
    context.logicalColumn += context.symbolWidthInColumns;
    context.x = newX;
    offset2widthInPixels.put(context.offset, context.symbolWidthInPixels);
    context.offset++;
  }

  private static int calculateWidthInColumns(int widthInPixels, int spaceWithInPixels) {
    int result = widthInPixels / spaceWithInPixels;
    if (widthInPixels % spaceWithInPixels > 0) {
      result++;
    }
    return result;
  }

  private SoftWrap registerSoftWrap(int minOffset, int preferredOffset, int maxOffset, int indentInColumns, int indentInPixels,
                                    int spaceSize)
  {
    Document document = myEditor.getDocument();

    // Performance optimization implied by profiling results analysis.
    if (myLineWrapPositionStrategy == null) {
      myLineWrapPositionStrategy = LanguageLineWrapPositionStrategy.INSTANCE.forEditor(myEditor);
    }
    int softWrapOffset = myLineWrapPositionStrategy.calculateWrapPosition(
      document.getCharsSequence(), minOffset, maxOffset, preferredOffset, true
    );
    int indent = 0;
    if (myCustomIndentUsedLastTime) {
      indent = myCustomIndentValueUsedLastTime;
    }
    SoftWrapImpl softWrap = new SoftWrapImpl(
      new TextChangeImpl("\n" + StringUtil.repeatSymbol(' ', indentInColumns + indent), softWrapOffset, softWrapOffset),
      indentInColumns + indent + 1/* for 'after soft wrap' drawing */,
      indentInPixels + (indent * spaceSize) + myPainter.getMinDrawingWidth(SoftWrapDrawingType.AFTER_SOFT_WRAP)
    );
    myStorage.storeOrReplace(softWrap, true);
    return softWrap;
  }

  //TODO den add doc
  public void dropDataIfNecessary() {
    // Check if we need to recalculate soft wraps due to indent settings change.
    boolean indentChanged = false;
    IndentType currentIndentType = getIndentToUse();
    boolean useCustomIndent = currentIndentType == IndentType.CUSTOM;
    int currentCustomIndent = myEditor.getSettings().getCustomSoftWrapIndent();
    if (useCustomIndent ^ myCustomIndentUsedLastTime || (useCustomIndent && myCustomIndentValueUsedLastTime != currentCustomIndent)) {
      indentChanged = true;
    }
    myCustomIndentUsedLastTime = useCustomIndent;
    myCustomIndentValueUsedLastTime = currentCustomIndent;

    // Check if we need to recalculate soft wraps due to visible area width change.
    int currentVisibleAreaWidth = myEditor.getScrollingModel().getVisibleArea().width;
    if (!indentChanged && myVisibleAreaWidth == currentVisibleAreaWidth) {
      return;
    }

    // Drop information about processed lines then.
    myDirtyRanges.clear();
    myDirtyRanges.add(new TextRange(0, myEditor.getDocument().getTextLength()));
    myStorage.removeAll();
    myVisibleAreaWidth = currentVisibleAreaWidth;
  }

  private IndentType getIndentToUse() {
    return myEditor.getSettings().isUseCustomSoftWrapIndent() ? IndentType.CUSTOM : IndentType.NONE;
  }

  /**
   * Registers given listener within the current manager.
   *
   * @param listener    listener to register
   * @return            <code>true</code> if this collection changed as a result of the call; <code>false</code> otherwise
   */
  public boolean addListener(@NotNull SoftWrapAwareDocumentParsingListener listener) {
    return myListeners.add(listener);
  }

  @SuppressWarnings({"ForLoopReplaceableByForEach"})
  private void revertListeners(int offset, int visualLine) {
    for (int i = 0; i < myListeners.size(); i++) {
      // Avoid unnecessary Iterator object construction as this method is expected to be called frequently.
      SoftWrapAwareDocumentParsingListener listener = myListeners.get(i);
      listener.revertToOffset(offset, visualLine);
    }
  }

  @SuppressWarnings({"ForLoopReplaceableByForEach"})
  private void notifyListenersOnFoldRegion(@NotNull FoldRegion foldRegion, int collapsedFoldingWidthInColumns, int visualLine) {
    for (int i = 0; i < myListeners.size(); i++) {
      // Avoid unnecessary Iterator object construction as this method is expected to be called frequently.
      SoftWrapAwareDocumentParsingListener listener = myListeners.get(i);
      listener.onFoldRegion(foldRegion, collapsedFoldingWidthInColumns, visualLine);
    }
  }

  @SuppressWarnings({"ForLoopReplaceableByForEach"})
  private void notifyListenersOnProcessedSymbol(@NotNull ProcessingContext context) {
    for (int i = 0; i < myListeners.size(); i++) {
      // Avoid unnecessary Iterator object construction as this method is expected to be called frequently.
      SoftWrapAwareDocumentParsingListener listener = myListeners.get(i);
      listener.onProcessedSymbol(context);
    }
  }

  @SuppressWarnings({"ForLoopReplaceableByForEach"})
  private void notifyListenersOnBeforeSoftWrap(@NotNull ProcessingContext context) {
    for (int i = 0; i < myListeners.size(); i++) {
      // Avoid unnecessary Iterator object construction as this method is expected to be called frequently.
      SoftWrapAwareDocumentParsingListener listener = myListeners.get(i);
      listener.beforeSoftWrap(context);
    }
  }

  @SuppressWarnings({"ForLoopReplaceableByForEach"})
  private void notifyListenersOnAfterSoftWrapLineFeed(@NotNull ProcessingContext context) {
    for (int i = 0; i < myListeners.size(); i++) {
      // Avoid unnecessary Iterator object construction as this method is expected to be called frequently.
      SoftWrapAwareDocumentParsingListener listener = myListeners.get(i);
      listener.afterSoftWrapLineFeed(context);
    }
  }

  private class LogicalLineData {
    public int indentInColumns;
    public int indentInPixels;
    public int endLineOffset;

    private int myNonWhiteSpaceSymbolOffset;

    public void update(int logicalLine, int spaceWidth, Editor editor) {
      Document document = myEditor.getDocument();
      int startLineOffset = document.getLineStartOffset(logicalLine);
      endLineOffset = document.getLineEndOffset(logicalLine);
      CharSequence text = document.getCharsSequence();
      indentInColumns = 0;
      indentInPixels = 0;

      for (int i = startLineOffset; i < endLineOffset; i++) {
        char c = text.charAt(i);
        switch (c) {
          case ' ': indentInColumns += 1; indentInPixels += spaceWidth; break;
          case '\t':
            int x = EditorUtil.nextTabStop(indentInPixels, editor);
            indentInColumns = calculateWidthInColumns(x - indentInPixels, spaceWidth);
            indentInPixels = x;
            break;
          default: myNonWhiteSpaceSymbolOffset = i; return;
        }
      }
    }

    /**
     * There is a possible case that all document line symbols before the first soft wrap are white spaces. We don't want to use
     * such a big indent then.
     * <p/>
     * This method encapsulates that 'reset' logical
     *
     * @param softWrapOffset    offset of the soft wrap that occurred on document line which data is stored at the current object
     * @param spaceWidth        space width to use
     */
    public void update(int softWrapOffset, int spaceWidth) {
      if (softWrapOffset > myNonWhiteSpaceSymbolOffset) {
        return;
      }
      if (myCustomIndentUsedLastTime) {
        indentInColumns = myCustomIndentValueUsedLastTime;
        indentInPixels = indentInColumns * spaceWidth;
      }
      else {
        indentInColumns = 0;
        indentInPixels = 0;
      }
    }
  }

  @Override
  public void onFoldRegionStateChange(@NotNull FoldRegion region) {
    //TODO den think about sorting dirty ranges here.
    assert ApplicationManagerEx.getApplicationEx().isDispatchThread();

    Document document = myEditor.getDocument();
    int startLine = document.getLineNumber(region.getStartOffset());
    int endLine = document.getLineNumber(region.getEndOffset());

    int startOffset = document.getLineStartOffset(startLine);
    int endOffset = document.getLineEndOffset(endLine);

    myDirtyRanges.add(new TextRange(startOffset, endOffset));
    recalculateSoftWraps();
  }
}
