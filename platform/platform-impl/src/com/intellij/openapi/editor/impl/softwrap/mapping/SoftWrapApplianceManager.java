/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.diagnostic.Dumpable;
import com.intellij.diagnostic.LogMessageEx;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.ScrollingModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.*;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapPainter;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapsStorage;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.text.StringUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.*;
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
public class SoftWrapApplianceManager implements Dumpable {
  
  private static final Logger LOG = Logger.getInstance("#" + SoftWrapApplianceManager.class.getName());
  
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

  private final List<SoftWrapAwareDocumentParsingListener> myListeners            = new ArrayList<>();
  private final ProcessingContext                          myContext              = new ProcessingContext();
  private final FontTypesStorage                           myOffset2fontType      = new FontTypesStorage();
  private final WidthsStorage                              myOffset2widthInPixels = new WidthsStorage();

  private final SoftWrapsStorage               myStorage;
  private final EditorImpl                     myEditor;
  private       SoftWrapPainter                myPainter;
  private final CachingSoftWrapDataMapper      myDataMapper;

  /**
   * Visual area width change causes soft wraps addition/removal, so, we want to update <code>'y'</code> coordinate
   * of the editor viewport then. For example, we observe particular text region at the 'vcs diff' control and change
   * its width. We would like to see the same text range at the viewport then.
   * <p/>
   * This field holds offset of the text range that is shown at the top-left viewport position. It's used as an anchor
   * during viewport's <code>'y'</code> coordinate adjustment on visual area width change.
   */
  private int myLastTopLeftCornerOffset = 0;
  private int myVerticalScrollBarWidth  = -1;

  private VisibleAreaWidthProvider       myWidthProvider;
  private LineWrapPositionStrategy       myLineWrapPositionStrategy;
  private IncrementalCacheUpdateEvent    myEventBeingProcessed;
  private boolean                        myCustomIndentUsedLastTime;
  private int                            myCustomIndentValueUsedLastTime;
  private int                            myVisibleAreaWidth;
  private boolean                        myInProgress;
  private boolean                        myIsDirty = true;
  private IncrementalCacheUpdateEvent    myDocumentChangedEvent;


  public SoftWrapApplianceManager(@NotNull SoftWrapsStorage storage,
                                  @NotNull EditorImpl editor,
                                  @NotNull SoftWrapPainter painter,
                                  CachingSoftWrapDataMapper dataMapper)
  {
    myStorage = storage;
    myEditor = editor;
    myPainter = painter;
    myDataMapper = dataMapper;
    myWidthProvider = new DefaultVisibleAreaWidthProvider(editor);
    myEditor.getScrollingModel().addVisibleAreaListener(e -> updateLastTopLeftCornerOffset());
  }

  public void registerSoftWrapIfNecessary() {
    recalculateIfNecessary();
  }

  public void reset() {
    myIsDirty = true;
    for (SoftWrapAwareDocumentParsingListener listener : myListeners) {
      listener.reset();
    }
  }
  
  public void release() {
    myLineWrapPositionStrategy = null;
  }

  public void recalculate(IncrementalCacheUpdateEvent e) {
    if (myIsDirty) {
      return;
    }
    if (myVisibleAreaWidth <= 0) {
      myIsDirty = true;
      return;
    }

    recalculateSoftWraps(e);

    onRecalculationEnd();
  }
  
  public void recalculate(List<? extends Segment> ranges) {
    if (myIsDirty) {
      return;
    }
    if (myVisibleAreaWidth <= 0) {
      myIsDirty = true; 
      return;
    }

    Collections.sort(ranges, new Comparator<Segment>() { 
      @Override
      public int compare(Segment o1, Segment o2) {
        int startDiff = o1.getStartOffset() - o2.getStartOffset();
        return startDiff == 0 ? o2.getEndOffset() - o1.getEndOffset() : startDiff;
      }
    });
    final int[] lastRecalculatedOffset = new int[] {0};
    SoftWrapAwareDocumentParsingListenerAdapter listener = new SoftWrapAwareDocumentParsingListenerAdapter() {
      @Override
      public void onRecalculationEnd(@NotNull IncrementalCacheUpdateEvent event) {
        lastRecalculatedOffset[0] = event.getActualEndOffset();
      }
    };
    myListeners.add(listener);
    try {
      for (Segment range : ranges) {
        int lastOffset = lastRecalculatedOffset[0];
        if (range.getEndOffset() > lastOffset) {
          recalculateSoftWraps(new IncrementalCacheUpdateEvent(Math.max(range.getStartOffset(), lastOffset), range.getEndOffset(),
                                                               myDataMapper, myEditor));
        }
      }
    }
    finally {
      myListeners.remove(listener);
    }

    onRecalculationEnd();
  }
  
  /**
   * @return    <code>true</code> if soft wraps were really re-calculated;
   *            <code>false</code> if it's not possible to do at the moment (e.g. current editor is not shown and we don't
   *            have information about viewport width)
   */
  private boolean recalculateSoftWraps() {
    if (!myIsDirty) {
      return true;
    }
    if (myVisibleAreaWidth <= 0) {
      return false;
    }
    myIsDirty = false;

    recalculateSoftWraps(new IncrementalCacheUpdateEvent(myEditor.getDocument()));
    
    onRecalculationEnd();
    
    return true;
  }
  
  private void onRecalculationEnd() {
    updateLastTopLeftCornerOffset();
    for (SoftWrapAwareDocumentParsingListener listener : myListeners) {
      listener.recalculationEnds();
    }
  }

  private void recalculateSoftWraps(@NotNull IncrementalCacheUpdateEvent event) {
    if (myEditor.getDocument() instanceof DocumentImpl && ((DocumentImpl)myEditor.getDocument()).acceptsSlashR()) {
      LOG.error("Soft wrapping is not supported for documents with non-standard line endings. File: " + myEditor.getVirtualFile());
    }
    if (myInProgress) {
      LogMessageEx.error(LOG, "Detected race condition at soft wraps recalculation", myEditor.dumpState(), event.toString());
    }
    myInProgress = true;
    try {
      doRecalculateSoftWraps(event);
    }
    finally {
      myInProgress = false;
    }
  }
  
  private void doRecalculateSoftWraps(IncrementalCacheUpdateEvent event) {
    myEventBeingProcessed = event;
    notifyListenersOnCacheUpdateStart(event);
    // Preparation.
    myContext.reset();
    myOffset2fontType.clear();
    myOffset2widthInPixels.clear();

    // Define start of the visual line that holds target range start.
    final int start = event.getStartOffset(); 
    final LogicalPosition logical = event.getStartLogicalPosition();

    Document document = myEditor.getDocument();
    myContext.text = document.getCharsSequence();
    myContext.tokenStartOffset = start;
    IterationState iterationState = new IterationState(myEditor, start, document.getTextLength(), false);
    TextAttributes attributes = iterationState.getMergedAttributes();
    myContext.fontType = attributes.getFontType();
    myContext.rangeEndOffset = event.getMandatoryEndOffset();

    EditorPosition position = myEditor.myUseNewRendering ? new EditorPosition(logical, event.getStartVisualPosition(), start, myEditor) : 
                              new EditorPosition(logical, start, myEditor);
    position.x = start == 0 ? myEditor.getPrefixTextWidthInPixels() : 0;
    int spaceWidth = EditorUtil.getSpaceWidth(myContext.fontType, myEditor);
    int plainSpaceWidth = EditorUtil.getSpaceWidth(Font.PLAIN, myEditor);

    myContext.logicalLineData.update(logical.line, spaceWidth, plainSpaceWidth);

    myContext.currentPosition = position;
    myContext.lineStartPosition = position.clone();
    myContext.fontType2spaceWidth.put(myContext.fontType, spaceWidth);
    myContext.softWrapStartOffset = position.offset;

    myContext.reservedWidthInPixels = myPainter.getMinDrawingWidth(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED);
    
    SoftWrap softWrapAtStartPosition = myStorage.getSoftWrap(start);
    if (softWrapAtStartPosition != null) {
      myContext.currentPosition.x = softWrapAtStartPosition.getIndentInPixels();
      myContext.lineStartPosition.visualColumn = 0;
      myContext.lineStartPosition.softWrapColumnDiff -= softWrapAtStartPosition.getIndentInColumns();
      myContext.softWrapStartOffset++;
      
      notifyListenersOnVisualLineStart(myContext.lineStartPosition);
    }

    // Perform soft wraps calculation.
    while (!iterationState.atEnd()) {
      FoldRegion currentFold = iterationState.getCurrentFold();
      if (currentFold == null) {
        myContext.tokenEndOffset = iterationState.getEndOffset();
        if (processNonFoldToken()) {
          break;
        }
      }
      else {
        if (processCollapsedFoldRegion(currentFold)) {
          break;
        }

        // 'myOffset2widthInPixels' contains information necessary to processing soft wraps that lay before the current offset.
        // We do know that soft wraps are not allowed to go backward after processed collapsed fold region, hence, we drop
        // information about processed symbols width.
        myOffset2widthInPixels.clear();
      }

      iterationState.advance();
      attributes = iterationState.getMergedAttributes();
      myContext.fontType = attributes.getFontType();
      myContext.tokenStartOffset = iterationState.getStartOffset();
      myOffset2fontType.fill(myContext.tokenStartOffset, iterationState.getEndOffset(), myContext.fontType);
    }
    if (myContext.delayedSoftWrap != null) {
      myStorage.remove(myContext.delayedSoftWrap);
    }
    notifyListenersOnVisualLineEnd();
    event.setActualEndOffset(myContext.currentPosition.offset);
    validateFinalPosition(event);
    notifyListenersOnCacheUpdateEnd(event);
    myEventBeingProcessed = null;
  }

  private void validateFinalPosition(IncrementalCacheUpdateEvent event) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Soft wrap recalculation done: " + event.toString() + ". " + (event.getActualEndOffset() - event.getStartOffset()) + " characters processed");
    }
    int endOffsetUpperEstimate = EditorUtil.getNotFoldedLineEndOffset(myEditor, event.getMandatoryEndOffset());
    int line = myEditor.getDocument().getLineNumber(endOffsetUpperEstimate);
    if (line < myEditor.getDocument().getLineCount() - 1) {
      endOffsetUpperEstimate = myEditor.getDocument().getLineStartOffset(line + 1);
    }
    if (event.getActualEndOffset() > endOffsetUpperEstimate) {
      LOG.error("Unexpected error at soft wrap recalculation", new Attachment("softWrapModel.txt", myEditor.getSoftWrapModel().toString()));
    }
  }

  /**
   * Encapsulates logic of processing given collapsed fold region.
   *
   * @param foldRegion    target collapsed fold region to process
   * @return <code>true</code> if no further calculation is required
   */
  private boolean processCollapsedFoldRegion(FoldRegion foldRegion) {
    Document document = myEditor.getDocument();
    if (!foldRegion.isValid() ||
        foldRegion.getStartOffset() != myContext.tokenStartOffset
        || foldRegion.getEndOffset() > document.getTextLength()) {
      LOG.error("Inconsistent fold region state: fold region: " + foldRegion 
                + ", soft wrap model state: " + myEditor.getSoftWrapModel() 
                + ", folding model state: " + myEditor.getFoldingModel()); 
      return true;
    }

    String placeholder = foldRegion.getPlaceholderText();
    int placeholderWidthInPixels = 0;
    for (int i = 0; i < placeholder.length(); i++) {
      char c = placeholder.charAt(i);
      if (myEditor.myUseNewRendering && c == '\n') c = ' '; // we display \n as space (see com.intellij.openapi.editor.impl.view.EditorView.getFoldRegionLayout)
      placeholderWidthInPixels += SoftWrapModelImpl.getEditorTextRepresentationHelper(myEditor).charWidth(c, myContext.fontType);
    }

    if (myContext.delayedSoftWrap == null) {
      int newX = myContext.currentPosition.x + placeholderWidthInPixels;

      notifyListenersOnVisualLineStart(myContext.lineStartPosition);

      if (!myContext.exceedsVisualEdge(newX) || myContext.currentPosition.offset == myContext.lineStartPosition.offset) {
        myContext.advance(foldRegion, placeholderWidthInPixels);
        return false;
      }
    }

    myContext.logicalLineData.update(foldRegion.getStartOffset());
    
    SoftWrap softWrap = null;
    if (myContext.delayedSoftWrap == null && myContext.exceedsVisualEdge(myContext.currentPosition.x + myContext.reservedWidthInPixels)) {
      softWrap = registerSoftWrap(
        myContext.softWrapStartOffset, myContext.tokenStartOffset, myContext.tokenStartOffset, myContext.getSpaceWidth(),
        myContext.logicalLineData
      );
    }

    if (myContext.delayedSoftWrap != null) {
      myStorage.remove(myContext.delayedSoftWrap);
      myContext.delayedSoftWrap = null;
    }
    
    if (softWrap == null) {
      // If we're here that means that we can't find appropriate soft wrap offset before the fold region.
      // However, we expect that it's always possible to wrap collapsed fold region placeholder text
      softWrap = registerSoftWrap(foldRegion.getStartOffset(), myContext.getSpaceWidth(), myContext.logicalLineData);
    }
    myContext.softWrapStartOffset = softWrap.getStart();
    if (softWrap.getStart() < myContext.tokenStartOffset) {
      revertListeners(softWrap.getStart(), myContext.currentPosition.visualLine);
      for (int j = foldRegion.getStartOffset() - 1; j >= softWrap.getStart(); j--) {
        int pixelsDiff = myOffset2widthInPixels.data[j - myOffset2widthInPixels.anchor];
        int columnsDiff = calculateWidthInColumns(myContext.text.charAt(j), pixelsDiff, myContext.getPlainSpaceWidth());
        myContext.currentPosition.offset--;
        myContext.currentPosition.logicalColumn -= columnsDiff;
        myContext.currentPosition.visualColumn -= columnsDiff;
      }
    }
    notifyListenersOnSoftWrapLineFeed(true);

    myContext.currentPosition.visualColumn = 0;
    myContext.currentPosition.softWrapColumnDiff = myContext.currentPosition.visualColumn - myContext.currentPosition.foldingColumnDiff 
                                                   - myContext.currentPosition.logicalColumn;
    myContext.currentPosition.softWrapLinesCurrent++;
    myContext.currentPosition.visualLine++;
    notifyListenersOnSoftWrapLineFeed(false);

    myContext.currentPosition.x = softWrap.getIndentInPixels();
    myContext.currentPosition.visualColumn = softWrap.getIndentInColumns();
    myContext.currentPosition.softWrapColumnDiff += softWrap.getIndentInColumns();
    
    myContext.clearLastFoldInfo();
    myContext.skipToLineEnd = false;

    if (checkIsDoneAfterSoftWrap()) {
      return true;
    }

    for (int j = softWrap.getStart(); j < myContext.tokenStartOffset; j++) {
      char c = myContext.text.charAt(j);
      int newX = calculateNewX(c);
      myContext.onNonLineFeedSymbol(c, newX);
    }
    myOffset2fontType.clear();
    myContext.advance(foldRegion, placeholderWidthInPixels);
    
    return false;
  }
  
  /**
   * Encapsulates logic of processing target non-fold region token defined by the {@link #myContext current processing context}
   * (target token start offset is identified by {@link ProcessingContext#tokenStartOffset}; end offset is stored
   * at {@link ProcessingContext#tokenEndOffset}).
   * <p/>
   * <code>'Token'</code> here stands for the number of subsequent symbols that are represented using the same font by IJ editor.
   * 
   * @return <code>true</code> if no further calculation is required
   */
  private boolean processNonFoldToken() {
    int limit = 3 * (myContext.tokenEndOffset - myContext.lineStartPosition.offset);
    int counter = 0;
    int startOffset = myContext.currentPosition.offset;
    while (myContext.currentPosition.offset < myContext.tokenEndOffset) {
      if (counter++ > limit) {
        LogMessageEx.error(LOG, "Cycled soft wraps recalculation detected", String.format(
          "Start recalculation offset: %d, visible area width: %d, calculation context: %s, editor info: %s",
          startOffset, myVisibleAreaWidth, myContext, myEditor.dumpState()));
        for (int i = myContext.currentPosition.offset; i < myContext.tokenEndOffset; i++) {
          char c = myContext.text.charAt(i);
          if (c == '\n') {
            myContext.onNewLine();
            if (checkIsDoneAfterNewLine()) {
              return true;
            }
          }
          else {
            myContext.onNonLineFeedSymbol(c);
          }
        }
        return false;
      }
      int offset = myContext.currentPosition.offset;

      if (myContext.delayedSoftWrap != null && myContext.delayedSoftWrap.getStart() == offset) {
        processSoftWrap(myContext.delayedSoftWrap);
        myContext.delayedSoftWrap = null;
        if (checkIsDoneAfterSoftWrap()) {
          return true;
        }
      }

      char c = myContext.text.charAt(offset);
      if (c == '\n') {
        myContext.onNewLine();
        if (checkIsDoneAfterNewLine()) {
          return true;
        }
        continue;
      }

      if (myContext.skipToLineEnd) {
        myContext.skipToLineEnd = false; // Assuming that this flag is set if no soft wrap is registered during processing the call below
        if (createSoftWrapIfPossible()) {
          return true;
        }
        continue;
      }

      int newX = offsetToX(offset, c);
      if (myContext.exceedsVisualEdge(newX) && myContext.delayedSoftWrap == null) {
        if (createSoftWrapIfPossible()) {
          return true;
        }
      }
      else {
        myContext.onNonLineFeedSymbol(c, newX);
      }
    }
    return false;
  }
  
  private boolean checkIsDoneAfterNewLine() {
    return myContext.currentPosition.offset > myContext.rangeEndOffset;
  }

  private boolean checkIsDoneAfterSoftWrap() {
    SoftWrapImpl lastSoftWrap = myDataMapper.getLastSoftWrap();
    LOG.assertTrue(lastSoftWrap != null);
    if (myContext.currentPosition.offset > myContext.rangeEndOffset 
           && myDataMapper.matchesOldSoftWrap(lastSoftWrap, myEventBeingProcessed.getLengthDiff())) {
      myDataMapper.removeLastCacheEntry();
      return true;
    }
    return false;
  }

  /**
   * Allows to retrieve 'x' coordinate of the right edge of document symbol referenced by the given offset. 
   * 
   * @param offset    target symbol offset
   * @param c         target symbol referenced by the given offset
   * @return          'x' coordinate of the right edge of document symbol referenced by the given offset
   */
  private int offsetToX(int offset, char c) {
    if (myOffset2widthInPixels.end > offset
        && (myOffset2widthInPixels.anchor + myOffset2widthInPixels.end > offset)
        && myContext.currentPosition.symbol != '\t'/*we need to recalculate tabulation width after soft wrap*/)
    {
      return myContext.currentPosition.x + myOffset2widthInPixels.data[offset - myOffset2widthInPixels.anchor];
    }
    else {
      return calculateNewX(c);
    }
  }
  
  private boolean createSoftWrapIfPossible() {
    final int offset = myContext.currentPosition.offset;
    myContext.logicalLineData.update(offset);
    int softWrapStartOffset = myContext.softWrapStartOffset;
    int preferredOffset = Math.max(softWrapStartOffset, offset - 1 /* reserve a column for the soft wrap sign */);
    SoftWrapImpl softWrap = registerSoftWrap(
      softWrapStartOffset,
      preferredOffset,
      myContext.logicalLineData.endLineOffset,
      myContext.getSpaceWidth(),
      myContext.logicalLineData
    );
    FoldRegion revertedToFoldRegion = null;
    if (softWrap == null) {
      EditorPosition wrapPosition = null;
      
      // Try to insert soft wrap after the last collapsed fold region that is located on the current visual line.
      if (myContext.lastFoldEndPosition != null && myStorage.getSoftWrap(myContext.lastFoldEndPosition.offset) == null) {
        wrapPosition = myContext.lastFoldEndPosition;
      }

      if (wrapPosition == null && myContext.lastFoldStartPosition != null
          && myStorage.getSoftWrap(myContext.lastFoldStartPosition.offset) == null
          && myContext.lastFoldStartPosition.offset < myContext.currentPosition.offset)
      {
        wrapPosition = myContext.lastFoldStartPosition;
      }
      
      if (wrapPosition != null){
        revertListeners(wrapPosition.offset, wrapPosition.visualLine);
        myContext.currentPosition = wrapPosition;
        softWrap = registerSoftWrap(wrapPosition.offset, myContext.getSpaceWidth(), myContext.logicalLineData);
        myContext.tokenStartOffset = wrapPosition.offset;
        revertedToFoldRegion = myContext.lastFold;
      }
      else {
        return myContext.tryToShiftToNextLine();
      }
    }
    
    myContext.skipToLineEnd = false;
    
    notifyListenersOnVisualLineStart(myContext.lineStartPosition);
    int actualSoftWrapOffset = softWrap.getStart();

    // There are three possible options:
    //   1. Soft wrap offset is located before the current offset;
    //   2. Soft wrap offset is located after the current offset but doesn't exceed current token end offset
    //      (it may occur if there are no convenient wrap positions before the current offset);
    //   3. Soft wrap offset is located after the current offset and exceeds current token end offset;
    // We should process that accordingly.
    if (actualSoftWrapOffset > myContext.tokenEndOffset) {
      myContext.delayedSoftWrap = softWrap;
      myContext.onNonLineFeedSymbol(myContext.text.charAt(offset));
      return false;
    }
    else if (actualSoftWrapOffset < offset) {
      if (revertedToFoldRegion == null) {
        revertListeners(actualSoftWrapOffset, myContext.currentPosition.visualLine);
        for (int j = offset - 1; j >= actualSoftWrapOffset; j--) {
          int pixelsDiff = myOffset2widthInPixels.data[j - myOffset2widthInPixels.anchor];
          int columnsDiff = calculateWidthInColumns(myContext.text.charAt(j), pixelsDiff, myContext.getPlainSpaceWidth());
          myContext.currentPosition.offset--;
          myContext.currentPosition.logicalColumn -= columnsDiff;
          myContext.currentPosition.visualColumn -= columnsDiff;
          myContext.currentPosition.x -= pixelsDiff;
        }
      }
    }
    else if (actualSoftWrapOffset > offset) {
      myContext.onNonLineFeedSymbol(myContext.text.charAt(offset));
      for (int j = offset + 1; j < actualSoftWrapOffset; j++) {
        myContext.onNonLineFeedSymbol(myContext.text.charAt(offset));
      }
    }

    processSoftWrap(softWrap);
    myContext.currentPosition.offset = actualSoftWrapOffset;
    myOffset2fontType.clear();
    myOffset2widthInPixels.clear();

    if (checkIsDoneAfterSoftWrap()) {
      return true;
    }

    if (revertedToFoldRegion != null && myContext.currentPosition.offset == revertedToFoldRegion.getStartOffset()) {
      return processCollapsedFoldRegion(revertedToFoldRegion);
    }
    
    return false;
  }

  private int calculateNewX(char c) {
    if (c == '\t') {
      return EditorUtil.nextTabStop(myContext.currentPosition.x, myEditor);
    }
    else {
      return myContext.currentPosition.x + SoftWrapModelImpl.getEditorTextRepresentationHelper(myEditor).charWidth(c, myContext.fontType);
    }
  }

  private static int calculateWidthInColumns(char c, int widthInPixels, int plainSpaceWithInPixels) {
    if (c != '\t') {
      return 1;
    }
    int result = widthInPixels / plainSpaceWithInPixels;
    if (widthInPixels % plainSpaceWithInPixels > 0) {
      result++;
    }
    return result;
  }

  /**
   * This method is assumed to be called in a situation when visible area width is exceeded. It tries to create and register
   * new soft wrap which data is defined in accordance with the given parameters.
   * <p/>
   * There is a possible case that no soft wrap is created and registered. That is true, for example, for a situation when
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
  private SoftWrapImpl registerSoftWrap(int minOffset, int preferredOffset, int maxOffset, int spaceSize, LogicalLineData lineData) {
    int softWrapOffset = calculateBackwardSpaceOffsetIfPossible(minOffset, preferredOffset);
    if (softWrapOffset < 0) {
      softWrapOffset = calculateBackwardOffsetForEasternLanguageIfPossible(minOffset, preferredOffset);
    }
    if (softWrapOffset < 0) {
      Document document = myEditor.getDocument();

      // Performance optimization implied by profiling results analysis.
      if (myLineWrapPositionStrategy == null) {
        myLineWrapPositionStrategy = LanguageLineWrapPositionStrategy.INSTANCE.forEditor(myEditor);
      }

      softWrapOffset = myLineWrapPositionStrategy.calculateWrapPosition(
        document, myEditor.getProject(), minOffset, maxOffset, preferredOffset, true, true
      );
    }
    
    if (softWrapOffset >= lineData.endLineOffset || softWrapOffset < 0
        || (myCustomIndentUsedLastTime && softWrapOffset == lineData.nonWhiteSpaceSymbolOffset)
        || (softWrapOffset > preferredOffset && myContext.lastFoldStartPosition != null // Prefer to wrap on fold region backwards
            && myContext.lastFoldStartPosition.offset <= preferredOffset))              // to wrapping forwards.
    {
      return null;
    }

    return registerSoftWrap(softWrapOffset, spaceSize, lineData);
  }
  
  @NotNull
  private SoftWrapImpl registerSoftWrap(int offset, int spaceSize, LogicalLineData lineData) {
    int indentInColumns = 0;
    int indentInPixels = myPainter.getMinDrawingWidth(SoftWrapDrawingType.AFTER_SOFT_WRAP);
    if (myCustomIndentUsedLastTime) {
      indentInColumns = myCustomIndentValueUsedLastTime + lineData.indentInColumns;
      indentInPixels += lineData.indentInPixels + (myCustomIndentValueUsedLastTime * spaceSize);
    }
    SoftWrapImpl result = new SoftWrapImpl(
      new TextChangeImpl("\n" + StringUtil.repeatSymbol(' ', indentInColumns), offset, offset),
      indentInColumns + 1/* for 'after soft wrap' drawing */,
      indentInPixels
    );
    myStorage.storeOrReplace(result);
    return result;
  }

  /**
   * It was found out that frequent soft wrap position calculation may become performance bottleneck (e.g. consider application
   * that is run under IJ and writes long strings to stdout non-stop. If those strings are long enough to be soft-wrapped,
   * we have the mentioned situation).
   * <p/>
   * Hence, we introduce an optimization here - try to find offset of white space symbol that belongs to the target interval and
   * use its offset as soft wrap position.
   * 
   * @param minOffset         min offset to use (inclusive)
   * @param preferredOffset   max offset to use (inclusive)
   * @return                  offset of the space symbol that belongs to <code>[minOffset; preferredOffset]</code> interval if any;
   *                          <code>'-1'</code> otherwise
   */
  private int calculateBackwardSpaceOffsetIfPossible(int minOffset, int preferredOffset) {
    // There is a possible case that we have a long line that contains many non-white space symbols eligible for performing
    // soft wrap that are preceded by white space symbol. We don't want to create soft wrap that is located so far from the
    // preferred position then, hence, we check white space symbol existence not more than specific number of symbols back.
    int maxTrackBackSymbolsNumber = 10;
    int minOffsetToUse = minOffset;
    if (preferredOffset - minOffset > maxTrackBackSymbolsNumber) {
      minOffsetToUse = preferredOffset - maxTrackBackSymbolsNumber;
    }
    for (int i = preferredOffset - 1; i >= minOffsetToUse; i--) {
      char c = myContext.text.charAt(i);
      if (c == ' ') {
        return i + 1;
      }
    }
    return -1;
  }

  /**
   * There is a possible case that current line holds eastern language symbols (e.g. japanese text). We want to allow soft
   * wrap just after such symbols and this method encapsulates the logic that tries to calculate soft wraps offset on that basis.
   * 
   * @param minOffset         min offset to use (inclusive)
   * @param preferredOffset   max offset to use (inclusive)
   * @return                  soft wrap offset that belongs to <code>[minOffset; preferredOffset]</code> interval if any;
   *                          <code>'-1'</code> otherwise
   */
  public int calculateBackwardOffsetForEasternLanguageIfPossible(int minOffset, int preferredOffset) {
    // There is a possible case that we have a long line that contains many non-white space symbols eligible for performing
    // soft wrap that are preceded by white space symbol. We don't want to create soft wrap that is located so far from the
    // preferred position then, hence, we check white space symbol existence not more than specific number of symbols back.
    int maxTrackBackSymbolsNumber = 10;
    int minOffsetToUse = minOffset;
    if (preferredOffset - minOffset > maxTrackBackSymbolsNumber) {
      minOffsetToUse = preferredOffset - maxTrackBackSymbolsNumber;
    }
    for (int i = preferredOffset - 1; i >= minOffsetToUse; i--) {
      char c = myContext.text.charAt(i);
      if (c >= 0x2f00) { // Check this document for eastern languages unicode ranges - http://www.unicode.org/charts
        return i + 1;
      }
    }
    return -1;
  }
  
  private void processSoftWrap(SoftWrap softWrap) {
    notifyListenersOnSoftWrapLineFeed(true);
    
    EditorPosition position = myContext.currentPosition;
    position.visualColumn = 0;
    position.softWrapColumnDiff = position.visualColumn - position.foldingColumnDiff - position.logicalColumn;
    position.softWrapLinesCurrent++;
    position.visualLine++;
    notifyListenersOnSoftWrapLineFeed(false);
    myContext.lineStartPosition.from(myContext.currentPosition);

    position.x = softWrap.getIndentInPixels();
    position.visualColumn = softWrap.getIndentInColumns();
    position.softWrapColumnDiff += softWrap.getIndentInColumns();
    
    myContext.softWrapStartOffset = softWrap.getStart() + 1;
    
    myContext.clearLastFoldInfo();
  }

  /**
   * There is a possible case that we need to reparse the whole document (e.g. visible area width is changed or user-defined
   * soft wrap indent is changed etc). This method encapsulates that logic, i.e. it checks if necessary conditions are satisfied
   * and updates internal state as necessary.
   * 
   * @return <code>true</code> if re-calculation logic was performed;
   *         <code>false</code> otherwise (e.g. we need to perform re-calculation but current editor is now shown, i.e. we don't
   *         have information about viewport width
   */
  public boolean recalculateIfNecessary() {
    if (myInProgress) {
      return false;
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
    if (!indentChanged && myVisibleAreaWidth == currentVisibleAreaWidth) {
      return recalculateSoftWraps(); // Recalculate existing dirty regions if any.
    }

    final JScrollBar scrollBar = myEditor.getScrollPane().getVerticalScrollBar();
    if (myVerticalScrollBarWidth < 0) {
      myVerticalScrollBarWidth = scrollBar.getWidth();
      if (myVerticalScrollBarWidth <= 0) {
        myVerticalScrollBarWidth = scrollBar.getPreferredSize().width;
      }
    }
    
    // We experienced the following situation:
    //   1. Editor is configured to show scroll bars only when necessary;
    //   2. Editor with active soft wraps is changed in order for the vertical scroll bar to appear;
    //   3. Vertical scrollbar consumes vertical space, hence, soft wraps are recalculated because of the visual area width change;
    //   4. Newly recalculated soft wraps trigger editor size update;
    //   5. Editor size update starts scroll pane update which, in turn, disables vertical scroll bar at first (the reason for that
    //      lays somewhere at the swing depth);
    //   6. Soft wraps are recalculated because of visible area width change caused by the disabled vertical scroll bar;
    //   7. Go to the step 4;
    // I.e. we have an endless EDT activity that stops only when editor is re-sized in a way to avoid vertical scroll bar.
    // That's why we don't recalculate soft wraps when visual area width is changed to the vertical scroll bar width value assuming
    // that such a situation is triggered by the scroll bar (dis)appearance.
    if (Math.abs(currentVisibleAreaWidth - myVisibleAreaWidth) == myVerticalScrollBarWidth) {
      myVisibleAreaWidth = currentVisibleAreaWidth;
      return recalculateSoftWraps();
    }
    
    // We want to adjust viewport's 'y' coordinate on complete recalculation, so, we remember number of soft-wrapped lines
    // before the target offset on recalculation start and compare it with the number of soft-wrapped lines before the same offset
    // after the recalculation.
    int softWrapsBefore = -1;
    final ScrollingModelEx scrollingModel = myEditor.getScrollingModel();
    int yScrollOffset = scrollingModel.getVerticalScrollOffset();
    int anchorOffset = myLastTopLeftCornerOffset;
    if (anchorOffset >= 0) {
      softWrapsBefore = getNumberOfSoftWrapsBefore(anchorOffset);
    }

    // Drop information about processed lines.
    reset();
    myStorage.removeAll();
    myVisibleAreaWidth = currentVisibleAreaWidth;
    final boolean result = recalculateSoftWraps();
    if (!result) {
      return false;
    }

    // Adjust viewport's 'y' coordinate if necessary.
    if (softWrapsBefore >= 0) {
      int softWrapsNow = getNumberOfSoftWrapsBefore(anchorOffset);
      if (softWrapsNow != softWrapsBefore) {
        scrollingModel.disableAnimation();
        try {
          scrollingModel.scrollVertically(yScrollOffset + (softWrapsNow - softWrapsBefore) * myEditor.getLineHeight());
        }
        finally {
          scrollingModel.enableAnimation();
        }
      }
    }
    updateLastTopLeftCornerOffset();
    return true;
  }

  private void updateLastTopLeftCornerOffset() {
    int visualLine = 1 + myEditor.getScrollingModel().getVisibleArea().y / myEditor.getLineHeight();
    myLastTopLeftCornerOffset = myEditor.myUseNewRendering ? myEditor.visualLineStartOffset(visualLine) :
                                myDataMapper.getVisualLineStartOffset(visualLine);
  }
  
  private int getNumberOfSoftWrapsBefore(int offset) {
    final int i = myStorage.getSoftWrapIndex(offset);
    return i >= 0 ? i : -i - 1;
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

  public boolean removeListener(@NotNull SoftWrapAwareDocumentParsingListener listener) {
    return myListeners.remove(listener);
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
  private void notifyListenersOnVisualLineStart(@NotNull EditorPosition position) {
    for (int i = 0; i < myListeners.size(); i++) {
      // Avoid unnecessary Iterator object construction as this method is expected to be called frequently.
      SoftWrapAwareDocumentParsingListener listener = myListeners.get(i);
      listener.onVisualLineStart(position);
    }
  }

  @SuppressWarnings({"ForLoopReplaceableByForEach"})
  private void notifyListenersOnVisualLineEnd() {
    for (int i = 0; i < myListeners.size(); i++) {
      // Avoid unnecessary Iterator object construction as this method is expected to be called frequently.
      SoftWrapAwareDocumentParsingListener listener = myListeners.get(i);
      listener.onVisualLineEnd(myContext.currentPosition);
    }
  }

  @SuppressWarnings({"ForLoopReplaceableByForEach"})
  private void notifyListenersOnTabulation(int widthInColumns) {
    for (int i = 0; i < myListeners.size(); i++) {
      // Avoid unnecessary Iterator object construction as this method is expected to be called frequently.
      SoftWrapAwareDocumentParsingListener listener = myListeners.get(i);
      listener.onTabulation(myContext.currentPosition, widthInColumns);
    }
  }

  @SuppressWarnings({"ForLoopReplaceableByForEach"})
  private void notifyListenersOnSoftWrapLineFeed(boolean before) {
    for (int i = 0; i < myListeners.size(); i++) {
      // Avoid unnecessary Iterator object construction as this method is expected to be called frequently.
      SoftWrapAwareDocumentParsingListener listener = myListeners.get(i);
      if (before) {
        listener.beforeSoftWrapLineFeed(myContext.currentPosition);
      }
      else {
        listener.afterSoftWrapLineFeed(myContext.currentPosition);
      }
    }
  }

  @SuppressWarnings({"ForLoopReplaceableByForEach"})
  private void notifyListenersOnCacheUpdateStart(IncrementalCacheUpdateEvent event) {
    for (int i = 0; i < myListeners.size(); i++) {
      // Avoid unnecessary Iterator object construction as this method is expected to be called frequently.
      SoftWrapAwareDocumentParsingListener listener = myListeners.get(i);
      listener.onCacheUpdateStart(event);
    }
  }
  
  @SuppressWarnings({"ForLoopReplaceableByForEach"})
  private void notifyListenersOnCacheUpdateEnd(IncrementalCacheUpdateEvent event) {
    for (int i = 0; i < myListeners.size(); i++) {
      // Avoid unnecessary Iterator object construction as this method is expected to be called frequently.
      SoftWrapAwareDocumentParsingListener listener = myListeners.get(i);
      listener.onRecalculationEnd(event);
    }
  }

  public void beforeDocumentChange(DocumentEvent event) {
    myDocumentChangedEvent = new IncrementalCacheUpdateEvent(event, myDataMapper, myEditor); 
  }

  public void documentChanged(DocumentEvent event) {
    LOG.assertTrue(myDocumentChangedEvent != null);
    myDocumentChangedEvent.updateAfterDocumentChange(event.getDocument());
    recalculate(myDocumentChangedEvent);
    myDocumentChangedEvent = null;
  }

  public void setWidthProvider(@NotNull VisibleAreaWidthProvider widthProvider) {
    myWidthProvider = widthProvider;
    reset();
  }

  @NotNull
  @Override
  public String dumpState() {
    return String.format(
      "recalculation in progress: %b; event being processed: %s",
      myInProgress, myEventBeingProcessed
    );
  }

  @Override
  public String toString() {
    return dumpState();
  }

  @TestOnly
  public void setSoftWrapPainter(SoftWrapPainter painter) {
    myPainter = painter;
  }

  public Rectangle getAvailableArea() {
    Rectangle visibleArea = myEditor.getScrollingModel().getVisibleArea();
    return new Rectangle(myWidthProvider.getVisibleAreaWidth(), visibleArea.height);
  }

  /**
   * We need to use correct indent for soft-wrapped lines, i.e. they should be indented to the start of the logical line.
   * This class stores information about logical line start indent. 
   */
  private class LogicalLineData {
    
    public int indentInColumns;
    public int indentInPixels;
    public int endLineOffset;
    public int nonWhiteSpaceSymbolOffset;

    public void update(int logicalLine, int spaceWidth, int plainSpaceWidth) {
      Document document = myEditor.getDocument();
      int startLineOffset;
      if (logicalLine >= document.getLineCount()) {
        startLineOffset = endLineOffset = document.getTextLength();
      }
      else {
        startLineOffset = document.getLineStartOffset(logicalLine);
        endLineOffset = document.getLineEndOffset(logicalLine);
      }
      CharSequence text = document.getCharsSequence();
      indentInColumns = 0;
      indentInPixels = 0;
      nonWhiteSpaceSymbolOffset = -1;

      for (int i = startLineOffset; i < endLineOffset; i++) {
        char c = text.charAt(i);
        switch (c) {
          case ' ': indentInColumns += 1; indentInPixels += spaceWidth; break;
          case '\t':
            int x = EditorUtil.nextTabStop(indentInPixels, myEditor);
            indentInColumns += calculateWidthInColumns(c, x - indentInPixels, plainSpaceWidth);
            indentInPixels = x;
            break;
          default: nonWhiteSpaceSymbolOffset = i; return;
        }
      }
    }

    /**
     * There is a possible case that all document line symbols before the first soft wrap are white spaces. We don't want to use
     * such a big indent then.
     * <p/>
     * This method encapsulates logic that 'resets' indent to use if such a situation is detected.
     *
     * @param softWrapOffset    offset of the soft wrap that occurred on document line which data is stored at the current object
     */
    public void update(int softWrapOffset) {
      if (nonWhiteSpaceSymbolOffset >= 0 && softWrapOffset > nonWhiteSpaceSymbolOffset) {
        return;
      }
      indentInColumns = 0;
      indentInPixels = 0;
    }
    
    public void reset() {
      indentInColumns = 0;
      indentInPixels = 0;
      endLineOffset = 0;
    }
  }

  /**
   * This interface is introduced mostly for encapsulating GUI-specific values retrieval and make it possible to write
   * tests for soft wraps processing.
   */
  public interface VisibleAreaWidthProvider {
    int getVisibleAreaWidth();
  }

  private static class DefaultVisibleAreaWidthProvider implements VisibleAreaWidthProvider {

    private final EditorImpl myEditor;

    DefaultVisibleAreaWidthProvider(EditorImpl editor) {
      myEditor = editor;
    }

    @Override
    public int getVisibleAreaWidth() {
      Insets insets = myEditor.getContentComponent().getInsets();
      int width = Math.max(0, myEditor.getScrollingModel().getVisibleArea().width - insets.left - insets.right);
      if (myEditor.isInDistractionFreeMode()) {
        int rightMargin = myEditor.getSettings().getRightMargin(myEditor.getProject());
        if (rightMargin > 0) width = Math.min(width, rightMargin * EditorUtil.getPlainSpaceWidth(myEditor));
      }
      return width;
    }
  }

  /**
   * Primitive array-based data structure that contain mappings like {@code int -> int}.
   * <p/>
   * The key is array index plus anchor; the value is array value.
   */
  private static class WidthsStorage {
    public int[] data = new int[256];
    public int anchor;
    public int end;

    public void clear() {
      anchor = 0;
      end = 0;
    }
  }
  
  /**
   *
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
   * This is primitive array-based data structure that contains {@code offset -> font type} mappings.
   */
  private static class FontTypesStorage {
    
    private int[] myStarts = new int[256];
    private int[] myEnds = new int[256];
    private int[] myData = new int[256];
    private int myLastIndex = -1;

    public void fill(int start, int end, int value) {
      if (myLastIndex >= 0 && myData[myLastIndex] == value && myEnds[myLastIndex] == start) {
        myEnds[myLastIndex] = end;
        return;
      }
      if (++myLastIndex >= myData.length) {
        expand();
      }
      myStarts[myLastIndex] = start;
      myEnds[myLastIndex] = end;
      myData[myLastIndex] = value;
    }

    /**
     * Tries to retrieve stored value for the given offset if any;
     * 
     * @param offset    target offset
     * @return          target value if any is stored; <code>-1</code> otherwise
     */
    public int get(int offset) {
      // The key is array index plus anchor; the value is array value.
      if (myLastIndex < 0) {
        return -1;
      }
      for (int i = myLastIndex; i >= 0 && myEnds[i] >= offset; i--) {
        if (myStarts[i] <= offset) {
          return myData[i];
        }
      }
      return -1;
    }
    
    public void clear() {
      myLastIndex = -1;
    }

    private void expand() {
      int[] tmp = new int[myStarts.length * 2];
      System.arraycopy(myStarts, 0, tmp, 0, myStarts.length);
      myStarts = tmp;
      
      tmp = new int[myEnds.length * 2];
      System.arraycopy(myEnds, 0, tmp, 0, myEnds.length);
      myEnds = tmp;

      tmp = new int[myData.length * 2];
      System.arraycopy(myData, 0, tmp, 0, myData.length);
      myData = tmp;
    }
  }
  
  private class ProcessingContext {

    public final PrimitiveIntMap fontType2spaceWidth = new PrimitiveIntMap();
    public final LogicalLineData logicalLineData     = new LogicalLineData();

    public CharSequence   text;
    public EditorPosition lineStartPosition;
    public EditorPosition currentPosition;
    /**
     * Start position of the last collapsed fold region that is located at the current visual line and can be used as a fall back
     * position for soft wrapping.
     */
    public EditorPosition lastFoldStartPosition;
    public EditorPosition lastFoldEndPosition;
    /** A fold region referenced by the {@link #lastFoldStartPosition}. */
    public FoldRegion     lastFold;
    public SoftWrapImpl   delayedSoftWrap;
    public int            reservedWidthInPixels;
    /**
     * Min offset to use when new soft wrap should be introduced. I.e. every time we detect that text exceeds visual width,
     */
    public int            softWrapStartOffset;
    public int            rangeEndOffset;
    public int            tokenStartOffset;
    public int            tokenEndOffset;
    @JdkConstants.FontStyle
    public int            fontType;
    public boolean        skipToLineEnd;

    @Override
    public String toString() {
      return "reserved width: " + reservedWidthInPixels + ", soft wrap start offset: " + softWrapStartOffset + ", range end offset: "
             + rangeEndOffset + ", token offsets: [" + tokenStartOffset + "; " + tokenEndOffset + "], font type: " + fontType
             + ", skip to line end: " + skipToLineEnd + ", delayed soft wrap: " + delayedSoftWrap + ", current position: "+ currentPosition
             + "line start position: " + lineStartPosition;
    }

    public void reset() {
      text = null;
      lineStartPosition = null;
      currentPosition = null;
      clearLastFoldInfo();
      delayedSoftWrap = null;
      reservedWidthInPixels = 0;
      softWrapStartOffset = 0;
      rangeEndOffset = 0;
      tokenStartOffset = 0;
      tokenEndOffset = 0;
      fontType = Font.PLAIN;
      skipToLineEnd = false;
      fontType2spaceWidth.reset();
      logicalLineData.reset();
    }

    public int getSpaceWidth() {
      return getSpaceWidth(fontType);
    }

    public int getPlainSpaceWidth() {
      return getSpaceWidth(Font.PLAIN);
    }

    private int getSpaceWidth(@JdkConstants.FontStyle int fontType) {
      int result = fontType2spaceWidth.get(fontType);
      if (result <= 0) {
        result = EditorUtil.getSpaceWidth(fontType, myEditor);
        fontType2spaceWidth.put(fontType, result);
      }
      assert result > 0;
      return result;
    }

    /**
     * Asks current context to update its state assuming that it begins to point to the line next to its current position.
     */
    @SuppressWarnings("MagicConstant")
    public void onNewLine() {
      notifyListenersOnVisualLineEnd();
      currentPosition.onNewLine();
      softWrapStartOffset = currentPosition.offset;
      clearLastFoldInfo();
      lineStartPosition.from(currentPosition);
      logicalLineData.update(currentPosition.logicalLine, getSpaceWidth(), getPlainSpaceWidth());
      fontType = myOffset2fontType.get(currentPosition.offset);

      myOffset2fontType.clear();
      myOffset2widthInPixels.clear();
      skipToLineEnd = false;
    }

    private void clearLastFoldInfo() {
      lastFoldStartPosition = null;
      lastFoldEndPosition = null;
      lastFold = null;
    }

    public void onNonLineFeedSymbol(char c) {
      int newX;
      if (myOffset2widthInPixels.end > myContext.currentPosition.offset
          && (myOffset2widthInPixels.anchor + myOffset2widthInPixels.end > myContext.currentPosition.offset)
          && myContext.currentPosition.symbol != '\t'/*we need to recalculate tabulation width after soft wrap*/)
      {
        newX = myContext.currentPosition.x + myOffset2widthInPixels.data[myContext.currentPosition.offset - myOffset2widthInPixels.anchor];
      }
      else {
        newX = calculateNewX(c);
      }
      onNonLineFeedSymbol(c, newX);
    }
    
    @SuppressWarnings("MagicConstant")
    public void onNonLineFeedSymbol(char c, int newX) {
      int widthInPixels = newX - myContext.currentPosition.x;
      
      if (myOffset2widthInPixels.anchor <= 0) {
        myOffset2widthInPixels.anchor = currentPosition.offset;
      }
      if (currentPosition.offset - myOffset2widthInPixels.anchor >= myOffset2widthInPixels.data.length) {
        int newLength = Math.max(myOffset2widthInPixels.data.length * 2, currentPosition.offset - myOffset2widthInPixels.anchor + 1);
        int[] newData = new int[newLength];
        System.arraycopy(myOffset2widthInPixels.data, 0, newData, 0, myOffset2widthInPixels.data.length);
        myOffset2widthInPixels.data = newData;
      }
      myOffset2widthInPixels.data[currentPosition.offset - myOffset2widthInPixels.anchor] = widthInPixels;
      myOffset2widthInPixels.end++;
      
      int widthInColumns = calculateWidthInColumns(c, widthInPixels, myContext.getPlainSpaceWidth());
      if (c == '\t') {
        notifyListenersOnVisualLineStart(myContext.lineStartPosition);
        notifyListenersOnTabulation(widthInColumns);
      }
      
      currentPosition.logicalColumn += widthInColumns;
      currentPosition.visualColumn += widthInColumns;
      currentPosition.x = newX;
      currentPosition.offset++;
      fontType = myOffset2fontType.get(currentPosition.offset);
    }

    /**
     * Updates state of the current context object in order to point to the end of the given collapsed fold region.
     * 
     * @param foldRegion    collapsed fold region to process
     */
    private void advance(FoldRegion foldRegion, int placeHolderWidthInPixels) {
      lastFoldStartPosition = currentPosition.clone();
      lastFold = foldRegion;
      int visualLineBefore = currentPosition.visualLine;
      int logicalLineBefore = currentPosition.logicalLine;
      int logicalColumnBefore = currentPosition.logicalColumn;
      currentPosition.advance(foldRegion, -1);
      currentPosition.x += placeHolderWidthInPixels;
      int collapsedFoldingWidthInColumns = currentPosition.logicalColumn;
      if (currentPosition.logicalLine <= logicalLineBefore) {
        // Single-line fold region.
        collapsedFoldingWidthInColumns = currentPosition.logicalColumn - logicalColumnBefore;
      }
      else {
        final DocumentEx document = myEditor.getDocument();
        int endFoldLine = document.getLineNumber(foldRegion.getEndOffset());
        logicalLineData.endLineOffset = document.getLineEndOffset(endFoldLine);
      }
      notifyListenersOnFoldRegion(foldRegion, collapsedFoldingWidthInColumns, visualLineBefore);
      tokenStartOffset = myContext.currentPosition.offset;
      softWrapStartOffset = foldRegion.getEndOffset();
      lastFoldEndPosition = currentPosition.clone();
    }
    
    /**
     * Asks current context to update its state in order to show to the first symbol of the next visual line if it belongs to
     * [{@link #tokenStartOffset}; {@link #skipToLineEnd} is set to <code>'true'</code> otherwise
     */
    public boolean tryToShiftToNextLine() {
      for (int i = currentPosition.offset; i < tokenEndOffset; i++) {
        char c = text.charAt(i);
        currentPosition.offset = i;
        if (c == '\n') {
          onNewLine(); // Assuming that offset is incremented during this method call
          return checkIsDoneAfterNewLine();
        }
        else {
          onNonLineFeedSymbol(c, offsetToX(i, c));
        }
      }
      skipToLineEnd = true;
      return false;
    }

    /**
     * Allows to answer if point with the given <code>'x'</code> coordinate exceeds visual area's right edge.
     * 
     * @param x   target <code>'x'</code> coordinate to check
     * @return    <code>true</code> if given <code>'x'</code> coordinate exceeds visual area's right edge; <code>false</code> otherwise
     */
    public boolean exceedsVisualEdge(int x) {
      return x > myVisibleAreaWidth || x == myVisibleAreaWidth && !myEditor.myUseNewRendering;
    }
  }

  /**
   * Primitive data structure to hold {@code int -> int} mappings assuming that the following is true:
   * <pre>
   * <ul>
   *   <li>number of entries is small;</li>
   *   <li>the keys are roughly adjacent;</li>
   * </ul>
   * </pre>
   */
  private static class PrimitiveIntMap {
    
    private int[] myData = new int[16];
    private int myShift;
    
    public int get(int key) {
      int index = key + myShift;
      if (index < 0 || index >= myData.length) {
        return -1;
      }
      return myData[index];
    }
    
    public void put(int key, int value) {
      int index = key + myShift;
      if (index < 0) {
        int[] tmp = new int[myData.length - index];
        System.arraycopy(myData, 0, tmp, -index, myData.length);
        myData = tmp;
        myShift -= index;
        index = 0;
      }
      myData[index] = value;
    }
    
    public void reset() {
      myShift = 0;
      Arrays.fill(myData, 0);
    }
  }
}
