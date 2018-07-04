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

import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Provides custom titles and messages used in MultipleFileMergeDialog and DiffTool invoked from it.
 */
public class MergeDialogCustomizer {

  /**
   * @param files files that have conflicted changes and are shown in the dialog.
   * @return description that is shows above the list of conflicted files. Null (which is equivalent to empty) by default.
   */
  @Nullable public String getMultipleFileMergeDescription(@NotNull Collection<VirtualFile> files) {
    return null;
  }

  /**
   * @param file file that is merged.
   * @return title of the merge dialog invoked for a 3-way merge of a file.
   */
  @Nullable public String getMergeWindowTitle(@NotNull VirtualFile file) {
    return VcsBundle.message("multiple.file.merge.request.title", FileUtil.toSystemDependentName(file.getPresentableUrl()));
  }

  /**
   * @param file file that is merged.
   * @return title that is shown above the left panel in the 3-way merge dialog. "Local changes" by default.
   */
  @Nullable public String getLeftPanelTitle(@NotNull VirtualFile file) {
    return DiffBundle.message("merge.version.title.our");
  }

  /**
   * @param file file that is merged.
   * @return title that is shown above the center panel in the 3-way merge dialog. "Merge result" by default.
   */
  @Nullable public String getCenterPanelTitle(@NotNull VirtualFile file) {
    return DiffBundle.message("merge.version.title.base");
  }

  /**
   * @param file           file that is being merged.
   * @param revisionNumber revision number of the file at the right, can be null if unknown.
   * @return title that is shown above the right panel in the 3-way merge dialog. "Changes from server" with the revision number by default.
   */
  @Nullable public String getRightPanelTitle(@NotNull VirtualFile file, @Nullable VcsRevisionNumber revisionNumber) {
    if (revisionNumber != null) {
      return DiffBundle.message("merge.version.title.their.with.revision", revisionNumber.asString());
    } else {
      return DiffBundle.message("merge.version.title.their");
    }
  }

  /**
   * @return The title of multiple files merge dialog.
   * Don't mix with {@link #getMergeWindowTitle(VirtualFile)} which is the title of a 3-way merge dialog displayed for a single file.
   */
  @NotNull public String getMultipleFileDialogTitle() {
    return VcsBundle.message("multiple.file.merge.title");
  }
}
