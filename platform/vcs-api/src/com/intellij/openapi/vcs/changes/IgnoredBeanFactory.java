// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public final class IgnoredBeanFactory {
  private IgnoredBeanFactory() {
  }

  public static @NotNull IgnoredFileBean ignoreUnderDirectory(@NotNull @NlsSafe String path, @Nullable Project p) {
    String correctedPath = (path.endsWith("/") || path.endsWith(File.separator)) ? path : path + "/";
    return new IgnoredFileBean(correctedPath, IgnoreSettingsType.UNDER_DIR, p);
  }

  public static @NotNull IgnoredFileBean ignoreFile(@NotNull @NlsSafe String path, @Nullable Project p) {
    return new IgnoredFileBean(path, IgnoreSettingsType.FILE, p);
  }

  public static @NotNull IgnoredFileBean ignoreFile(@NotNull VirtualFile file, @Nullable Project p) {
    if (file.isDirectory()) {
      return ignoreUnderDirectory(file.getPath(), p);
    }

    return ignoreFile(file.getPath(), p);
  }

  public static @NotNull IgnoredFileBean withMask(@NotNull @NonNls String mask) {
    return new IgnoredFileBean(mask);
  }
}
