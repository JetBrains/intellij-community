/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.command.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ExternalChangeAction;

class DocumentUndoProvider implements Disposable {
  private final Project myProject;

  DocumentUndoProvider(Project project) {
    MyEditorDocumentListener documentListener = new MyEditorDocumentListener();
    myProject = project;

    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(documentListener, this);
  }

  public void dispose() {
  }

  private UndoManagerImpl getUndoManager() {
    return (UndoManagerImpl)(myProject == null ? UndoManager.getGlobalInstance() : UndoManager.getInstance(myProject));
  }

  private class MyEditorDocumentListener extends DocumentAdapter {
    public void documentChanged(final DocumentEvent e) {
      Document document = e.getDocument();

      // if we don't ignore copy's events, we will receive notification
      // for the same event twice (from original document too)
      // and undo will work incorrectly
      if (UndoManagerImpl.isCopy(document)) return;

      if (allEditorsAreViewersFor(document)) return;
      if (!shouldRecordActions(document)) return;

      UndoManagerImpl undoManager = getUndoManager();
      if (!undoManager.isActive() || !isUndoable(document)) {
        registerNonUndoableAction(document);
        return;
      }

      registerUndoableAction(e);
    }

    private boolean shouldRecordActions(final Document document) {
      if (document.getUserData(UndoManager.DONT_RECORD_UNDO) == Boolean.TRUE) return false;

      final VirtualFile vFile = FileDocumentManager.getInstance().getFile(document);
      return vFile == null || vFile.getUserData(UndoManager.DONT_RECORD_UNDO) != Boolean.TRUE;
    }

    private boolean allEditorsAreViewersFor(Document document) {
      Editor[] editors = EditorFactory.getInstance().getEditors(document);
      if (editors.length == 0) return false;
      for (Editor editor : editors) {
        if (!editor.isViewer()) return false;
      }
      return true;
    }

    private void registerUndoableAction(DocumentEvent e) {
      getUndoManager().undoableActionPerformed(new EditorChangeAction((DocumentEx)e.getDocument(),
                                                                      e.getOffset(),
                                                                      e.getOldFragment(),
                                                                      e.getNewFragment(),
                                                                      e.getOldTimeStamp()));
    }

    private void registerNonUndoableAction(final Document document) {
      DocumentReference ref = DocumentReferenceManager.getInstance().create(document);
      getUndoManager().nonundoableActionPerformed(ref, false);
    }

    private boolean isUndoable(Document document) {
      boolean isFromRefresh = ApplicationManager.getApplication().hasWriteAction(ExternalChangeAction.class);
      if (!isFromRefresh) return true;

      return getUndoManager().isUndoOrRedoAvailable(DocumentReferenceManager.getInstance().create(document));
    }
  }
}
