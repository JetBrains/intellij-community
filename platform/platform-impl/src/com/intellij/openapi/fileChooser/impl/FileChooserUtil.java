// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser.impl;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class FileChooserUtil {
  private static final String LAST_OPENED_FILE_PATH = "last_opened_file_path";

  @Nullable
  public static VirtualFile getLastOpenedFile(@Nullable Project project) {
    if (project == null) return null;
    String path = PropertiesComponent.getInstance(project).getValue(LAST_OPENED_FILE_PATH);
    return path != null ? LocalFileSystem.getInstance().findFileByPath(path) : null;
  }

  public static void setLastOpenedFile(@Nullable Project project, @Nullable VirtualFile file) {
    if (project == null || project.isDisposed() || file == null) return;
    PropertiesComponent.getInstance(project).setValue(LAST_OPENED_FILE_PATH, file.getPath());
  }

  @Nullable
  public static VirtualFile getFileToSelect(@NotNull FileChooserDescriptor descriptor, @Nullable Project project,
                                            @Nullable VirtualFile toSelect, @Nullable VirtualFile lastPath) {
    boolean chooseDir = descriptor instanceof FileSaverDescriptor;
    VirtualFile result;

    if (toSelect == null && lastPath == null) {
      result = project == null || project.isDefault() ? null : ProjectUtil.guessProjectDir(project);
    }
    else if (toSelect != null && lastPath != null) {
      if (Boolean.TRUE.equals(descriptor.getUserData(PathChooserDialog.PREFER_LAST_OVER_EXPLICIT))) {
        result = lastPath;
      }
      else {
        result = toSelect;
      }
    }
    else if (toSelect == null) {
      result = lastPath;
    }
    else {
      result = toSelect;
    }

    if (result != null) {
      if (chooseDir && !result.isDirectory()) {
        result = result.getParent();
      }
    }
    else if (SystemInfo.isUnix) {
      result = VfsUtil.getUserHomeDir();
    }

    return result;
  }

  @NotNull
  public static List<VirtualFile> getChosenFiles(@NotNull final FileChooserDescriptor descriptor,
                                                 @NotNull final List<? extends VirtualFile> selectedFiles) {
    return ContainerUtil.mapNotNull(selectedFiles, (NullableFunction<VirtualFile, VirtualFile>)file -> file != null && file.isValid() ? descriptor.getFileToSelect(file) : null);
  }
}
