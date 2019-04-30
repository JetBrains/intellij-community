// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * @author yole
 */
final class SaveCommittingDocumentsVetoer extends FileDocumentSynchronizationVetoer {
  @Override
  public boolean maySaveDocument(@NotNull Document document, boolean isSaveExplicit) {
    final Object beingCommitted = document.getUserData(AbstractCommitter.DOCUMENT_BEING_COMMITTED_KEY);
    if (beingCommitted == VetoSavingCommittingDocumentsAdapter.SAVE_DENIED) {
      return false;
    }
    if (beingCommitted instanceof Project) {
      return ApplicationManager.getApplication().getComponent(VetoSavingCommittingDocumentsAdapter.class)
        .showAllowSaveDialog(Collections.singletonMap(document, (Project)beingCommitted));
    }
    return true;
  }
}
