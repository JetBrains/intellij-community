/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.EditorLinePainter;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LineExtensionInfo;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.impl.EditorDocumentPriorities;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.softwrap.mapping.IncrementalCacheUpdateEvent;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapAwareDocumentParsingListenerAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Calculates width (in pixels) of editor contents.
 */
class EditorSizeManager implements PrioritizedDocumentListener, Disposable, FoldingListener {
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
  
  private final List<TextRange> myDeferredRanges = new ArrayList<TextRange>();
  
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
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    invalidateRange(event.getOffset(), event.getOffset() + event.getNewLength());
  }
  
  private int foldingChangeStartOffset = Integer.MAX_VALUE;
  private int foldingChangeEndOffset = Integer.MIN_VALUE;
  
  @Override
  public void onFoldRegionStateChange(@NotNull FoldRegion region) {
    if (region.isValid()) {
      foldingChangeStartOffset = Math.min(foldingChangeStartOffset, region.getStartOffset());
      foldingChangeEndOffset = Math.max(foldingChangeEndOffset, region.getEndOffset());
    }
  }

  @Override
  public void onFoldProcessingEnd() {
    if (foldingChangeStartOffset <= foldingChangeEndOffset) {
      invalidateRange(foldingChangeStartOffset, foldingChangeEndOffset);
    }
    foldingChangeStartOffset = Integer.MAX_VALUE;
    foldingChangeEndOffset = Integer.MIN_VALUE;

    for (TextRange range : myDeferredRanges) {
      onTextLayoutPerformed(range.getStartOffset(), range.getEndOffset());
    }
    myDeferredRanges.clear();
  }

  private void onSoftWrapRecalculationEnd(IncrementalCacheUpdateEvent event) {
    invalidateRange(event.getStartOffset(), event.getActualEndOffset());
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
    return new Dimension(width, myEditor.getPreferredHeight());
  }

  private boolean shouldRespectAdditionalColumns(int widthWithoutCaret) {
    return !myEditor.getSoftWrapModel().isSoftWrappingEnabled()
           || myEditor.getSoftWrapModel().isRespectAdditionalColumns()
           || widthWithoutCaret > myEditor.getScrollingModel().getVisibleArea().getWidth();
  }

  private int getPreferredWidth() {
    if (myWidthInPixels < 0) {
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
    int lineCount = myLineWidths.size();
    int maxWidth = 0;
    for (int i = 0; i < lineCount; i++) {
      int width = myLineWidths.get(i);
      if (width == UNKNOWN_WIDTH) {
        final Ref<Boolean> approximateValue = new Ref<Boolean>(Boolean.FALSE);
        width = myView.getMaxWidthInLineRange(i, i, new Runnable() {
          @Override
          public void run() {
            approximateValue.set(Boolean.TRUE);
          }
        });
        if (approximateValue.get()) width = -width;
        myLineWidths.set(i, width);
      }
      maxWidth = Math.max(maxWidth, Math.abs(width));
    }
    return maxWidth;
  }

  void reset() {
    invalidateRange(0, myDocument.getTextLength());
  }

  void invalidateRange(int startOffset, int endOffset) {
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
    if (myEditor.getFoldingModel().isInBatchFoldingOperation()) {
      myDeferredRanges.add(new TextRange(startOffset, endOffset));
    }
    else {
      onTextLayoutPerformed(startOffset, endOffset);
    }
  }

  private void onTextLayoutPerformed(int startOffset, int endOffset) {
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
}
