// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes;

import com.intellij.AppTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.changes.ui.CommitHelper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;

import java.util.Map;

public class VetoSavingCommittingDocumentsAdapter implements ApplicationComponent {
  static final Object SAVE_DENIED = new Object();

  private final FileDocumentManager myFileDocumentManager;

  public VetoSavingCommittingDocumentsAdapter(final FileDocumentManager fileDocumentManager) {
    myFileDocumentManager = fileDocumentManager;
  }

  @Override
  public void initComponent() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerListener() {
      @Override
      public void beforeAllDocumentsSaving() {
        Map<Document, Project> documentsToWarn = getDocumentsBeingCommitted();
        if (!documentsToWarn.isEmpty()) {
          boolean allowSave = showAllowSaveDialog(documentsToWarn);
          updateSaveability(documentsToWarn, allowSave);
        }
      }
    });
  }

  private Map<Document, Project> getDocumentsBeingCommitted() {
    Map<Document, Project> documentsToWarn = ContainerUtil.newHashMap();
    for (Document unsavedDocument : myFileDocumentManager.getUnsavedDocuments()) {
      final Object data = unsavedDocument.getUserData(CommitHelper.DOCUMENT_BEING_COMMITTED_KEY);
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
      ((UserDataHolderEx)document).replace(CommitHelper.DOCUMENT_BEING_COMMITTED_KEY, oldData, newValue);
    }
  }

  boolean showAllowSaveDialog(Map<Document, Project> documentsToWarn) {
    StringBuilder messageBuilder = new StringBuilder("The following " + (documentsToWarn.size() == 1 ? "file is" : "files are") +
                                                     " currently being committed to the VCS. " +
                                                     "Saving now could cause inconsistent data to be committed.\n");
    for (Document document : documentsToWarn.keySet()) {
      final VirtualFile file = myFileDocumentManager.getFile(document);
      messageBuilder.append(FileUtil.toSystemDependentName(file.getPath())).append("\n");
    }
    messageBuilder.append("Save the ").append(documentsToWarn.size() == 1 ? "file" : "files").append(" now?");

    Project project = documentsToWarn.values().iterator().next();
    int rc = Messages.showOkCancelDialog(project, messageBuilder.toString(), "Save Files During Commit", "Save Now", "Postpone Save",
                                         Messages.getQuestionIcon());
    return rc == Messages.OK;
  }
}
