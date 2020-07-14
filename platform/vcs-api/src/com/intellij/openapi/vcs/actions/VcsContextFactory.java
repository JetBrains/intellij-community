// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;

public interface VcsContextFactory {
  @NotNull
  VcsContext createCachedContextOn(@NotNull AnActionEvent event);

  @NotNull
  VcsContext createContextOn(@NotNull AnActionEvent event);

  /**
   * Creates a FilePath corresponding to the specified virtual file.
   *
   * @param virtualFile the file for which the FilePath should be created.
   * @return the FilePath instance.
   */
  @NotNull
  FilePath createFilePathOn(@NotNull VirtualFile virtualFile);

  /**
   * Creates a FilePath corresponding to the specified java.io.File.
   *
   * @param file the file for which the FilePath should be created.
   * @return the FilePath instance.
   */
  @NotNull
  FilePath createFilePathOn(@NotNull File file);

  /**
   * Creates a FilePath corresponding to the specified java.io.File. Assumes that the file does not exist in the filesystem
   * and does not try to find the corresponding VirtualFile, which provides a performance benefit.
   *
   * @param file the file for which the FilePath should be created.
   * @param isDirectory whether {@code file} specifies a file or a directory.
   * @return the FilePath instance.
   *
   * @deprecated use {@link #createFilePathOn(File, boolean)}
   */
  @NotNull
  @Deprecated
  FilePath createFilePathOnDeleted(@NotNull File file, boolean isDirectory);

  /**
   * Creates a FilePath corresponding to the specified java.io.File. If the file does not exist, uses the value
   * of the {@code isDirectory} parameter to determine if the file is a directory.
   *
   * @param file the file for which the FilePath should be created.
   * @param isDirectory whether {@code file} specifies a file or a directory.
   * @return the FilePath instance.
   */
  @NotNull FilePath createFilePathOn(@NotNull File file, boolean isDirectory);

  @NotNull FilePath createFilePath(@NotNull Path file, boolean isDirectory);

  /**
   * Creates a FilePath corresponding to the specified path in a VCS repository. Does not try to locate
   * the file in the local filesystem.
   *
   * @param path the repository path for which the FilePath should be created.
   * @param isDirectory whether {@code file} specifies a file or a directory.
   * @return the FilePath instance.
   */
  @NotNull
  FilePath createFilePathOnNonLocal(@NotNull String path, boolean isDirectory);

  /**
   * Creates a FilePath corresponding to a file with the specified name in the specified directory.
   * Assumes that the file does not exist in the filesystem and does not try to find the corresponding VirtualFile,
   * which provides a performance benefit.
   *
   * @param parent the containing directory for the file.
   * @param name   the name of the file.
   * @return the FilePath instance.
   */
  @NotNull
  FilePath createFilePathOn(@NotNull VirtualFile parent, @NotNull String name);

  @NotNull
  FilePath createFilePath(@NotNull VirtualFile parent, @NotNull String fileName, boolean isDirectory);

  @NotNull
  LocalChangeList createLocalChangeList(@NotNull Project project, @NotNull final String name);

  @NotNull FilePath createFilePath(@NotNull String path, boolean isDirectory);

  final class SERVICE {
    private SERVICE() {
    }

    public static VcsContextFactory getInstance() {
      return ApplicationManager.getApplication().getService(VcsContextFactory.class);
    }
  }
}
