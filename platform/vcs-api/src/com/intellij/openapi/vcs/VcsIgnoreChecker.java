// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public interface VcsIgnoreChecker {
  ExtensionPointName<VcsIgnoreChecker> EXTENSION_POINT_NAME = new ExtensionPointName<>("com.intellij.vcsIgnoreChecker");

  @NotNull
  VcsKey getSupportedVcs();

  @NotNull
  IgnoredCheckResult isIgnored(@NotNull VirtualFile vcsRoot, @NotNull File file);

  @NotNull
  IgnoredCheckResult isFilePatternIgnored(@NotNull VirtualFile vcsRoot, @NotNull String filePattern);
}
