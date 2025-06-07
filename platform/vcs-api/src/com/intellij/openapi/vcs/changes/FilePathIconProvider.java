// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


/**
 * Customize icons for files in VCS dialogs.
 * Similar to {@link com.intellij.ide.IconProvider} and {@link FileIconProvider} but doesn't depend on PSI and VirtualFile`s
 *
 * @author Plyashkun
 */
public interface FilePathIconProvider {
  ExtensionPointName<FilePathIconProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.openapi.vcs.changes.ui.filePathIconProvider");

  /**
   * @deprecated Do not call this method directly because the `isDirectory` property of filePath is not reliable enough.
   * Use {@link #getIcon(FilePath, boolean, Project)} instead.
   *
   * @param filePath file for which icon is shown
   * @param project  current opened project
   * @return {@code null} if there is no appropriate icon for given file path
   */
  @Deprecated(forRemoval = true)
  default @Nullable Icon getIcon(@NotNull FilePath filePath, @Nullable Project project) {
    throw new UnsupportedOperationException("Implementors must override at least one of getIcon() methods");
  }

  /**
   * @param filePath file for which icon is shown
   * @param isDirectory flag that indicates if file is directory or not
   * @param project  current opened project
   * @return {@code null} if there is no appropriate icon for given file path
   */
  default @Nullable Icon getIcon(@NotNull FilePath filePath, boolean isDirectory, @Nullable Project project) {
    return getIcon(filePath, project);
  }
}
