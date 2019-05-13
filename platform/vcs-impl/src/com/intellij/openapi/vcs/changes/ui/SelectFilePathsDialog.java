/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class SelectFilePathsDialog extends AbstractSelectFilesDialog {

  private final ChangesTreeImpl<FilePath> myFileList;

  public SelectFilePathsDialog(@NotNull Project project,
                               @NotNull List<? extends FilePath> originalFiles,
                               @Nullable String prompt,
                               @Nullable VcsShowConfirmationOption confirmationOption,
                               @Nullable String okActionName,
                               @Nullable String cancelActionName,
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
