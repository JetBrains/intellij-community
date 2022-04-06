// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.impl;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class FileChooserUtil {
  private static final String LAST_OPENED_FILE_PATH = "last_opened_file_path";

  @Nullable
  public static VirtualFile getLastOpenedFile(@Nullable Project project) {
    String path;
    if (project == null) {
      path = PropertiesComponent.getInstance().getValue(LAST_OPENED_FILE_PATH);
    }
    else {
      path = PropertiesComponent.getInstance(project).getValue(LAST_OPENED_FILE_PATH);
    }
    return path != null ? LocalFileSystem.getInstance().findFileByPath(path) : null;
  }

  /**
   * @deprecated Use {@link #setLastOpenedFile(Project, Path)}
   */
  @Deprecated(forRemoval = true)
  public static void setLastOpenedFile(@Nullable Project project, @Nullable VirtualFile file) {
    if (file == null) {
      return;
    }
    if (project == null) {
      PropertiesComponent.getInstance().setValue(LAST_OPENED_FILE_PATH, file.getPath());
    }
    else if (!project.isDisposed()) {
      PropertiesComponent.getInstance(project).setValue(LAST_OPENED_FILE_PATH, file.getPath());
    }
  }

  public static void setLastOpenedFile(@Nullable Project project, @NotNull Path file) {
    if (project == null) {
      PropertiesComponent.getInstance().setValue(LAST_OPENED_FILE_PATH, FileUtil.toSystemIndependentName(file.toString()));
    }
    else if (!project.isDisposed()) {
      PropertiesComponent.getInstance(project).setValue(LAST_OPENED_FILE_PATH, FileUtil.toSystemIndependentName(file.toString()));
    }
  }

  @ApiStatus.Internal
  public static @Nullable VirtualFile getFileToSelect(@NotNull FileChooserDescriptor descriptor, @Nullable Project project, @Nullable VirtualFile toSelect) {
    VirtualFile result = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(pathToSelect(descriptor, project, toSelect)));
    return result != null && descriptor instanceof FileSaverDescriptor && !result.isDirectory() ? result.getParent() : result;
  }

  @ApiStatus.Internal
  static @Nullable Path getInitialPath(@NotNull FileChooserDescriptor descriptor, @Nullable Project project, @Nullable VirtualFile toSelect) {
    Path result = NioFiles.toPath(FileUtil.toSystemDependentName(pathToSelect(descriptor, project, toSelect)));
    return result != null && descriptor instanceof FileSaverDescriptor && !Files.isDirectory(result) ? result.getParent() : result;
  }

  private static String pathToSelect(FileChooserDescriptor descriptor, @Nullable Project project, @Nullable VirtualFile toSelect) {
    String last = (project != null ? PropertiesComponent.getInstance(project) : PropertiesComponent.getInstance()).getValue(LAST_OPENED_FILE_PATH);
    if (last != null && (toSelect == null || descriptor.getUserData(PathChooserDialog.PREFER_LAST_OVER_EXPLICIT) == Boolean.TRUE)) {
      return last;
    }

    if (toSelect != null) {
      return toSelect.getPath();
    }

    if (project != null && !project.isDefault()) {
      VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
      if (projectDir != null) {
        return projectDir.getPath();
      }
    }

    return SystemProperties.getUserHome();
  }

  @NotNull
  public static List<VirtualFile> getChosenFiles(@NotNull final FileChooserDescriptor descriptor,
                                                 @NotNull final List<? extends VirtualFile> selectedFiles) {
    return ContainerUtil.mapNotNull(selectedFiles, file -> file != null && file.isValid() ? descriptor.getFileToSelect(file) : null);
  }
}
