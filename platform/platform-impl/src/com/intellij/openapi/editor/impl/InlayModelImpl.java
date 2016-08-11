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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.util.Getter;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class InlayModelImpl implements InlayModel, Disposable {
  public static final Comparator<Inlay> INLAY_COMPARATOR = Comparator.comparingInt(Inlay::getOffset)
                                                                     .thenComparingInt(i -> ((InlayImpl)i).myOriginalOffset);

  private final EditorImpl myEditor;
  private final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);
  final RangeMarkerTree<InlayImpl> myInlayTree;
  boolean myStickToLargerOffsetsOnUpdate = true;

  InlayModelImpl(@NotNull EditorImpl editor) {
    myEditor = editor;
    myInlayTree = new RangeMarkerTree<InlayImpl>(editor.getDocument()) {
      @NotNull
      @Override
      protected RMNode<InlayImpl> createNewNode(@NotNull InlayImpl key, int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {
        return new RMNode<InlayImpl>(this, key, start, end, greedyToLeft, greedyToRight) {
          @Override
          protected Getter<InlayImpl> createGetter(@NotNull InlayImpl interval) {
            return interval;
          }
        };
      }

      @Override
      void fireBeforeRemoved(@NotNull InlayImpl markerEx, @NotNull @NonNls Object reason) {
        myDispatcher.getMulticaster().onRemoved(markerEx);
      }
    };
    myEditor.getDocument().addDocumentListener(new PrioritizedDocumentListener() {
      @Override
      public int getPriority() {
        return EditorDocumentPriorities.INLAY_MODEL;
      }

      @Override
      public void beforeDocumentChange(DocumentEvent event) {
        int offset = event.getOffset();
        if (event.getOldLength() == 0 &&
            offset == myEditor.getCaretModel().getOffset() &&
            !getElementsInRange(offset, offset, Inlay.Type.INLINE).isEmpty() &&
            myEditor.getCaretModel().getVisualPosition().equals(myEditor.offsetToVisualPosition(offset, false, false))) {
          myStickToLargerOffsetsOnUpdate = true;
        }
      }

      @Override
      public void documentChanged(DocumentEvent event) {
        myStickToLargerOffsetsOnUpdate = false;
      }
    }, this);
  }

  @Override
  public void dispose() {
    myInlayTree.dispose();
  }

  @Nullable
  @Override
  public Inlay addElement(int offset, @NotNull Inlay.Type type, @NotNull Inlay.Renderer renderer) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    DocumentEx document = myEditor.getDocument();
    offset = Math.max(0, Math.min(document.getTextLength(), offset));
    InlayImpl inlay = new InlayImpl(myEditor, offset, type, renderer);
    myDispatcher.getMulticaster().onAdded(inlay);
    return inlay;
  }

  @NotNull
  @Override
  public List<Inlay> getElementsInRange(int startOffset, int endOffset, @NotNull Inlay.Type type) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    List<Inlay> result = new ArrayList<>();
    myInlayTree.processOverlappingWith(startOffset, endOffset, inlay -> {
      if (inlay.getType() == type && (startOffset == endOffset || inlay.getOffset() != endOffset)) result.add(inlay);
      return true;
    });
    Collections.sort(result, INLAY_COMPARATOR);
    return result;
  }

  @NotNull
  @Override
  public List<Inlay> getVisibleElements(@NotNull Inlay.Type type) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    List<Inlay> result = new ArrayList<>();
    myInlayTree.process(inlay -> {
      if (inlay.getType() == type && !myEditor.getFoldingModel().isOffsetCollapsed(inlay.getOffset())) result.add(inlay);
      return true;
    });
    Collections.sort(result, INLAY_COMPARATOR);
    return result;
  }

  @NotNull
  @Override
  public List<Inlay> getLineExtendingElements() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    List<Inlay> result = new ArrayList<>();
    myInlayTree.process(inlay -> {
      if (inlay.getType() == Inlay.Type.BLOCK || inlay.getHeightInPixels() > myEditor.getLineHeight()) {
        result.add(inlay);
      }
      return true;
    });
    Collections.sort(result, INLAY_COMPARATOR);
    return result;
  }

  @Override
  public boolean hasInlayAt(int offset) {
    return !getElementsInRange(offset, offset, Inlay.Type.INLINE).isEmpty();
  }

  @Override
  public boolean hasInlayAt(@NotNull VisualPosition visualPosition) {
    int offset = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(visualPosition));
    List<Inlay> inlays = getElementsInRange(offset, offset, Inlay.Type.INLINE);
    if (inlays.isEmpty()) return false;
    VisualPosition inlayStartPosition = myEditor.offsetToVisualPosition(offset, false, false);
    return visualPosition.equals(inlayStartPosition);
  }

  @Override
  public void addListener(@NotNull Listener listener, @NotNull Disposable disposable) {
    myDispatcher.addListener(listener, disposable);
  }
}
