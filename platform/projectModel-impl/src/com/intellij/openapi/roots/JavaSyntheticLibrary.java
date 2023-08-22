// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class JavaSyntheticLibrary extends ImmutableSyntheticLibrary {

  public JavaSyntheticLibrary(@NotNull String comparisonId,
                              @NotNull List<? extends VirtualFile> sourceRoots,
                              @NotNull List<? extends VirtualFile> binaryRoots,
                              @NotNull Set<? extends VirtualFile> excludedRoots) {
    super(comparisonId, sourceRoots, binaryRoots, excludedRoots, null, null);
  }
}
