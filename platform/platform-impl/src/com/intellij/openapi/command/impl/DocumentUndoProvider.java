// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoConstants;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.AbstractFileViewProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DocumentUndoProvider implements Disposable {
  private static final Key<Boolean> UNDOING_EDITOR_CHANGE = Key.create("DocumentUndoProvider.UNDOING_EDITOR_CHANGE");

  private final Project myProject;

  DocumentUndoProvider(@Nullable Project project) {
    myProject = project;

    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new MyEditorDocumentListener(), this);
  }

  @Override
  public void dispose() {
  }

  private UndoManagerImpl getUndoManager() {
    return (UndoManagerImpl)(myProject == null ? UndoManager.getGlobalInstance() : UndoManager.getInstance(myProject));
  }

  public static void startDocumentUndo(@Nullable Document doc) {
    if (doc != null) doc.putUserData(UNDOING_EDITOR_CHANGE, Boolean.TRUE);
  }

  public static void finishDocumentUndo(@Nullable Document doc) {
    if (doc != null) doc.putUserData(UNDOING_EDITOR_CHANGE, null);
  }

  private class MyEditorDocumentListener implements DocumentListener {
    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent e) {
      Document document = e.getDocument();
      if (!shouldProcess(document)) return;

      UndoManagerImpl undoManager = getUndoManager();
      if (undoManager.isActive() && isUndoable(document) && (undoManager.isUndoInProgress() || undoManager.isRedoInProgress()) &&
          document.getUserData(UNDOING_EDITOR_CHANGE) != Boolean.TRUE) {
        throw new IllegalStateException("Do not change documents during undo as it will break undo sequence.");
      }
    }

    @Override
    public void documentChanged(@NotNull final DocumentEvent e) {
      Document document = e.getDocument();
      if (!shouldProcess(document)) return;

      UndoManagerImpl undoManager = getUndoManager();
      if (undoManager.isActive() && isUndoable(document)) {
        registerUndoableAction(e);
      }
      else {
        registerNonUndoableAction(document);
      }
    }

    private boolean shouldProcess(Document document) {
      if (myProject != null && myProject.isDisposed()) return false;
      if (!ApplicationManager.getApplication().isDispatchThread()) return false; // some light document
      return !UndoManagerImpl.isCopy(document) // if we don't ignore copy's events, we will receive notification
             // for the same event twice (from original document too)
             // and undo will work incorrectly
             && shouldRecordActions(document);
    }

    private boolean shouldRecordActions(final Document document) {
      if (document.getUserData(UndoConstants.DONT_RECORD_UNDO) == Boolean.TRUE) return false;

      VirtualFile vFile = FileDocumentManager.getInstance().getFile(document);
      if (vFile == null) return true;
      return vFile.getUserData(AbstractFileViewProvider.FREE_THREADED) != Boolean.TRUE &&
             vFile.getUserData(UndoConstants.DONT_RECORD_UNDO) != Boolean.TRUE;
    }

    private void registerUndoableAction(DocumentEvent e) {
      getUndoManager().undoableActionPerformed(new EditorChangeAction(e));
    }

    private void registerNonUndoableAction(final Document document) {
      DocumentReference ref = DocumentReferenceManager.getInstance().create(document);
      getUndoManager().nonundoableActionPerformed(ref, false);
    }

    private boolean isUndoable(Document document) {
      DocumentReference ref = DocumentReferenceManager.getInstance().create(document);
      VirtualFile file = ref.getFile();

      // Allow undo even from refresh if requested
      if (file != null && file.getUserData(UndoConstants.FORCE_RECORD_UNDO) == Boolean.TRUE) {
        return true;
      }
      return !UndoManagerImpl.isRefresh() || getUndoManager().isUndoOrRedoAvailable(ref);
    }
  }
}
