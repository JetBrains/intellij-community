// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Provides content, masks for VCS native ignore files (e.g., {@code .gitignore}, {@code .hgignore}).
 * Every plugin which has ignore files should implement it to contribute own ignores to VCS.
 */
@ApiStatus.Experimental
public interface IgnoredFileProvider {
  ExtensionPointName<IgnoredFileProvider> IGNORE_FILE = new ExtensionPointName<>("com.intellij.ignoredFileProvider");

  boolean isIgnoredFile(@NotNull Project project, @NotNull FilePath filePath);

  @NotNull
  Set<IgnoredFileDescriptor> getIgnoredFiles(@NotNull Project project);

  @NotNull
  @NonNls
  String getIgnoredGroupDescription();
}