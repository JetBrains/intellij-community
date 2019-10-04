// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author yole
 */
public final class ProjectBaseDirectory {
  public static ProjectBaseDirectory getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectBaseDirectory.class);
  }

  private Path baseDir;

  public Path getBaseDir(Path baseDir) {
    return ObjectUtils.chooseNotNull(getBase(), baseDir);
  }

  /**
   * @deprecated Use {@link #getBase()}
   */
  @Nullable
  @Deprecated
  public VirtualFile getBaseDir() {
    if (baseDir == null) {
      return null;
    }
    return LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(baseDir.toString()));
  }

  @Nullable
  public Path getBase() {
    return baseDir;
  }

  public void setBaseDir(Path baseDir) {
    this.baseDir = baseDir;
  }

  /**
   * @deprecated Use {@link #setBaseDir(Path)}
   */
  @Deprecated
  public void setBaseDir(VirtualFile baseDir) {
    this.baseDir = Paths.get(baseDir.getPath());
  }
}
