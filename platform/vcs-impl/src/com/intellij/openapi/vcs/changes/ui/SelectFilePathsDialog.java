// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;


public class SelectFilePathsDialog extends AbstractSelectFilesDialog {

  private final ChangesTreeImpl<FilePath> myFileList;

  public SelectFilePathsDialog(@NotNull Project project,
                               @NotNull List<? extends FilePath> originalFiles,
                               @Nullable @NlsContexts.Label String prompt,
                               @Nullable VcsShowConfirmationOption confirmationOption,
                               @Nullable @NlsContexts.Button String okActionName,
                               @Nullable @NlsContexts.Button String cancelActionName,
                               boolean showCheckboxes) {
    super(project, false, confirmationOption, prompt);
    myFileList = new ChangesTreeImpl.FilePaths(project, showCheckboxes, true, originalFiles);
    if (okActionName != null) {
      getOKAction().putValue(Action.NAME, okActionName);
    }
    if (cancelActionName != null) {
      getCancelAction().putValue(Action.NAME, cancelActionName);
    }
    init();
  }

  public Collection<FilePath> getSelectedFiles() {
    return myFileList.getIncludedChanges();
  }

  @NotNull
  @Override
  protected ChangesTree getFileList() {
    return myFileList;
  }
}
