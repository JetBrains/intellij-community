// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


/**
 * Implement this interface and register the implementation as {@code directoryIndexExcludePolicy} extension in plugin.xml to specify files
 * and directories which should be automatically
 * <a href="https://www.jetbrains.com/help/idea/content-roots.html#exclude-files-folders">excluded</a> from the project and its modules.
 */
public interface DirectoryIndexExcludePolicy {
  ProjectExtensionPointName<DirectoryIndexExcludePolicy> EP_NAME =
    new ProjectExtensionPointName<>("com.intellij.directoryIndexExcludePolicy");

  /**
   * Supply all file urls (existing as well as not yet created) that should be treated as 'excluded'
   */
  @Contract(pure = true)
  default String @NotNull [] getExcludeUrlsForProject() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Nullable
  @Contract(pure = true)
  default Function<Sdk, List<VirtualFile>> getExcludeSdkRootsStrategy() {
    return null;
  }

  @Contract(pure = true)
  default VirtualFilePointer @NotNull [] getExcludeRootsForModule(@NotNull ModuleRootModel rootModel) { return VirtualFilePointer.EMPTY_ARRAY; }

  static DirectoryIndexExcludePolicy @NotNull [] getExtensions(@NotNull AreaInstance areaInstance) {
    return EP_NAME.getExtensions(areaInstance).toArray(new DirectoryIndexExcludePolicy[0]);
  }
}
