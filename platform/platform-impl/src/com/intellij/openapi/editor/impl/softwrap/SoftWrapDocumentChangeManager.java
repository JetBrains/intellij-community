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

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the logic of performing necessary changes on soft wraps during target document change.
 *
 * @author Denis Zhdanov
 * @since Jul 7, 2010 2:28:10 PM
 */
public class SoftWrapDocumentChangeManager {

  /**
   * Holds logical lines where soft wraps should be removed.
   * <p/>
   * The general idea is to do the following:
   * <ul>
   *   <li>listen for document changes, mark all soft wraps that belong to modified logical line as <code>'dirty'</code>;</li>
   *   <li>remove soft wraps marked as 'dirty' on repaint;</li>
   * </ul>
   */
  private final TIntHashSet          myDirtyLines      = new TIntHashSet();
  private final List<DeferredChange> myDeferredChanges = new ArrayList<DeferredChange>();

  private final SoftWrapsStorage myStorage;
  private final Editor           myEditor;

  public SoftWrapDocumentChangeManager(Editor editor, SoftWrapsStorage storage) {
    myStorage = storage;
    myEditor = editor;
    init(editor.getDocument());
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
      if (change.startLine >= document.getLineCount()) {
        continue;
      }
      int index = myStorage.getSoftWrapIndex(document.getLineStartOffset(change.startLine));
      if (index < 0) {
        index = -index -1;
      }
      for (int i = index; i < softWraps.size(); i++) {
        TextChangeImpl softWrap = softWraps.get(i);
        if (softWrap.getStart() >= document.getTextLength()) {
          continue;
        }
        int softWrapLine = document.getLineNumber(softWrap.getStart());
        if (myDirtyLines.contains(softWrapLine)) {
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

    myDirtyLines.clear();
    myDeferredChanges.clear();
  }

  private void init(Document document) {
    document.addDocumentListener(new LineOrientedDocumentChangeAdapter() {
      @Override
      public void beforeDocumentChange(int startLine, int endLine, int symbolsDifference) {
      }

      @Override
      public void afterDocumentChange(int startLine, int endLine, int symbolsDifference) {
        updateDeferredData(startLine, endLine, symbolsDifference);
      }
    });
  }

  private void updateDeferredData(int startLine, int endLine, int symbolsDifference) {
    for (int i = startLine; i <= endLine; i++) {
      myDirtyLines.add(i);
    }
    myDeferredChanges.add(new DeferredChange(startLine, endLine, symbolsDifference));
  }

  private static class DeferredChange {
    final int startLine;
    final int endLine;
    final int symbolsDifference;

    DeferredChange(int startLine, int endLine, int symbolsDifference) {
      this.startLine = startLine;
      this.endLine = endLine;
      this.symbolsDifference = symbolsDifference;
    }

    @Override
    public String toString() {
      return startLine + "-" + endLine + ": " + symbolsDifference;
    }
  }
}
