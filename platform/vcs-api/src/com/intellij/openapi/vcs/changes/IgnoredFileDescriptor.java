// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IgnoredFileDescriptor {
  /**
   * Path to file or directory in case if {@link IgnoreSettingsType#FILE} or {@link IgnoreSettingsType#UNDER_DIR}
   * @return full path to file or directory. null in case if {@link IgnoreSettingsType} is {@link IgnoreSettingsType#MASK}
   */
  @Nullable
  @NlsSafe
  String getPath();

  /**
   * Ignored mask represents ignore pattern in ignore files for different VCS (e.g. .gitignore, .hgignore, etc).
   * The recommended way to support all VCS is to choose a common pattern for mask.
   * Note: At the moment, this mask will be written to ignore file without any pre-processing (as it is)
   *
   * @return ignored mask. null in case if {@link IgnoreSettingsType} is not {@link IgnoreSettingsType#MASK}
   */
  @Nullable
  @NlsSafe
  String getMask();

  @NotNull
  IgnoreSettingsType getType();

  /**
   * @deprecated use {@link #matchesFile(FilePath)}
   */
  @Deprecated(forRemoval = true)
  boolean matchesFile(@NotNull VirtualFile file);

  boolean matchesFile(@NotNull FilePath filePath);
}
