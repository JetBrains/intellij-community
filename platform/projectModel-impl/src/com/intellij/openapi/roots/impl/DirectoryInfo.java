// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated this is an internal obsolete class, methods from {@link com.intellij.openapi.roots.ProjectFileIndex} should be used istead.
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@ApiStatus.Internal
@Deprecated(forRemoval = true)
public abstract class DirectoryInfo {
  @Nullable
  public abstract VirtualFile getContentRoot();
}
