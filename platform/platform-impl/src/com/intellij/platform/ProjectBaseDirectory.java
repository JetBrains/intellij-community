// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @deprecated Do not use.
 */
@Deprecated(forRemoval = true)
@Service
public final class ProjectBaseDirectory {
  public static ProjectBaseDirectory getInstance(@NotNull Project project) {
    return project.getService(ProjectBaseDirectory.class);
  }

  private Path baseDir;

  public Path getBaseDir(Path baseDir) {
    return ObjectUtils.chooseNotNull(getBase(), baseDir);
  }

  @Nullable
  public Path getBase() {
    return baseDir;
  }

  /**
   * @deprecated Do not use.
   */
  @Deprecated(forRemoval = true)
  public void setBaseDir(VirtualFile baseDir) {
    this.baseDir = Paths.get(baseDir.getPath());
  }
}
