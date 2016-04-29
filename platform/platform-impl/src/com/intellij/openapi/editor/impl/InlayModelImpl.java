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
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.util.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class InlayModelImpl implements InlayModel, Disposable {
  private final EditorImpl myEditor;
  final RangeMarkerTree<InlayImpl> myInlayTree;

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
    };
  }

  @Override
  public void dispose() {
    myInlayTree.dispose();
  }

  @Nullable
  @Override
  public Inlay addInlineElement(int offset, int widthInPixels, @Nullable Inlay.Renderer renderer) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    DocumentEx document = myEditor.getDocument();
    offset = Math.max(0, Math.min(document.getTextLength(), offset));
    InlayImpl inlay = new InlayImpl(myEditor, offset, widthInPixels, renderer);
    myEditor.repaint(offset, offset, false);
    return inlay;
  }

  @NotNull
  @Override
  public List<Inlay> getInlineElementsInRange(int startOffset, int endOffset) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    List<Inlay> result = new ArrayList<>();
    myInlayTree.processOverlappingWith(startOffset, endOffset, inlay -> {
      if (startOffset == endOffset || inlay.getOffset() != endOffset) result.add(inlay);
      return true;
    });
    Collections.sort(result, Comparator.comparingInt(Inlay::getOffset));
    return result;
  }
}
