package com.intellij.openapi.command.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceByDocument;
import com.intellij.openapi.command.undo.NonUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ExternalChangeAction;

/**
 * author: lesya
 */
class DocumentEditingUndoProvider implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.command.impl.DocumentEditingUndoProvider");

  private final Project myProject;

  DocumentEditingUndoProvider(Project project, EditorFactory editorFactory) {
    MyEditorDocumentListener documentListener = new MyEditorDocumentListener();
    myProject = project;

    EditorEventMulticaster m = editorFactory.getEventMulticaster();
    m.addDocumentListener(documentListener, this);
  }

  public void dispose() {
  }

  private UndoManagerImpl getUndoManager() {
    return (UndoManagerImpl)(myProject == null ? UndoManager.getGlobalInstance() : UndoManager.getInstance(myProject));
  }

  private class MyEditorDocumentListener extends DocumentAdapter {
    public void documentChanged(final DocumentEvent e) {
      final Document document = e.getDocument();

      // if we don't ignore copy's events, we will receive notification
      // for the same event twice (from original document too)
      // and undo will work incorrectly
      if (UndoManagerImpl.isCopy(document)) return;

      if (allEditorsAreViewersFor(document)) return;
      if (!isToRecordActions(document)) return;

      UndoManagerImpl undoManager = getUndoManager();
      if (externalChanges()) {
        createNonUndoableAction(document);
      }
      else {
        LOG.assertTrue(
          undoManager.isInsideCommand(),
          "Undoable actions allowed inside commands only (see com.intellij.openapi.command.CommandProcessor.executeCommand())"
        );
        if (undoManager.isActive()) {
          createUndoableEditorChangeAction(e);
        }
        else {
          createNonUndoableAction(document);
        }
      }
    }

    private boolean isToRecordActions(final Document document) {
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

    private void createUndoableEditorChangeAction(final DocumentEvent e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("document changed:");
        LOG.debug("  offset:" + e.getOffset());
        LOG.debug("  old fragment:" + e.getOldFragment());
        LOG.debug("  new fragment:" + e.getNewFragment());
      }

      EditorChangeAction action = new EditorChangeAction((DocumentEx)e.getDocument(), e.getOffset(),
                                                         e.getOldFragment(), e.getNewFragment(), e.getOldTimeStamp());

      getUndoManager().undoableActionPerformed(action);
    }

    private void createNonUndoableAction(final Document document) {
      UndoManagerImpl undoManager = getUndoManager();
      final DocumentReference ref = DocumentReferenceByDocument.createDocumentReference(document);
      if (!undoManager.documentWasChanged(ref)) return;

      undoManager.undoableActionPerformed(
        new NonUndoableAction() {
          public DocumentReference[] getAffectedDocuments() {
            return new DocumentReference[]{ref};
          }

          public boolean isComplex() {
            return false;
          }
        }
      );
    }

    private boolean externalChanges() {
      return ApplicationManager.getApplication().getCurrentWriteAction(ExternalChangeAction.class) != null;
    }
  }
}
