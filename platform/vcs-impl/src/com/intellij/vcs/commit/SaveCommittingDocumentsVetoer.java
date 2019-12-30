// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class SaveCommittingDocumentsVetoer extends FileDocumentSynchronizationVetoer implements FileDocumentManagerListener {

  private static final Object SAVE_DENIED = new Object();

  @Override
  public void beforeAllDocumentsSaving() {
    Map<Document, Project> documentsToWarn = getDocumentsBeingCommitted();
    if (!documentsToWarn.isEmpty()) {
      boolean allowSave = showAllowSaveDialog(documentsToWarn);
      updateSaveability(documentsToWarn, allowSave);
    }
  }

  @Override
  public boolean maySaveDocument(@NotNull Document document, boolean isSaveExplicit) {
    final Object beingCommitted = document.getUserData(AbstractCommitter.DOCUMENT_BEING_COMMITTED_KEY);
    if (beingCommitted == SAVE_DENIED) {
      return false;
    }
    if (beingCommitted instanceof Project) {
      return showAllowSaveDialog(Collections.singletonMap(document, (Project)beingCommitted));
    }
    return true;
  }

  private static Map<Document, Project> getDocumentsBeingCommitted() {
    Map<Document, Project> documentsToWarn = new HashMap<>();
    for (Document unsavedDocument : FileDocumentManager.getInstance().getUnsavedDocuments()) {
      final Object data = unsavedDocument.getUserData(AbstractCommitter.DOCUMENT_BEING_COMMITTED_KEY);
      if (data instanceof Project) {
        documentsToWarn.put(unsavedDocument, (Project)data);
      }
    }
    return documentsToWarn;
  }

  private static void updateSaveability(Map<Document, Project> documentsToWarn, boolean allowSave) {
    Object newValue = allowSave ? null : SAVE_DENIED;
    for (Document document : documentsToWarn.keySet()) {
      Project oldData = documentsToWarn.get(document);
      //the committing thread could have finished already and file is not being committed anymore
      ((UserDataHolderEx)document).replace(AbstractCommitter.DOCUMENT_BEING_COMMITTED_KEY, oldData, newValue);
    }
  }

  private static boolean showAllowSaveDialog(Map<Document, Project> documentsToWarn) {
    StringBuilder messageBuilder = new StringBuilder("The following " + (documentsToWarn.size() == 1 ? "file is" : "files are") +
                                                     " currently being committed to the VCS. " +
                                                     "Saving now could cause inconsistent data to be committed.\n");
    for (Document document : documentsToWarn.keySet()) {
      final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      messageBuilder.append(FileUtil.toSystemDependentName(file.getPath())).append("\n");
    }
    messageBuilder.append("Save the ").append(documentsToWarn.size() == 1 ? "file" : "files").append(" now?");

    Project project = documentsToWarn.values().iterator().next();
    int rc = Messages.showOkCancelDialog(project, messageBuilder.toString(), "Save Files During Commit", "Save Now", "Postpone Save",
                                         Messages.getQuestionIcon());
    return rc == Messages.OK;
  }
}
