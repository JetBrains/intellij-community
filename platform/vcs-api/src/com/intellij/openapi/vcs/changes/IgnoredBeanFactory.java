// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public final class IgnoredBeanFactory {
  private IgnoredBeanFactory() {
  }

  @NotNull
  public static IgnoredFileBean ignoreUnderDirectory(@NotNull @NonNls String path, @Nullable Project p) {
    String correctedPath = (path.endsWith("/") || path.endsWith(File.separator)) ? path : path + "/";
    return new IgnoredFileBean(correctedPath, IgnoreSettingsType.UNDER_DIR, p);
  }

  @NotNull
  public static IgnoredFileBean ignoreFile(@NotNull @NonNls String path, @Nullable Project p) {
    return new IgnoredFileBean(path, IgnoreSettingsType.FILE, p);
  }

  @NotNull
  public static IgnoredFileBean ignoreFile(@NotNull VirtualFile file, @Nullable Project p) {
    if (file.isDirectory()) {
      return ignoreUnderDirectory(file.getPath(), p);
    }

    return ignoreFile(file.getPath(), p);
  }

  @NotNull
  public static IgnoredFileBean withMask(@NotNull String mask) {
    return new IgnoredFileBean(mask);
  }
}
