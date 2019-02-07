// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public interface DirectoryIndexExcludePolicy {
  ExtensionPointName<DirectoryIndexExcludePolicy> EP_NAME = ExtensionPointName.create("com.intellij.directoryIndexExcludePolicy");

  /**
   * @deprecated Override {@link #getExcludeUrlsForProject()} instead
   *
   */
  @Deprecated
  @NotNull
  default VirtualFile[] getExcludeRootsForProject() {
    return VirtualFile.EMPTY_ARRAY;
  }

  /**
   * Supply all file urls (existing as well as not yet created) that should be treated as 'excluded'
   */
  @NotNull
  default String[] getExcludeUrlsForProject() {
    return ContainerUtil.map2Array(getExcludeRootsForProject(), String.class, VirtualFile::getUrl);
  }

  @Nullable
  default Function<Sdk, List<VirtualFile>> getExcludeSdkRootsStrategy() {
    return null;
  }

  @NotNull
  VirtualFilePointer[] getExcludeRootsForModule(@NotNull ModuleRootModel rootModel);

  @NotNull
  static DirectoryIndexExcludePolicy[] getExtensions(@NotNull AreaInstance areaInstance) {
    return EP_NAME.getExtensions(areaInstance);
  }
}
