// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.JBIterable;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class VcsContextUtil {
  @Nullable
  public static VirtualFile selectedFile(@NotNull DataContext context) {
    return selectedFilesIterable(context).first();
  }

  @NotNull
  public static List<VirtualFile> selectedFiles(@NotNull DataContext context) {
    return selectedFilesIterable(context).toList();
  }

  @NotNull
  public static List<FilePath> selectedFilePaths(@NotNull DataContext context) {
    return selectedFilePathsIterable(context).toList();
  }

  @NotNull
  public static JBIterable<VirtualFile> selectedFilesIterable(@NotNull DataContext context) {
    Iterable<VirtualFile> files = VcsDataKeys.VIRTUAL_FILES.getData(context);
    if (files != null) {
      return JBIterable.from(files).filter(VirtualFile::isInLocalFileSystem);
    }

    return JBIterable.empty();
  }

  @NotNull
  public static JBIterable<FilePath> selectedFilePathsIterable(@NotNull DataContext context) {
    Iterable<FilePath> paths = VcsDataKeys.FILE_PATHS.getData(context);
    if (paths != null) {
      return JBIterable.from(paths);
    }

    FilePath path = VcsDataKeys.FILE_PATH.getData(context);
    if (path != null) {
      return JBIterable.of(path);
    }

    JBIterable<VirtualFile> virtualFiles = selectedFilesIterable(context);
    if (virtualFiles.isNotEmpty()) {
      return virtualFiles.map(VcsUtil::getFilePath);
    }

    return JBIterable.empty();
  }
}
