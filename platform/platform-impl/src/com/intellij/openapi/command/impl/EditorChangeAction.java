
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceByDocument;
import com.intellij.openapi.command.undo.DocumentReferenceByVirtualFile;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.impl.FileStatusManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NonNls;

class EditorChangeAction implements UndoableAction {
  private final DocumentEx myDocument;
  private final VirtualFile myDocumentFile;
  private final int myOffset;
  private final CharSequence myOldString;
  private final CharSequence myNewString;
  private final long myTimeStamp;
  private final boolean myBulkUpdate;

  public EditorChangeAction(DocumentEx document, int offset,
                            CharSequence oldString, CharSequence newString,
                            long oldTimeStamp) {
    myDocumentFile = FileDocumentManager.getInstance().getFile(document);
    if (myDocumentFile != null) {
      myDocument = null;
    }
    else {
      myDocument = document;
    }

    myOffset = offset;
    myOldString = oldString == null ? "" : oldString;
    myNewString = newString == null ? "" : newString;
    myTimeStamp = oldTimeStamp;
    myBulkUpdate = document.isInBulkUpdate();
  }

  public void undo() {
    exchangeStrings(myNewString, myOldString);
    getDocument().setModificationStamp(myTimeStamp);
    fireFileStatusChanged();
  }

  private void fireFileStatusChanged() {
    VirtualFile file = myDocumentFile != null ? myDocumentFile : FileDocumentManager.getInstance().getFile(getDocument());
    if (file == null || file instanceof LightVirtualFile) return;

    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      final FileStatusManagerImpl fileStatusManager = (FileStatusManagerImpl)FileStatusManager.getInstance (project);
      fileStatusManager.refreshFileStatusFromDocument(file, getDocument());
    }
  }

  private void exchangeStrings(CharSequence newString, CharSequence oldString) {
    final DocumentEx document = getDocument();
    
    if (myBulkUpdate) document.setInBulkUpdate(true);
    if (newString.length() > 0 && oldString.length() == 0){
      document.deleteString(myOffset, myOffset + newString.length());
    }
    else if (oldString.length() > 0 && newString.length() == 0){
      document.insertString(myOffset, oldString);
    }
    else if (oldString.length() > 0 && newString.length() > 0){
      document.replaceString(myOffset, myOffset + newString.length(), oldString);
    }
    if (myBulkUpdate) document.setInBulkUpdate(false);
  }

  public void redo() {
    exchangeStrings(myOldString, myNewString);
  }

  public DocumentReference[] getAffectedDocuments() {
    final DocumentReference ref = myDocument != null
                                  ? DocumentReferenceByDocument.createDocumentReference(myDocument)
                                  : new DocumentReferenceByVirtualFile(myDocumentFile);
    return new DocumentReference[]{ref};
  }

  public boolean isComplex() {
    return false;
  }

  private DocumentEx getDocument() {
    if (myDocument != null) return myDocument;
    if (!myDocumentFile.isValid()) {
      final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(myDocumentFile.getUrl());
      if (file != null) {
        return (DocumentEx)FileDocumentManager.getInstance().getDocument(file);
      }
    }
    return (DocumentEx)FileDocumentManager.getInstance().getDocument(myDocumentFile);
  }

  @NonNls
  public String toString() {
    return "editor change: '"+myOldString+"' to '"+myNewString+"'";
  }
}

