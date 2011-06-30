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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 05.09.2006
 * Time: 20:07:21
 */
package com.intellij.openapi.vcs.changes;

import com.intellij.AppTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.changes.ui.CommitHelper;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VetoSavingCommittingDocumentsAdapter implements ApplicationComponent, FileDocumentSynchronizationVetoListener {
  private static final Object SAVE_DENIED = new Object();

  private final FileDocumentManager myFileDocumentManager;

  public VetoSavingCommittingDocumentsAdapter(final FileDocumentManager fileDocumentManager) {
    myFileDocumentManager = fileDocumentManager;
  }

  public void beforeDocumentSaving(Document document) throws VetoDocumentSavingException {
    final Object beingCommitted = document.getUserData(CommitHelper.DOCUMENT_BEING_COMMITTED_KEY);
    if (beingCommitted == SAVE_DENIED) {
      throw new VetoDocumentSavingException();
    }
    if (beingCommitted instanceof Project) {
      boolean allowSave = showAllowSaveDialog((Project) beingCommitted, Collections.singletonList(document));
      if (!allowSave) {
        throw new VetoDocumentSavingException();
      }
    }
  }


  public void beforeFileContentReload(VirtualFile file, Document document) throws VetoDocumentReloadException {
  }

  @NonNls @NotNull
  public String getComponentName() {
    return "VetoSavingComittingDocumentsAdapter";
  }

  public void initComponent() {
    myFileDocumentManager.addFileDocumentSynchronizationVetoer(this);
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerAdapter() {
      @Override
      public void beforeAllDocumentsSaving() {
        List<Document> documentsToWarn = new ArrayList<Document>();
        final Document[] unsavedDocuments = myFileDocumentManager.getUnsavedDocuments();
        Project commitOwnerProject = null;
        for (Document unsavedDocument : unsavedDocuments) {
          final Object data = unsavedDocument.getUserData(CommitHelper.DOCUMENT_BEING_COMMITTED_KEY);
          if (data instanceof Project) {
            commitOwnerProject = (Project) data;
            documentsToWarn.add(unsavedDocument);
          }
        }
        if (!documentsToWarn.isEmpty()) {
          boolean allowSave = showAllowSaveDialog(commitOwnerProject, documentsToWarn);
          for (Document document : documentsToWarn) {
            document.putUserData(CommitHelper.DOCUMENT_BEING_COMMITTED_KEY, allowSave ? null : SAVE_DENIED);
          }
        }
      }
    });
  }

  public void disposeComponent() {
    myFileDocumentManager.removeFileDocumentSynchronizationVetoer(this);
  }

  private boolean showAllowSaveDialog(Project project, List<Document> documentsToWarn) {
    StringBuilder messageBuilder = new StringBuilder("The following " + (documentsToWarn.size() == 1 ? "file is" : "files are") +
                                                     " currently being committed to the VCS. " +
                                                     "Saving now could cause inconsistent data to be committed.\n");
    for (Document document : documentsToWarn) {
      final VirtualFile file = myFileDocumentManager.getFile(document);
      messageBuilder.append(FileUtil.toSystemDependentName(file.getPath())).append("\n");
    }
    messageBuilder.append("Save the ").append(documentsToWarn.size() == 1 ? "file" : "files").append(" now?");

    int rc = Messages.showDialog(project, messageBuilder.toString(), "Save Files During Commit", new String[] { "Save Now", "Postpone Save" },
                                 0, Messages.getQuestionIcon());
    return rc == 0;
  }
}
