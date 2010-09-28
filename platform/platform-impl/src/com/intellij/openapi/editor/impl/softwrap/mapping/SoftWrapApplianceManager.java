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
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
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
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The general idea of soft wraps processing is to build a cache to use for quick document dimensions mapping
 * ({@code 'logical position -> visual position'}, {@code 'offset -> logical position'} etc) and update it incrementally
 * on events like document modification fold region(s) expanding/collapsing etc.
 * <p/>
 * This class encapsulates document parsing logic. It notifies {@link SoftWrapAwareDocumentParsingListener registered listeners}
 * about parsing and they are free to store necessary information for further usage.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Jul 5, 2010 10:01:27 AM
 */
public class SoftWrapApplianceManager implements FoldingListener, DocumentListener {

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

  /**
   * @see #fillOffsetFonts(CharSequence, int, int, int)
   */
  private static final int STORAGE_SEGMENT_SIZE = 100;

  private final List<SoftWrapAwareDocumentParsingListener> myListeners = new ArrayList<SoftWrapAwareDocumentParsingListener>();
  private final List<DirtyRegion> myDirtyRegions = new ArrayList<DirtyRegion>();

  private final Storage myOffset2fontType = new Storage();
  private final Storage myOffset2widthInPixels = new Storage();

  private final SoftWrapsStorage               myStorage;
  private final EditorEx                       myEditor;
  private final SoftWrapPainter myPainter;
  private final EditorTextRepresentationHelper myRepresentationHelper;
  private final SoftWrapDataMapper myDataMapper;

  private VisibleAreaWidthProvider myWidthProvider;
  private LineWrapPositionStrategy myLineWrapPositionStrategy;
  private boolean myCustomIndentUsedLastTime;
  private int myCustomIndentValueUsedLastTime;
  private int myVisibleAreaWidth;
  private long myLastDocumentStamp;
  private boolean myInProgress;

  public SoftWrapApplianceManager(@NotNull SoftWrapsStorage storage,
                                  @NotNull EditorEx editor,
                                  @NotNull SoftWrapPainter painter,
                                  @NotNull EditorTextRepresentationHelper representationHelper, SoftWrapDataMapper dataMapper)
  {
    myStorage = storage;
    myEditor = editor;
    myPainter = painter;
    myRepresentationHelper = representationHelper;
    myDataMapper = dataMapper;
    myWidthProvider = new DefaultVisibleAreaWidthProvider(editor);
  }

  public void registerSoftWrapIfNecessary(@NotNull Rectangle clip, int startOffset) {
    //TODO den    perform full soft wraps recalculation at background thread, calculate soft wraps only for the target
    //TODO den    visible clip at EDT
    recalculateIfNecessary();
  }

  public void release() {
    myDirtyRegions.clear();
    myDirtyRegions.add(new DirtyRegion(0, myEditor.getDocument().getTextLength()));
    myLineWrapPositionStrategy = null;
  }

  private void recalculateSoftWraps() {
    if (myVisibleAreaWidth <= 0 || myDirtyRegions.isEmpty() || !myEditor.getFoldingModel().isFoldingEnabled()) {
      return;
    }

    myLastDocumentStamp = myEditor.getDocument().getModificationStamp();
    myInProgress = true;
    //TODO den think about sorting and merging dirty ranges here.
    try {
      for (DirtyRegion dirtyRegion : myDirtyRegions) {
        recalculateSoftWraps(dirtyRegion);
      }
      myDirtyRegions.clear();
    }
    finally {
      myInProgress = false;
    }
  }

  private void recalculateSoftWraps(DirtyRegion region) {
    region.beforeRecalculation();
    if (region.notifyAboutRecalculationStart) {
      notifyListenersOnRangeRecalculation(region, true);
    }
    myStorage.removeInRange(region.startRange.getStartOffset(), region.startRange.getEndOffset());
    try {
      doRecalculateSoftWraps(region.endRange);
    }
    finally {
      notifyListenersOnRangeRecalculation(region, false);
    }
  }

  @SuppressWarnings({"AssignmentToForLoopParameter"})
  private void doRecalculateSoftWraps(TextRange range) {
    // Preparation.
    myOffset2fontType.clear();
    myOffset2widthInPixels.clear();

    // Define start of the visual line that holds target range start.
    int start = range.getStartOffset();
    int end;
    LogicalPosition logical = myDataMapper.offsetToLogicalPosition(start);
    VisualPosition visual
      = new VisualPosition(myDataMapper.logicalToVisualPosition(logical, myEditor.logicalToVisualPosition(logical, false)).line, 0);
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
    TIntIntHashMap fontType2spaceWidth = new TIntIntHashMap();
    fontType2spaceWidth.put(fontType, spaceWidth);
    int softWrapStartOffset = startLineContext.offset;

    int reservedWidth = myPainter.getMinDrawingWidth(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED);
    SoftWrap delayedSoftWrap = null;

    // Perform soft wraps calculation.
    outer:
    while (!iterationState.atEnd() && start <= range.getEndOffset()) {
      FoldRegion currentFold = iterationState.getCurrentFold();
      if (currentFold != null) {
        String placeholder = currentFold.getPlaceholderText();
        FontInfo fontInfo = EditorUtil.fontForChar(placeholder.charAt(0), fontType, myEditor);
        newX = context.x;
        for (int i = 0; i < placeholder.length(); i++) {
          newX += fontInfo.charWidth(placeholder.charAt(i), contentComponent);
        }
        if (newX + reservedWidth >= myVisibleAreaWidth) {
          logicalLineData.update(currentFold.getStartOffset(), spaceWidth);
          SoftWrap softWrap = registerSoftWrap(softWrapStartOffset, start, start, spaceWidth, logicalLineData);
          assert softWrap != null; // We expect that it's always possible to wrap collapsed fold region placeholder text
          softWrapStartOffset = softWrap.getStart();
          if (softWrap.getStart() < start) {
            revertListeners(softWrap.getStart(), context.visualLine);
            for (int j = currentFold.getStartOffset() - 1; j >= softWrap.getStart(); j--) {
              int pixelsDiff = myOffset2widthInPixels.data[j - myOffset2widthInPixels.anchor];
              int tmpFontType = myOffset2fontType.data[j - myOffset2fontType.anchor];
              int columnsDiff = calculateWidthInColumns(text.charAt(j), pixelsDiff, fontType2spaceWidth.get(tmpFontType));
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
            fontType = myOffset2fontType.data[j - myOffset2fontType.anchor];
            newX = calculateNewX(context, fontType, contentComponent);
            processSymbol(context, startLineContext, logicalLineData, fontType, newX, fontType2spaceWidth);
          }
          myOffset2fontType.clear();
          myOffset2widthInPixels.clear();
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
          softWrapStartOffset = currentFold.getEndOffset();
        }
      }

      end = iterationState.getEndOffset();
      fillOffsetFonts(text, fontType, start, end);
      for (int i = start; i < end; i++) {
        if (i >= myOffset2fontType.end || myOffset2fontType.data[i - myOffset2fontType.anchor] <= 0) {
          fillOffsetFonts(text, fontType, i, end);
        }
        if (i > range.getEndOffset()) {
          break outer;
        }
        char c = text.charAt(i);
        int tmpFontType = myOffset2fontType.data[i - myOffset2fontType.anchor];
        if (tmpFontType > 0) {
          fontType = tmpFontType;
        }
        context.symbol = c;

        if (delayedSoftWrap != null && delayedSoftWrap.getStart() == i) {
          processSoftWrap(delayedSoftWrap, context);
          softWrapStartOffset = delayedSoftWrap.getStart();
          startLineContext.from(context);
          delayedSoftWrap = null;
        }

        if (c == '\n') {
          processSymbol(context, startLineContext, logicalLineData, fontType, 0, fontType2spaceWidth);
          softWrapStartOffset = startLineContext.offset;
          continue;
        }

        if (myOffset2widthInPixels.end > context.offset && myOffset2widthInPixels.data[context.offset - myOffset2widthInPixels.anchor] > 0
            && context.symbol != '\t'/*we need to recalculate tabulation width after soft wrap*/)
        {
          newX = context.x + myOffset2widthInPixels.data[context.offset - myOffset2widthInPixels.anchor];
        }
        else {
          newX = calculateNewX(context, fontType, contentComponent);
        }

        if (newX + reservedWidth >= myVisibleAreaWidth) {
          logicalLineData.update(i, spaceWidth);
          SoftWrap softWrap = registerSoftWrap(
            softWrapStartOffset, Math.max(softWrapStartOffset, i - 1),
            calculateSoftWrapEndOffset(softWrapStartOffset, logicalLineData.endLineOffset), spaceWidth, logicalLineData
          );
          if (softWrap == null) {
            processSymbol(context, startLineContext, logicalLineData, fontType, newX, fontType2spaceWidth);
            continue;
          }
          int newI = softWrap.getStart();

          // There are three possible options:
          //   1. Soft wrap offset is located before the current offset;
          //   2. Soft wrap offset is located after the current offset but doesn't exceed current token end offset
          //      (it may occur if there are no convenient wrap positions before the current offset);
          //   3. Soft wrap offset is located after the current offset and exceeds current token end offset;
          // We should process that accordingly.
          if (newI > end) {
            delayedSoftWrap = softWrap;
            processSymbol(context, startLineContext, logicalLineData, fontType, newX, fontType2spaceWidth);
            continue;
          }
          else if (newI < i) {
            revertListeners(newI, context.visualLine);
            for (int j = i - 1; j >= newI; j--) {
              int pixelsDiff = myOffset2widthInPixels.data[j - myOffset2widthInPixels.anchor];
              tmpFontType = myOffset2fontType.data[j - myOffset2fontType.anchor];
              int columnsDiff = calculateWidthInColumns(text.charAt(j), pixelsDiff, fontType2spaceWidth.get(tmpFontType));
              context.offset--;
              context.logicalColumn -= columnsDiff;
              context.visualColumn -= columnsDiff;
            }
          }
          else if (newI > i) {
            processSymbol(context, startLineContext, logicalLineData, fontType, newX, fontType2spaceWidth);
            for (int j = i + 1; j < newI; j++) {
              context.symbol = text.charAt(j);
              newX = calculateNewX(context, fontType, contentComponent);
              processSymbol(context, startLineContext, logicalLineData, fontType, newX, fontType2spaceWidth);
            }
          }

          processSoftWrap(softWrap, context);
          softWrapStartOffset = newI;
          i = newI - 1/* because of loop increment */;
          startLineContext.from(context);
          myOffset2fontType.clear();
          myOffset2widthInPixels.clear();
        }
        else {
          processSymbol(context, startLineContext, logicalLineData, fontType, newX, fontType2spaceWidth);
        }
      }

      iterationState.advance();
      attributes = iterationState.getMergedAttributes();
      fontType = attributes.getFontType();
      start = iterationState.getStartOffset();
    }
  }

  /**
   * We need to be able to track back font types to offsets mappings because text processing may be shifted back because of soft wrap.
   * <p/>
   * <b>Example</b>
   * Suppose with have this line of text that should be soft-wrapped
   * <pre>
   *                       | &lt;- right margin
   *     token1 token2-toke|n3
   *                       | &lt;- right margin
   * </pre>
   * It's possible that <code>'token1'</code>, white spaces and <code>'token2'</code> use different font types and
   * soft wrapping should be performed between <code>'token1'</code> and <code>'token2'</code>. We need to be able to
   * match offsets of <code>'token2'</code> to font types then.
   * <p/>
   * There is an additional trick here - there is a possible case that a bunch number of adjacent symbols use the same font
   * type (are marked by {@link IterationState} as a single token. That is often the case for plain text). We don't want to
   * store those huge mappings then (it may take over million records) because it's indicated by profiling as extremely expensive
   * and causing unnecessary garbage collections that dramatically reduce overall application throughput.
   * <p/>
   * Hence, we want to restrict ourselves by storing information about particular sub-sequence of overall token offsets.
   * <p/>
   * This method encapsulates that logic.
   *
   * @param text              target text
   * @param fontType          font type used within <code>[start; end)</code> offset
   * @param start             start offset of the symbol that uses given font type (inclusive)
   * @param end               end offset of the symbol that uses given font type (exclusive)
   */
  private void fillOffsetFonts(CharSequence text, int fontType, int start, int end) {
    int newLength = start - end;
    if (myOffset2fontType.anchor > 0) {
      newLength += myOffset2fontType.end;
    }
    else {
      myOffset2fontType.anchor = start;
    }

    if (newLength > myOffset2fontType.data.length) {
      int[] newData = new int[newLength];
      System.arraycopy(myOffset2fontType.data, 0, newData, 0, myOffset2fontType.end);
      myOffset2fontType.data = newData;
    }


    for (int i = start, counter = 0; i < end; i++, counter++) {
      myOffset2fontType.data[start - myOffset2fontType.anchor] = fontType;
      char c = text.charAt(i);
      if (c == '\n' || counter >= STORAGE_SEGMENT_SIZE) {
        myOffset2fontType.end = i - myOffset2fontType.anchor;
        return;
      }
    }
    myOffset2fontType.end = end - myOffset2fontType.anchor;
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
                             int fontType, int newX, TIntIntHashMap fontType2spaceWidth)
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

      myOffset2fontType.clear();
      myOffset2widthInPixels.clear();
      //clear(offset2fontType);
      //clear(offset2widthInPixels);
      startLineContext.from(context);
      logicalLineData.update(context.logicalLine, spaceWidth, myEditor);
      context.x = 0;
      return;
    }

    context.symbolWidthInPixels = newX - context.x;
    context.symbolWidthInColumns = calculateWidthInColumns(context.symbol, context.symbolWidthInPixels, spaceWidth);
    notifyListenersOnProcessedSymbol(context);
    context.visualColumn += context.symbolWidthInColumns;
    context.logicalColumn += context.symbolWidthInColumns;
    context.x = newX;
    if (myOffset2widthInPixels.anchor <= 0) {
      myOffset2widthInPixels.anchor = context.offset;
    }
    myOffset2widthInPixels.data[context.offset - myOffset2widthInPixels.anchor] = context.symbolWidthInPixels;
    myOffset2widthInPixels.end++;
    context.offset++;
  }

  private static int calculateWidthInColumns(char c, int widthInPixels, int spaceWithInPixels) {
    if (c != '\t') {
      return 1;
    }
    int result = widthInPixels / spaceWithInPixels;
    if (widthInPixels % spaceWithInPixels > 0) {
      result++;
    }
    return result;
  }

  /**
   * This method is assumed to be called in situation when visible area width is exceeded. It tries to create and register
   * new soft wrap which data is defined in accordance with the given parameters.
   * <p/>
   * There is a possible case that no soft wrap is created and registered. That is true, for example, for situation when
   * we have a long line of text that doesn't contain white spaces, operators or any other symbols that may be used
   * as a <code>'wrap points'</code>. We just left such lines as-is.
   *
   * @param minOffset         min line <code>'wrap point'</code> offset
   * @param preferredOffset   preferred <code>'wrap point'</code> offset, i.e. max offset which symbol doesn't exceed right margin
   * @param maxOffset         max line <code>'wrap point'</code> offset
   * @param spaceSize         current space width in pixels
   * @param lineData          object that encapsulates information about currently processed logical line
   * @return                  newly created and registered soft wrap if any; <code>null</code> otherwise
   */
  @Nullable
  private SoftWrap registerSoftWrap(int minOffset, int preferredOffset, int maxOffset, int spaceSize, LogicalLineData lineData) {
    Document document = myEditor.getDocument();

    // Performance optimization implied by profiling results analysis.
    if (myLineWrapPositionStrategy == null) {
      myLineWrapPositionStrategy = LanguageLineWrapPositionStrategy.INSTANCE.forEditor(myEditor);
    }
    int softWrapOffset = myLineWrapPositionStrategy.calculateWrapPosition(
      document.getCharsSequence(), minOffset, maxOffset, preferredOffset, true
    );
    if (softWrapOffset >= lineData.endLineOffset) {
      return null;
    }

    int indentInColumns = 0;
    int indentInPixels = myPainter.getMinDrawingWidth(SoftWrapDrawingType.AFTER_SOFT_WRAP);
    if (myCustomIndentUsedLastTime) {
      indentInColumns = myCustomIndentValueUsedLastTime + lineData.indentInColumns;
      indentInPixels += lineData.indentInPixels + (myCustomIndentValueUsedLastTime * spaceSize);
    }
    SoftWrapImpl softWrap = new SoftWrapImpl(
      new TextChangeImpl("\n" + StringUtil.repeatSymbol(' ', indentInColumns), softWrapOffset, softWrapOffset),
      indentInColumns + 1/* for 'after soft wrap' drawing */,
      indentInPixels
    );
    myStorage.storeOrReplace(softWrap, true);
    return softWrap;
  }

  private void processSoftWrap(SoftWrap softWrap, ProcessingContext context) {
    notifyListenersOnBeforeSoftWrap(context);

    context.visualColumn = 0;
    context.softWrapColumnDiff = context.visualColumn - context.foldingColumnDiff - context.logicalColumn;
    context.softWrapLinesCurrent++;
    context.visualLine++;
    notifyListenersOnAfterSoftWrapLineFeed(context);

    context.x = softWrap.getIndentInPixels();
    context.visualColumn = softWrap.getIndentInColumns();
    context.softWrapColumnDiff += softWrap.getIndentInColumns();
  }

  public void recalculateIfNecessary() {
    recalculateIfNecessary(myEditor.getDocument().getModificationStamp());
  }

  /**
   * There is a possible case that we need to reparse the whole document (e.g. visible area width is changed or user-defined
   * soft wrap indent is changed etc). This method encapsulates that logic, i.e. it checks if necessary conditions are satisfied
   * and updates internal state as necessary.
   *
   * @param documentStamp     document modification stamp to use if document was changed while soft wrapping was off
   */
  public void recalculateIfNecessary(long documentStamp) {
    if (myInProgress) {
      return;
    }

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
    int currentVisibleAreaWidth = myWidthProvider.getVisibleAreaWidth();
    if (!indentChanged && myVisibleAreaWidth == currentVisibleAreaWidth && documentStamp == myLastDocumentStamp) {
      recalculateSoftWraps(); // Recalculate existing dirty regions if any.
      return;
    }

    // Drop information about processed lines then.
    myDirtyRegions.clear();
    myDirtyRegions.add(new DirtyRegion(0, myEditor.getDocument().getTextLength() - 1));
    myStorage.removeAll();
    myVisibleAreaWidth = currentVisibleAreaWidth;
    recalculateSoftWraps();
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
      listener.onCollapsedFoldRegion(foldRegion, collapsedFoldingWidthInColumns, visualLine);
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

  @SuppressWarnings({"ForLoopReplaceableByForEach"})
  private void notifyListenersOnRangeRecalculation(DirtyRegion region, boolean start) {
    for (int i = 0; i < myListeners.size(); i++) {
      // Avoid unnecessary Iterator object construction as this method is expected to be called frequently.
      SoftWrapAwareDocumentParsingListener listener = myListeners.get(i);
      if (start) {
        listener.onRecalculationStart(region.startRange.getStartOffset(), region.startRange.getEndOffset());
      }
      else {
        listener.onRecalculationEnd(region.endRange.getStartOffset(), region.endRange.getEndOffset());
      }
    }
  }

  @Override
  public void onFoldRegionStateChange(@NotNull FoldRegion region) {
    assert ApplicationManagerEx.getApplicationEx().isDispatchThread();

    Document document = myEditor.getDocument();
    int startLine = document.getLineNumber(region.getStartOffset());
    int endLine = document.getLineNumber(region.getEndOffset());

    int startOffset = document.getLineStartOffset(startLine);
    int endOffset = document.getLineEndOffset(endLine);

    myDirtyRegions.add(new DirtyRegion(startOffset, endOffset));
  }

  @Override
  public void onFoldProcessingEnd() {
    recalculateSoftWraps();
  }

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
    DirtyRegion region = new DirtyRegion(event);
    myDirtyRegions.add(region);
    notifyListenersOnRangeRecalculation(region, true);
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    recalculateIfNecessary(event.getOldTimeStamp());
  }

  /**
   * The general idea of soft wraps processing is to build a cache during complete document parsing and update it incrementally
   * on events like document modification, fold region expanding/collapsing etc.
   * <p/>
   * This class encapsulates information about document region to be recalculated.
   */
  private class DirtyRegion {

    public TextRange startRange;
    public TextRange endRange;
    public boolean notifyAboutRecalculationStart;
    private boolean myRecalculateEnd;

    DirtyRegion(int startOffset, int endOffset) {
      startRange = new TextRange(startOffset, endOffset);
      endRange = new TextRange(startOffset, endOffset);
      notifyAboutRecalculationStart = true;
    }

    DirtyRegion(DocumentEvent event) {
      Document document = event.getDocument();
      int startLine = document.getLineNumber(event.getOffset());
      int oldEndLine = document.getLineNumber(event.getOffset() + event.getOldLength());
      startRange = new TextRange(document.getLineStartOffset(startLine), document.getLineEndOffset(oldEndLine));
      endRange = new TextRange(event.getOffset(), event.getOffset() + event.getNewLength());
      myRecalculateEnd = true;
    }

    public void beforeRecalculation() {
      if (!myRecalculateEnd) {
        return;
      }
      Document document = myEditor.getDocument();
      int startLine = document.getLineNumber(endRange.getStartOffset());
      int endLine = document.getLineNumber(endRange.getEndOffset());
      int endOffset = document.getLineEndOffset(endLine);
      int textLength = document.getTextLength();
      if (textLength > 0 && endOffset >= textLength) {
        endOffset = textLength - 1;
      }
      endRange = new TextRange(document.getLineStartOffset(startLine), endOffset);
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
            indentInColumns += calculateWidthInColumns(c, x - indentInPixels, spaceWidth);
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

  public void setWidthProvider(VisibleAreaWidthProvider widthProvider) {
    myWidthProvider = widthProvider;
  }

  /**
   * This interface is introduced mostly for encapsulating GUI-specific values retrieval and make it possible to write
   * tests for soft wraps processing.
   */
  public interface VisibleAreaWidthProvider {
    int getVisibleAreaWidth();
  }

  private static class DefaultVisibleAreaWidthProvider implements VisibleAreaWidthProvider {

    private final Editor myEditor;

    DefaultVisibleAreaWidthProvider(Editor editor) {
      myEditor = editor;
    }

    @Override
    public int getVisibleAreaWidth() {
      return myEditor.getScrollingModel().getVisibleArea().width;
    }
  }

  /**
   * Primitive array-based data structure that contain mappings like {@code int -> int}.
   * <p/>
   * The key is array index plus anchor; the value is array value.
   */
  private static class Storage {
    public int[] data = new int[256];
    public int anchor;
    public int end;

    public void clear() {
      anchor = 0;
      end = 0;
      Arrays.fill(data, 0);
    }
  }
}
