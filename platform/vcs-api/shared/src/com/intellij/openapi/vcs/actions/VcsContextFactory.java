// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;

public interface VcsContextFactory {

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
   * <p>
   *
   * @param file the file for which the FilePath should be created.
   * @return the FilePath instance.
   * @deprecated This method will detect {@link FilePath#isDirectory()} using NIO.
   * Avoid using the method, if {@code isDirectory} is known from context or not important.
   */
  @NotNull
  @Deprecated
  FilePath createFilePathOn(@NotNull File file);

  /**
   * Creates a FilePath corresponding to the specified java.io.File. If the file does not exist, uses the value
   * of the {@code isDirectory} parameter to determine if the file is a directory.
   *
   * @param file        the file for which the FilePath should be created.
   * @param isDirectory whether {@code file} specifies a file or a directory.
   * @return the FilePath instance.
   */
  @NotNull FilePath createFilePathOn(@NotNull File file, boolean isDirectory);

  @NotNull FilePath createFilePath(@NotNull Path file, boolean isDirectory);

  /**
   * Creates a FilePath corresponding to the specified path in a VCS repository. Does not try to locate
   * the file in the local filesystem.
   *
   * @param path        the repository path for which the FilePath should be created.
   * @param isDirectory whether {@code file} specifies a file or a directory.
   * @return the FilePath instance.
   */
  @NotNull
  FilePath createFilePathOnNonLocal(@NotNull @NonNls String path, boolean isDirectory);

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
  FilePath createFilePathOn(@NotNull VirtualFile parent, @NotNull @NonNls String name);

  @NotNull
  FilePath createFilePath(@NotNull VirtualFile parent, @NotNull @NonNls String fileName, boolean isDirectory);

  @NotNull
  LocalChangeList createLocalChangeList(@NotNull Project project, final @NotNull @NlsSafe String name);

  @NotNull FilePath createFilePath(@NotNull @NonNls String path, boolean isDirectory);

  static VcsContextFactory getInstance() {
    return ApplicationManager.getApplication().getService(VcsContextFactory.class);
  }

  /**
   * @deprecated use {@link VcsContextFactory#getInstance()} instead
   */
  @Deprecated(forRemoval = true)
  final class SERVICE {

    private SERVICE() {
    }

    public static VcsContextFactory getInstance() {
      return VcsContextFactory.getInstance();
    }
  }
}
