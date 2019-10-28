// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.merge;

import com.intellij.diff.DiffEditorTitleCustomizer;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Provides custom titles and messages used in the MultipleFileMergeDialog and DiffTools invoked from it.
 */
@ApiStatus.OverrideOnly
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
   * @see #getLeftTitleCustomizer
   */
  @NotNull
  public String getLeftPanelTitle(@NotNull VirtualFile file) {
    return DiffBundle.message("merge.version.title.our");
  }

  /**
   * Returns the title that is shown above the center panel in the 3-way merge dialog.
   *
   * @param file the file that is being merged.
   * @see #getCenterTitleCustomizer
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
   * @see #getRightTitleCustomizer
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

  /**
   * Allows to override the names of the columns of the multiple files merge dialog, defined in {@link MergeSession#getMergeInfoColumns()}.
   * <br/><br/>
   * Return the column names, matching the order of columns defined in the MergeSession.
   * Return {@code null} to use names from {@link MergeSession#getMergeInfoColumns()}.
   */
  @Nullable
  public List<String> getColumnNames() {
    return null;
  }

  /**
   * Allows to customize the left diff editor title in the 3-way merge dialog using {@link DiffEditorTitleCustomizer}.
   * This method takes precedence over {@link #getLeftPanelTitle}, which is used as a fallback only if this method returns {@code null}.
   */
  @ApiStatus.Experimental
  @Nullable
  public DiffEditorTitleCustomizer getLeftTitleCustomizer(@NotNull FilePath file) {
    return null;
  }

  /**
   * Allows to customize the center diff editor title in the 3-way merge dialog using {@link DiffEditorTitleCustomizer}.
   * This method takes precedence over {@link #getCenterPanelTitle}, which is used as a fallback only if this method returns {@code null}.
   */
  @ApiStatus.Experimental
  @Nullable
  public DiffEditorTitleCustomizer getCenterTitleCustomizer(@NotNull FilePath file) {
    return null;
  }

  /**
   * Allows to customize the right diff editor title in the 3-way merge dialog using {@link DiffEditorTitleCustomizer}.
   * This method takes precedence over {@link #getRightPanelTitle}, which is used as a fallback only if this method returns {@code null}.
   */
  @ApiStatus.Experimental
  @Nullable
  public DiffEditorTitleCustomizer getRightTitleCustomizer(@NotNull FilePath file) {
    return null;
  }
}
