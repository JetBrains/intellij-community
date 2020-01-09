// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.ui.Messages.getQuestionIcon;
import static com.intellij.openapi.ui.Messages.showOkCancelDialog;
import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.openapi.vcs.VcsBundle.message;
import static com.intellij.util.containers.ContainerUtil.mapNotNull;
import static java.util.Collections.singletonList;

final class SaveCommittingDocumentsVetoer extends FileDocumentSynchronizationVetoer implements FileDocumentManagerListener {

  private static final Object SAVE_DENIED = new Object();

  @Override
  public void beforeAllDocumentsSaving() {
    Map<Document, Project> documentsToWarn = getDocumentsBeingCommitted();
    if (!documentsToWarn.isEmpty()) {
      Project project = documentsToWarn.values().iterator().next();
      boolean allowSave = confirmSave(project, documentsToWarn.keySet());
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
      return confirmSave((Project)beingCommitted, singletonList(document));
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

  private static boolean confirmSave(@NotNull Project project, @NotNull Collection<Document> documents) {
    Collection<VirtualFile> files = mapNotNull(documents, it -> FileDocumentManager.getInstance().getFile(it));
    String text = message("save.committing.files.confirmation.text", documents.size(), join(files, it -> it.getPresentableUrl(), "\n"));

    return Messages.OK == showOkCancelDialog(
      project,
      text,
      message("save.committing.files.confirmation.title"),
      message("save.committing.files.confirmation.ok"),
      message("save.committing.files.confirmation.cancel"),
      getQuestionIcon()
    );
  }
}
