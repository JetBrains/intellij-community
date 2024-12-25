// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class FileChooserUtil {
  private static final String LAST_OPENED_FILE = "last_opened_file_path";
  private static final String RECENT_FILES = "file.chooser.recent.files";
  private static final int RECENT_FILES_LIMIT = 30;

  @ApiStatus.Internal
  public static boolean isLastOpenedPathSet(@Nullable Project project) {
    String last = getLastOpenedFilePath(project);
    return last != null;
  }

  public static @Nullable VirtualFile getLastOpenedFile(@Nullable Project project) {
    String last = getLastOpenedFilePath(project);
    return last != null ? LocalFileSystem.getInstance().findFileByPath(last) : null;
  }

  private static @NonNls @Nullable String getLastOpenedFilePath(@Nullable Project project) {
    return (project != null ? PropertiesComponent.getInstance(project) : PropertiesComponent.getInstance()).getValue(LAST_OPENED_FILE);
  }

  /** @deprecated Use {@link #setLastOpenedFile(Project, Path)} */
  @Deprecated(forRemoval = true)
  public static void setLastOpenedFile(@Nullable Project project, @Nullable VirtualFile file) {
    if (file != null) {
      setLastOpenedPath(project, file.getPath());
    }
  }

  public static void setLastOpenedFile(@Nullable Project project, @NotNull Path file) {
    setLastOpenedPath(project, FileUtil.toSystemIndependentName(file.toString()));
  }

  private static void setLastOpenedPath(@Nullable Project project, String path) {
    if (project == null) {
      PropertiesComponent.getInstance().setValue(LAST_OPENED_FILE, path);
    }
    else if (!project.isDisposed()) {
      PropertiesComponent.getInstance(project).setValue(LAST_OPENED_FILE, path);
    }
  }

  @ApiStatus.Internal
  public static @NotNull List<@SystemIndependent String> getRecentPaths() {
    List<String> values = PropertiesComponent.getInstance().getList(RECENT_FILES);
    return values != null ? values : List.of();
  }

  @ApiStatus.Internal
  public static void updateRecentPaths(@Nullable Project project, @NotNull VirtualFile file) {
    var fs = file.getFileSystem();
    if (fs instanceof ArchiveFileSystem) {
      file = ((ArchiveFileSystem)fs).getLocalByEntry(file);
      if (file == null) return;
    }

    var path = file.getPath();
    setLastOpenedPath(project, path);
    var recent = Stream.concat(Stream.of(path), getRecentPaths().stream())
      .distinct()
      .limit(RECENT_FILES_LIMIT)
      .collect(Collectors.toList());
    PropertiesComponent.getInstance().setList(RECENT_FILES, recent);
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
    String last = getLastOpenedFilePath(project);
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

  public static @Unmodifiable @NotNull List<VirtualFile> getChosenFiles(@NotNull FileChooserDescriptor descriptor, @NotNull List<VirtualFile> selectedFiles) {
    return ContainerUtil.mapNotNull(selectedFiles, file -> file != null && file.isValid() ? descriptor.getFileToSelect(file) : null);
  }
}
