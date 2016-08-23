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
package com.intellij.openapi.editor.impl.view;

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.impl.EditorDocumentPriorities;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType;
import com.intellij.openapi.editor.impl.softwrap.mapping.IncrementalCacheUpdateEvent;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapAwareDocumentParsingListenerAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Calculates width (in pixels) of editor contents.
 */
class EditorSizeManager extends InlayModel.SimpleAdapter implements PrioritizedDocumentListener, Disposable, FoldingListener, Dumpable {
  private static final Logger LOG = Logger.getInstance(EditorSizeManager.class);
  
  private static final int UNKNOWN_WIDTH = Integer.MAX_VALUE;
  
  private final EditorView myView;
  private final EditorImpl myEditor;
  private final DocumentEx myDocument;
  
  private final TIntArrayList myLineWidths = new TIntArrayList(); // cached widths of visual lines (in pixels)
                                                                  // negative value means an estimated (not precise) width 
                                                                  // UNKNOWN_WIDTH(Integer.MAX_VALUE) means no value
  private int myWidthInPixels;

  private int myMaxLineWithExtensionWidth;
  private int myWidestLineWithExtension;
  
  private int myDocumentChangeStartOffset;
  private int myDocumentChangeEndOffset;
  private int myFoldingChangeStartOffset = Integer.MAX_VALUE;
  private int myFoldingChangeEndOffset = Integer.MIN_VALUE;
  
  private int myVirtualPageHeight;
  
  private boolean myDirty; // true if we cannot calculate preferred size now because soft wrap model was invalidated after editor 
                           // became hidden. myLineWidths contents is irrelevant in such a state. Previously calculated preferred size
                           // is kept until soft wraps will be recalculated and size calculations will become possible
  
  private final List<TextRange> myDeferredRanges = new ArrayList<>();
  
  private final SoftWrapAwareDocumentParsingListenerAdapter mySoftWrapChangeListener = new SoftWrapAwareDocumentParsingListenerAdapter() {
    @Override
    public void onRecalculationEnd(@NotNull IncrementalCacheUpdateEvent event) {
      onSoftWrapRecalculationEnd(event);
    }
  };
  
  EditorSizeManager(EditorView view) {
    myView = view;
    myEditor = view.getEditor();
    myDocument = myEditor.getDocument(); 
    myDocument.addDocumentListener(this, this);
    myEditor.getFoldingModel().addListener(this, this);
    myEditor.getSoftWrapModel().getApplianceManager().addListener(mySoftWrapChangeListener);
    myEditor.getInlayModel().addListener(this, this);
  }

  @Override
  public void dispose() {
    myEditor.getSoftWrapModel().getApplianceManager().removeListener(mySoftWrapChangeListener);
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.EDITOR_TEXT_WIDTH_CACHE;
  }

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
    if (myDocument.isInBulkUpdate()) return;
    myDocumentChangeStartOffset = event.getOffset();
    myDocumentChangeEndOffset = event.getOffset() + event.getNewLength();
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    if (myDocument.isInBulkUpdate()) return;
    doInvalidateRange(myDocumentChangeStartOffset, myDocumentChangeEndOffset);
    assertValidState();
  }
  
  @Override
  public void onFoldRegionStateChange(@NotNull FoldRegion region) {
    if (myDocument.isInBulkUpdate()) return;
    if (region.isValid()) {
      myFoldingChangeStartOffset = Math.min(myFoldingChangeStartOffset, region.getStartOffset());
      myFoldingChangeEndOffset = Math.max(myFoldingChangeEndOffset, region.getEndOffset());
    }
  }

  @Override
  public void onFoldProcessingEnd() {
    if (myDocument.isInBulkUpdate()) return;
    if (myFoldingChangeStartOffset <= myFoldingChangeEndOffset) {
      doInvalidateRange(myFoldingChangeStartOffset, myFoldingChangeEndOffset);
    }
    myFoldingChangeStartOffset = Integer.MAX_VALUE;
    myFoldingChangeEndOffset = Integer.MIN_VALUE;

    for (TextRange range : myDeferredRanges) {
      onTextLayoutPerformed(range.getStartOffset(), range.getEndOffset());
    }
    myDeferredRanges.clear();
    assertValidState();
  }

  @Override
  public void onUpdated(@NotNull Inlay inlay) {
    if (myDocument.isInEventsHandling() || myDocument.isInBulkUpdate()) return;
    doInvalidateRange(inlay.getOffset(), inlay.getOffset());
  }

  private void onSoftWrapRecalculationEnd(IncrementalCacheUpdateEvent event) {
    if (myDocument.isInBulkUpdate()) return;
    boolean invalidate = true;
    if (myEditor.getFoldingModel().isInBatchFoldingOperation()) {
      myFoldingChangeStartOffset = Math.min(myFoldingChangeStartOffset, event.getStartOffset());
      myFoldingChangeEndOffset = Math.max(myFoldingChangeEndOffset, event.getActualEndOffset());
      invalidate = false;
    }
    if (myDocument.isInEventsHandling()) {
      myDocumentChangeStartOffset = Math.min(myDocumentChangeStartOffset, event.getStartOffset());
      myDocumentChangeEndOffset = Math.max(myDocumentChangeEndOffset, event.getActualEndOffset());
      invalidate = false;
    }
    if (invalidate) {
      doInvalidateRange(event.getStartOffset(), event.getActualEndOffset());
    }
  }

  Dimension getPreferredSize() {
    int widthWithoutCaret = getPreferredWidth();
    int width = widthWithoutCaret;
    if (!myDocument.isInBulkUpdate()) {
      for (Caret caret : myEditor.getCaretModel().getAllCarets()) {
        if (caret.isUpToDate()) {
          int caretX = myView.visualPositionToXY(caret.getVisualPosition()).x;
          width = Math.max(caretX, width);
        }
      }
    }
    if (shouldRespectAdditionalColumns(widthWithoutCaret)) {
      width += myEditor.getSettings().getAdditionalColumnsCount() * myView.getPlainSpaceWidth();
    }
    Insets insets = myView.getInsets();
    return new Dimension(width + insets.left + insets.right, getPreferredHeight());
  }
  
  int getPreferredHeight() {
    int lineHeight = myView.getLineHeight();
    if (myEditor.isOneLineMode()) return lineHeight;

    // Preferred height of less than a single line height doesn't make sense:
    // at least a single line with a blinking caret on it is to be displayed
    int size = Math.max(myEditor.getVisibleLineCount(), 1) * lineHeight;

    EditorSettings settings = myEditor.getSettings();
    if (settings.isAdditionalPageAtBottom()) {
      int visibleAreaHeight = myEditor.getScrollingModel().getVisibleArea().height;
      // There is a possible case that user with 'show additional page at bottom' scrolls to that virtual page; switched to another
      // editor (another tab); and then returns to the previously used editor (the one scrolled to virtual page). We want to preserve
      // correct view size then because viewport position is set to the end of the original text otherwise.
      if (visibleAreaHeight > 0 || myVirtualPageHeight <= 0) {
        myVirtualPageHeight = Math.max(visibleAreaHeight - 2 * lineHeight, lineHeight);
      }
      
      size += Math.max(myVirtualPageHeight, 0);
    }
    else {
      size += settings.getAdditionalLinesCount() * lineHeight;
    }
      
    Insets insets = myView.getInsets();
    return size + insets.top + insets.bottom;
  }

  private boolean shouldRespectAdditionalColumns(int widthWithoutCaret) {
    return !myEditor.getSoftWrapModel().isSoftWrappingEnabled()
           || myEditor.getSoftWrapModel().isRespectAdditionalColumns()
           || widthWithoutCaret > myEditor.getScrollingModel().getVisibleArea().getWidth();
  }

  private int getPreferredWidth() {
    if (myWidthInPixels < 0) {
      assert !myDocument.isInBulkUpdate();
      myWidthInPixels = calculatePreferredWidth();
    }
    validateMaxLineWithExtension();
    return Math.max(myWidthInPixels, myMaxLineWithExtensionWidth);
  }

  private void validateMaxLineWithExtension() {
    if (myMaxLineWithExtensionWidth > 0) {
      Project project = myEditor.getProject();
      VirtualFile virtualFile = myEditor.getVirtualFile();
      if (project != null && virtualFile != null) {
        for (EditorLinePainter painter : EditorLinePainter.EP_NAME.getExtensions()) {
          Collection<LineExtensionInfo> extensions = painter.getLineExtensions(project, virtualFile, myWidestLineWithExtension);
          if (extensions != null && !extensions.isEmpty()) {
            return;
          }
        }
      }
      myMaxLineWithExtensionWidth = 0;
    }
  }

  private int calculatePreferredWidth() {
    if (checkDirty()) return 1;
    assertValidState();
    VisualLinesIterator iterator = new VisualLinesIterator(myEditor, 0);
    int maxWidth = 0;
    while (!iterator.atEnd()) {
      int visualLine = iterator.getVisualLine();
      int width = myLineWidths.get(visualLine);
      if (width == UNKNOWN_WIDTH) {
        final Ref<Boolean> approximateValue = new Ref<>(Boolean.FALSE);
        width = getVisualLineWidth(iterator, () -> approximateValue.set(Boolean.TRUE));
        if (approximateValue.get()) width = -width;
        myLineWidths.set(visualLine, width);
      }
      maxWidth = Math.max(maxWidth, Math.abs(width));
      iterator.advance();
    }
    return maxWidth;
  }
  
  int getVisualLineWidth(VisualLinesIterator visualLinesIterator, @Nullable Runnable quickEvaluationListener) {
    assert !visualLinesIterator.atEnd();
    int visualLine = visualLinesIterator.getVisualLine();
    FoldRegion[] topLevelRegions = myEditor.getFoldingModel().fetchTopLevel();
    if (quickEvaluationListener != null &&
        (topLevelRegions == null || topLevelRegions.length == 0) && myEditor.getSoftWrapModel().getRegisteredSoftWraps().isEmpty() &&
        !myView.getTextLayoutCache().hasCachedLayoutFor(visualLine)) {
      // fast path - speeds up editor opening
      quickEvaluationListener.run();
      return myView.getLogicalPositionCache().offsetToLogicalColumn(visualLine, 
                                                                    myDocument.getLineEndOffset(visualLine) - 
                                                                    myDocument.getLineStartOffset(visualLine)) * 
             myView.getMaxCharWidth();
    }
    float x = 0;
    int maxOffset = visualLinesIterator.getVisualLineStartOffset();
    for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView, visualLinesIterator,
                                                                                            quickEvaluationListener)) {
      x = fragment.getEndX();
      maxOffset = Math.max(maxOffset, fragment.getMaxOffset());
    }
    if (myEditor.getSoftWrapModel().getSoftWrap(maxOffset) != null) {
      x += myEditor.getSoftWrapModel().getMinDrawingWidthInPixels(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED);
    }
    return (int)x;
  }
  
  void reset() {
    assert !myDocument.isInBulkUpdate();
    doInvalidateRange(0, myDocument.getTextLength());
  }

  void invalidateRange(int startOffset, int endOffset) {
    if (myDocument.isInBulkUpdate()) return;
    if (myDocument.isInEventsHandling()) {
      myDocumentChangeStartOffset = Math.min(myDocumentChangeStartOffset, startOffset);
      myDocumentChangeEndOffset = Math.max(myDocumentChangeEndOffset, endOffset);
    }
    else if (myFoldingChangeEndOffset != Integer.MIN_VALUE) {
      // during batch folding processing we delay invalidation requests, as we cannot perform coordinate conversions immediately
      myFoldingChangeStartOffset = Math.min(myFoldingChangeStartOffset, startOffset);
      myFoldingChangeEndOffset = Math.max(myFoldingChangeEndOffset, endOffset);
    }
    else {
      doInvalidateRange(startOffset, endOffset);
    }
  }
  
  private void doInvalidateRange(int startOffset, int endOffset) {
    if (checkDirty()) return;
    myWidthInPixels = -1;
    int startVisualLine = myView.offsetToVisualLine(startOffset, false);
    int endVisualLine = myView.offsetToVisualLine(endOffset, true);
    int lineDiff = myEditor.getVisibleLineCount() - myLineWidths.size();
    if (lineDiff > 0) {
      int[] newEntries = new int[lineDiff];
      myLineWidths.insert(startVisualLine, newEntries);
    }
    else if (lineDiff < 0) {
      myLineWidths.remove(startVisualLine, -lineDiff);
    }
    for (int i = startVisualLine; i <= endVisualLine && i < myLineWidths.size(); i++) {
      myLineWidths.set(i, UNKNOWN_WIDTH);
    }
  }

  int getMaxLineWithExtensionWidth() {
    return myMaxLineWithExtensionWidth;
  }

  void setMaxLineWithExtensionWidth(int lineNumber, int width) {
    myWidestLineWithExtension = lineNumber;
    myMaxLineWithExtensionWidth = width;
  }

  void textLayoutPerformed(int startOffset, int endOffset) {
    if (myDocument.isInBulkUpdate()) return;
    if (myEditor.getFoldingModel().isInBatchFoldingOperation()) {
      myDeferredRanges.add(new TextRange(startOffset, endOffset));
    }
    else {
      onTextLayoutPerformed(startOffset, endOffset);
    }
  }

  private void onTextLayoutPerformed(int startOffset, int endOffset) {
    if (checkDirty()) return;    
    boolean purePaintingMode = myEditor.isPurePaintingMode();
    boolean foldingEnabled = myEditor.getFoldingModel().isFoldingEnabled();
    myEditor.setPurePaintingMode(false);
    myEditor.getFoldingModel().setFoldingEnabled(true);
    try {
      int startVisualLine = myView.offsetToVisualLine(startOffset, false);
      int endVisualLine = myView.offsetToVisualLine(endOffset, true);
      boolean sizeInvalidated = false;
      for (int i = startVisualLine; i <= endVisualLine; i++) {
        if (myLineWidths.get(i) < 0) {
          myLineWidths.set(i, UNKNOWN_WIDTH);
          sizeInvalidated = true;
        }
      }
      if (sizeInvalidated) {
        myWidthInPixels = -1;
        myEditor.getContentComponent().revalidate();
      }
    }
    finally {
      myEditor.setPurePaintingMode(purePaintingMode);
      myEditor.getFoldingModel().setFoldingEnabled(foldingEnabled);
    }
  }
  
  private boolean checkDirty() {
    if (myEditor.getSoftWrapModel().isDirty()) {
      myDirty = true;
      return true;
    }
    if (myDirty) {
      int visibleLineCount = myEditor.getVisibleLineCount();
      int lineDiff = visibleLineCount - myLineWidths.size();
      if (lineDiff > 0) myLineWidths.add(new int[lineDiff]);
      else if (lineDiff < 0) myLineWidths.remove(visibleLineCount, -lineDiff);
      for (int i = 0; i < visibleLineCount; i++) {
        myLineWidths.set(i, UNKNOWN_WIDTH);
      }
      myDirty = false;
    }
    return false;
  }

  @NotNull
  @Override
  public String dumpState() {
    return "[cached width: " + myWidthInPixels + 
           ", max line with extension width: " + myMaxLineWithExtensionWidth + 
           ", line widths: " + myLineWidths + "]";
  }

  private void assertValidState() {
    if (myDocument.isInBulkUpdate() || myDirty) return;
    if (myLineWidths.size() != myEditor.getVisibleLineCount()) {
      LOG.error("Inconsistent state", new Attachment("editor.txt", myEditor.dumpState()));
      reset();
    }
    assert myLineWidths.size() == myEditor.getVisibleLineCount();
  }

  @TestOnly
  public void validateState() {
    assertValidState();
  }
}
