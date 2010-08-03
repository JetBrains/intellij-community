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
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the logic of performing necessary changes on soft wraps during target document change.
 *
 * @author Denis Zhdanov
 * @since Jul 7, 2010 2:28:10 PM
 */
public class SoftWrapDocumentChangeManager {

  private final List<DeferredChange> myDeferredChanges = new ArrayList<DeferredChange>();

  private final SoftWrapsStorage myStorage;
  private final Editor           myEditor;

  public SoftWrapDocumentChangeManager(@NotNull Editor editor, @NotNull SoftWrapsStorage storage) {
    myStorage = storage;
    myEditor = editor;
    init(editor.getDocument());
  }

  /**
   * Performs {@code 'soft wrap' -> 'hard wrap'} conversion for the given soft wrap.
   *
   * @param softWrap    soft wrap to make hard wrap
   */
  public void makeHardWrap(@NotNull TextChangeImpl softWrap) {
    myEditor.getDocument().replaceString(softWrap.getStart(), softWrap.getEnd(), softWrap.getText());
  }

  /**
   * Flushes all changes to be applied to {@link SoftWrapsStorage registered soft wraps}.
   * <p/>
   * I.e. the main idea is that current manager listens for document changes but defers soft wraps modification
   * until this method is called.
   */
  public void syncSoftWraps() {
    Document document = myEditor.getDocument();

    TIntHashSet softWrapsToRemoveIndices = new TIntHashSet();

    // Update offsets for soft wraps that remain after the changed line(s).
    List<TextChangeImpl> softWraps = myStorage.getSoftWraps();
    for (DeferredChange change : myDeferredChanges) {
      if (change.startOffset >= document.getTextLength()) {
        continue;
      }
      int index = myStorage.getSoftWrapIndex(change.startOffset);
      if (index < 0) {
        index = -index -1;
      }
      for (int i = index; i < softWraps.size(); i++) {
        TextChangeImpl softWrap = softWraps.get(i);
        if (softWrapsToRemoveIndices.contains(i)) {
          continue;
        }
        if (softWrap.getStart() < change.endOffset || softWrap.getStart() >= document.getTextLength()) {
          softWrapsToRemoveIndices.add(i);
          continue;
        }
        softWrap.advance(change.symbolsDifference);
      }
    }

    // Removes soft wraps from changed lines.
    softWrapsToRemoveIndices.forEach(new TIntProcedure() {
      @Override
      public boolean execute(int value) {
        myStorage.removeByIndex(value);
        return true;
      }
    });

    myDeferredChanges.clear();
  }

  private void init(final Document document) {
    document.addDocumentListener(new LineOrientedDocumentChangeAdapter() {
      @Override
      public void beforeDocumentChange(int startLine, int endLine, int symbolsDifference) {
        myDeferredChanges.add(
          new DeferredChange(document.getLineStartOffset(startLine), document.getLineEndOffset(endLine), symbolsDifference)
        );
      }

      @Override
      public void afterDocumentChange(int startLine, int endLine, int symbolsDifference) {
      }
    });
  }

  private static class DeferredChange {
    final int startOffset;
    final int endOffset;
    final int symbolsDifference;

    DeferredChange(int startOffset, int endOffset, int symbolsDifference) {
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      this.symbolsDifference = symbolsDifference;
    }

    @Override
    public String toString() {
      return startOffset + "-" + endOffset + ": " + symbolsDifference;
    }
  }
}
