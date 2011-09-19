/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.merge;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Provides custom titles and messages used in MultipleFileMergeDialog and DiffTool invoked from it.
 * @author Kirill Likhodedov
 */
public class MergeDialogCustomizer {

  /**
   * @param files files that have conflicted changes and are shown in the dialog.
   * @return description that is shows above the list of conflicted files. Null (which is equivalent to empty) by default.
   */
  public @Nullable String getMultipleFileMergeDescription(Collection<VirtualFile> files) {
    return null;
  }

  /**
   * @param file file that is merged.
   * @return title of the merge dialog invoked for a 3-way merge of a file.
   */
  public @Nullable String getMergeWindowTitle(VirtualFile file) {
    return VcsBundle.message("multiple.file.merge.request.title", FileUtil.toSystemDependentName(file.getPresentableUrl()));
  }

  /**
   * @param file file that is merged.
   * @return title that is shown above the left panel in the 3-way merge dialog. "Local changes" by default.
   */
  public @Nullable String getLeftPanelTitle(VirtualFile file) {
    return VcsBundle.message("merge.version.title.local.changes");
  }

  /**
   * @param file file that is merged.
   * @return title that is shown above the center panel in the 3-way merge dialog. "Merge result" by default.
   */
  public @Nullable String getCenterPanelTitle(VirtualFile file) {
    return VcsBundle.message("merge.version.title.merge.result");
  }

  /**
   * @param file file that is merged.
   * @param lastRevisionNumber
   * @return title that is shown above the right panel in the 3-way merge dialog. "Changes from server" with the revision number by default.
   */
  public @Nullable String getRightPanelTitle(VirtualFile file, VcsRevisionNumber lastRevisionNumber) {
    if (lastRevisionNumber != null) {
      return VcsBundle.message("merge.version.title.last.version.number", lastRevisionNumber.asString());
    } else {
      return VcsBundle.message("merge.version.title.last.version");
    }
  }

  /**
   * @return The title of multiple files merge dialog.
   * Don't mix with {@link #getMergeWindowTitle(com.intellij.openapi.vfs.VirtualFile)} which is the title of a 3-way merge dialog
   * displayed for a single file.
   */
  public @NotNull String getMultipleFileDialogTitle() {
    return VcsBundle.message("multiple.file.merge.title");
  }

}
