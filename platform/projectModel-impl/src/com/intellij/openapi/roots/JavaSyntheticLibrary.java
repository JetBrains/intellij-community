// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public class JavaSyntheticLibrary extends ImmutableSyntheticLibrary {

  public JavaSyntheticLibrary(@NotNull Collection<VirtualFile> sourceRoots,
                              @NotNull Collection<VirtualFile> binaryRoots,
                              @NotNull Set<VirtualFile> excludedRoots,
                              @Nullable Condition<VirtualFile> excludeCondition) {
    super(sourceRoots, binaryRoots, excludedRoots, excludeCondition);
  }
}
