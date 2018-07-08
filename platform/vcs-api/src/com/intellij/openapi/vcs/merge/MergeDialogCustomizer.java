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
 * Provides custom titles and messages used in the MultipleFileMergeDialog and DiffTools invoked from it.
 */
public class MergeDialogCustomizer {

  /**
   * Returns the description that is shown above the list of conflicted files.
   *
   * @param files the files that have conflicted changes and are shown in the dialog.
   */
  @NotNull
  public String getMultipleFileMergeDescription(@NotNull Collection<VirtualFile> files) {
    return "";
  }

  /**
   * Returns the title of the merge dialog invoked for a 3-way merge of a file (after pressing the "Merge" button).
   *
   * @param file the file that is being merged.
   */
  @NotNull
  public String getMergeWindowTitle(@NotNull VirtualFile file) {
    return VcsBundle.message("multiple.file.merge.request.title", FileUtil.toSystemDependentName(file.getPresentableUrl()));
  }

  /**
   * Returns the title that is shown above the left panel in the 3-way merge dialog.
   *
   * @param file the file that is being merged.
   */
  @NotNull
  public String getLeftPanelTitle(@NotNull VirtualFile file) {
    return DiffBundle.message("merge.version.title.our");
  }

  /**
   * Returns the title that is shown above the center panel in the 3-way merge dialog.
   *
   * @param file the file that is being merged.
   */
  @NotNull
  public String getCenterPanelTitle(@NotNull VirtualFile file) {
    return DiffBundle.message("merge.version.title.base");
  }

  /**
   * Returns the title that is shown above the right panel in the 3-way merge dialog.
   *
   * @param file           the file that is being merged.
   * @param revisionNumber the revision number of the file at the right. Can be null if unknown.
   */
  @NotNull
  public String getRightPanelTitle(@NotNull VirtualFile file, @Nullable VcsRevisionNumber revisionNumber) {
    return revisionNumber != null
           ? DiffBundle.message("merge.version.title.their.with.revision", revisionNumber.asString())
           : DiffBundle.message("merge.version.title.their");
  }

  /**
   * Returns the title of the multiple files merge dialog.
   * <br/><br/>
   * Don't mix with {@link #getMergeWindowTitle(VirtualFile)} which is the title of the 3-way merge dialog displayed for a single file.
   */
  @NotNull
  public String getMultipleFileDialogTitle() {
    return VcsBundle.message("multiple.file.merge.title");
  }
}
