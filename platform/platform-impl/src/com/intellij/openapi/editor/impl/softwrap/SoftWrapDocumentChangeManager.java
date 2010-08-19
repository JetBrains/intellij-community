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
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Encapsulates the logic of performing necessary changes on soft wraps during target document change.
 *
 * @author Denis Zhdanov
 * @since Jul 7, 2010 2:28:10 PM
 */
public class SoftWrapDocumentChangeManager implements DocumentListener {

  private final TIntHashSet mySoftWrapsToRemoveIndices = new TIntHashSet();

  private final SoftWrapsStorage myStorage;
  private final Editor           myEditor;

  public SoftWrapDocumentChangeManager(@NotNull Editor editor, @NotNull SoftWrapsStorage storage) {
    myStorage = storage;
    myEditor = editor;
  }

  /**
   * Performs {@code 'soft wrap' -> 'hard wrap'} conversion for soft wrap at the given offset if any.
   *
   * @param offset    offset that may point to soft wrap to make hard wrap
   * @return          <code>true</code> if given offset points to soft wrap that was made hard wrap; <code>false</code> otherwise
   */
  public boolean makeHardWrap(int offset) {
    TextChangeImpl softWrap = myStorage.getSoftWrap(offset);
    if (softWrap == null) {
      return false;
    }

    myStorage.removeByIndex(myStorage.getSoftWrapIndex(offset));
    myEditor.getDocument().replaceString(softWrap.getStart(), softWrap.getEnd(), softWrap.getText());
    return true;
  }

  /**
   * Flushes all changes to be applied to {@link SoftWrapsStorage registered soft wraps}.
   * <p/>
   * I.e. the main idea is that current manager listens for document changes but defers soft wraps modification
   * until this method is called.
   */
  public void syncSoftWraps() {
    mySoftWrapsToRemoveIndices.forEach(new TIntProcedure() {
      @Override
      public boolean execute(int value) {
        myStorage.removeByIndex(value);
        return true;
      }
    });
    mySoftWrapsToRemoveIndices.clear();
  }

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
    // Mark soft wraps to be removed.
    Document document = myEditor.getDocument();
    int startLine = document.getLineNumber(event.getOffset());
    int startOffset = document.getLineStartOffset(startLine);
    int endLine = document.getLineNumber(event.getOffset() + event.getOldLength());
    int endOffset = document.getLineEndOffset(endLine);
    markSoftWrapsForDeletion(startOffset, endOffset);

    // Update offsets for soft wraps that remain after the changed line(s).
    applyDocumentChangeDiff(event);
  }

  @Override
  public void documentChanged(DocumentEvent event) {
  }

  private void markSoftWrapsForDeletion(int startOffset, int endOffset) {
    List<TextChangeImpl> softWraps = myStorage.getSoftWraps();
    int index = myStorage.getSoftWrapIndex(startOffset);
    if (index < 0) {
      index = -index - 1;
    }
    for (int i = index; i < softWraps.size(); i++) {
      TextChangeImpl softWrap = softWraps.get(i);
      if (softWrap.getStart() >= endOffset) {
        break;
      }
      mySoftWrapsToRemoveIndices.add(i);
    }
  }

  private void applyDocumentChangeDiff(DocumentEvent event) {
    List<TextChangeImpl> softWraps = myStorage.getSoftWraps();
    
    // We use 'offset + 1' here because soft wrap is represented before the document symbol at the same offset, hence, document
    // modification at particular offset doesn't affect soft wrap registered for the same offset.
    int index = myStorage.getSoftWrapIndex(event.getOffset() + 1);
    if (index < 0) {
      index = -index - 1;
    }

    int diff = event.getNewLength() - event.getOldLength();
    for (int i = index; i < softWraps.size(); i++) {
      TextChangeImpl softWrap = softWraps.get(i);
      if (mySoftWrapsToRemoveIndices.contains(i)) {
        continue;
      }

      // Process only soft wraps not affected by the document change. E.g. there is a possible case that whole document text
      // is replaced, hence, all registered soft wraps are affected by that and no offset advance should be performed.
      if (softWrap.getStart() >= event.getOffset() + event.getOldLength()) {
        softWrap.advance(diff);
      }
    }
  }
}
