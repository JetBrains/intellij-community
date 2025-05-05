// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The way to modify the {@link Sdk} roots, home path etc.<br>
 * First you call {@link Sdk#getSdkModificator()}<br>
 * Then you modify things via SdkModificator setters, e.g. {@link #setHomePath(String)}<br>
 * Last, you must call {@link #commitChanges()}
 */
public interface SdkModificator {
  @NotNull
  String getName();

  void setName(@NotNull String name);

  @NlsSafe
  String getHomePath();

  void setHomePath(String path);

  @Nullable
  String getVersionString();

  void setVersionString(String versionString);

  @Nullable
  SdkAdditionalData getSdkAdditionalData();

  void setSdkAdditionalData(SdkAdditionalData data);

  VirtualFile @NotNull [] getRoots(@NotNull OrderRootType rootType);

  default String @NotNull [] getUrls(@NotNull OrderRootType rootType) {
    return ContainerUtil.map(getRoots(rootType), file -> file.getUrl(), ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  void addRoot(@NotNull VirtualFile root, @NotNull OrderRootType rootType);

  default void addRoot(@NotNull String url, @NotNull OrderRootType rootType) {
    VirtualFile rootFile = VirtualFileManager.getInstance().refreshAndFindFileByUrl(url);
    if (rootFile != null) {
      addRoot(rootFile, rootType);
    }
  }

  void removeRoot(@NotNull VirtualFile root, @NotNull OrderRootType rootType);

  default void removeRoot(@NotNull String url, @NotNull OrderRootType rootType) {
    for (VirtualFile file : getRoots(rootType)) {
      if (file.getUrl().equals(url)) {
        removeRoot(file, rootType);
        break;
      }
    }
  }

  void removeRoots(@NotNull OrderRootType rootType);

  void removeAllRoots();

  @RequiresWriteLock
  void commitChanges();

  boolean isWritable();

  @ApiStatus.Internal
  default void applyChangesWithoutWriteAction() { }
}
