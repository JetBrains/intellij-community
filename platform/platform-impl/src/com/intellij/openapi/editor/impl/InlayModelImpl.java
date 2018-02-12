/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.PrioritizedInternalDocumentListener;
import com.intellij.openapi.util.Getter;
import com.intellij.util.DocumentUtil;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class InlayModelImpl implements InlayModel, Disposable {
  private static final Logger LOG = Logger.getInstance(InlayModelImpl.class);
  private static final Comparator<Inlay> INLAY_COMPARATOR = Comparator.comparingInt(Inlay::getOffset)
    .thenComparing(i -> i.isRelatedToPrecedingText())
    .thenComparingInt(i -> ((InlayImpl)i).myOriginalOffset);

  private final EditorImpl myEditor;
  private final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);
  
  final List<InlayImpl> myInlaysInvalidatedOnMove = new ArrayList<>();
  final RangeMarkerTree<InlayImpl> myInlayTree;
  
  boolean myMoveInProgress;
  private List<Inlay> myInlaysAtCaret;

  InlayModelImpl(@NotNull EditorImpl editor) {
    myEditor = editor;
    myInlayTree = new RangeMarkerTree<InlayImpl>(editor.getDocument()) {
      @NotNull
      @Override
      protected RMNode<InlayImpl> createNewNode(@NotNull InlayImpl key, int start, int end,
                                                boolean greedyToLeft, boolean greedyToRight, boolean stickingToRight, int layer) {
        return new RMNode<InlayImpl>(this, key, start, end, greedyToLeft, greedyToRight, stickingToRight) {
          @Override
          protected Getter<InlayImpl> createGetter(@NotNull InlayImpl interval) {
            return interval;
          }
        };
      }

      @Override
      void fireBeforeRemoved(@NotNull InlayImpl markerEx, @NotNull @NonNls Object reason) {
        if (markerEx.myOffsetBeforeDisposal == -1) {
          if (myMoveInProgress) {
            // delay notification about invalidated inlay - folding model is not consistent at this point
            // (FoldingModelImpl.moveTextHappened hasn't been called yet at this point)
            myInlaysInvalidatedOnMove.add(markerEx);
          }
          else {
            notifyRemoved(markerEx);
          }
        }
      }
    };
    myEditor.getDocument().addDocumentListener(new PrioritizedInternalDocumentListener() {
      @Override
      public int getPriority() {
        return EditorDocumentPriorities.INLAY_MODEL;
      }

      @Override
      public void beforeDocumentChange(DocumentEvent event) {
        if (myEditor.getDocument().isInBulkUpdate()) return;
        int offset = event.getOffset();
        if (event.getOldLength() == 0 && offset == myEditor.getCaretModel().getOffset()) {
          List<Inlay> inlays = getInlineElementsInRange(offset, offset);
          int inlayCount = inlays.size();
          if (inlayCount > 0) {
            VisualPosition inlaysStartPosition = myEditor.offsetToVisualPosition(offset, false, false);
            VisualPosition caretPosition = myEditor.getCaretModel().getVisualPosition();
            if (inlaysStartPosition.line == caretPosition.line && 
                caretPosition.column >= inlaysStartPosition.column && caretPosition.column <= inlaysStartPosition.column + inlayCount) {
              myInlaysAtCaret = inlays;
              for (int i = 0; i < inlayCount; i++) {
                ((InlayImpl)inlays.get(i)).setStickingToRight(i >= (caretPosition.column - inlaysStartPosition.column));
              }
            }
          }
        }
      }

      @Override
      public void documentChanged(DocumentEvent event) {
        if (myInlaysAtCaret != null) {
          for (Inlay inlay : myInlaysAtCaret) {
            ((InlayImpl)inlay).setStickingToRight(inlay.isRelatedToPrecedingText());
          }
          myInlaysAtCaret = null;
        }
      }

      @Override
      public void moveTextHappened(int start, int end, int base) {
        for (InlayImpl inlay : myInlaysInvalidatedOnMove) {
          notifyRemoved(inlay);
        }
        myInlaysInvalidatedOnMove.clear();
      }
    }, this);
  }

  void reinitSettings() {
    myInlayTree.processAll(inlay -> {
      inlay.updateSize();
      return true;
    });
  }

  @Override
  public void dispose() {
    myInlayTree.dispose();
  }

  @Nullable
  @Override
  public Inlay addInlineElement(int offset, boolean relatesToPrecedingText, @NotNull EditorCustomElementRenderer renderer) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    DocumentEx document = myEditor.getDocument();
    if (DocumentUtil.isInsideSurrogatePair(document, offset)) return null;
    offset = Math.max(0, Math.min(document.getTextLength(), offset));
    InlayImpl inlay = new InlayImpl(myEditor, offset, relatesToPrecedingText, renderer);
    notifyAdded(inlay);
    return inlay;
  }

  @NotNull
  @Override
  public List<Inlay> getInlineElementsInRange(int startOffset, int endOffset) {
    List<Inlay> result = new ArrayList<>();
    myInlayTree.processOverlappingWith(startOffset, endOffset, inlay -> {
      result.add(inlay);
      return true;
    });
    Collections.sort(result, INLAY_COMPARATOR);
    return result;
  }

  @Override
  public boolean hasInlineElementAt(int offset) {
    return !myInlayTree.processOverlappingWith(offset, offset, inlay -> false);
  }

  @Override
  public boolean hasInlineElementAt(@NotNull VisualPosition visualPosition) {
    int offset = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(visualPosition));
    int inlayCount = getInlineElementsInRange(offset, offset).size();
    if (inlayCount == 0) return false;
    VisualPosition inlayStartPosition = myEditor.offsetToVisualPosition(offset, false, false);
    return visualPosition.line == inlayStartPosition.line && 
           visualPosition.column >= inlayStartPosition.column && visualPosition.column < inlayStartPosition.column + inlayCount;
  }

  @Nullable
  @Override
  public Inlay getElementAt(@NotNull Point point) {
    if (myInlayTree.size() == 0) return null;

    int offset = myEditor.logicalPositionToOffset(myEditor.xyToLogicalPosition(point));
    List<Inlay> inlays = getInlineElementsInRange(offset, offset);
    if (inlays.isEmpty()) return null;

    VisualPosition startVisualPosition = myEditor.offsetToVisualPosition(offset);
    int x = myEditor.visualPositionToXY(startVisualPosition).x;
    for (Inlay inlay : inlays) {
      int endX = x + inlay.getWidthInPixels();
      if (point.x >= x && point.x < endX) return inlay;
      x = endX;
    }
    return null;
  }

  @Override
  public void addListener(@NotNull Listener listener, @NotNull Disposable disposable) {
    myDispatcher.addListener(listener, disposable);
  }

  private void notifyAdded(InlayImpl inlay) {
    myDispatcher.getMulticaster().onAdded(inlay);
  }

  void notifyChanged(InlayImpl inlay) {
    myDispatcher.getMulticaster().onUpdated(inlay);
  }

  void notifyRemoved(InlayImpl inlay) {
    myDispatcher.getMulticaster().onRemoved(inlay);
  }

  @TestOnly
  public void validateState() {
    for (Inlay inlay : getInlineElementsInRange(0, myEditor.getDocument().getTextLength())) {
      LOG.assertTrue(!DocumentUtil.isInsideSurrogatePair(myEditor.getDocument(), inlay.getOffset()));
    }
  }
}
