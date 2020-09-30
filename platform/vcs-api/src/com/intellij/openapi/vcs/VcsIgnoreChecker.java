// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public interface VcsIgnoreChecker {
  @NotNull VcsKey getSupportedVcs();

  @NotNull IgnoredCheckResult isIgnored(@NotNull VirtualFile vcsRoot, @NotNull Path file);

  @NotNull IgnoredCheckResult isFilePatternIgnored(@NotNull VirtualFile vcsRoot, @NotNull String filePattern);
}
