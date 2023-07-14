// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.platform.backend.workspace.VirtualFileUrls;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public class RootFileSupplier {
  private static final Logger LOG = Logger.getInstance(RootFileSupplier.class);
  public static final RootFileSupplier INSTANCE = new RootFileSupplier();

  @NotNull
  List<@NotNull VirtualFile> getUnloadedContentRoots(UnloadedModuleDescription description) {
    return ContainerUtil.mapNotNull(description.getContentRoots(), VirtualFilePointer::getFile);
  }

  @Nullable
  public VirtualFile correctRoot(@NotNull VirtualFile file, @NotNull Object container, @Nullable Object containerProvider) {
    if (!ensureValid(file, container, containerProvider)) {
      return null;
    }
    return file;
  }

  @Nullable
  public VirtualFile findFileByUrl(String url) {
    return VirtualFileManager.getInstance().findFileByUrl(url);
  }

  public VirtualFile @NotNull [] getExcludedRoots(LibraryEx library) {
    return library.getExcludedRoots();
  }

  VirtualFile @NotNull [] getLibraryRoots(LibraryOrSdkOrderEntry entry, OrderRootType type) {
    return entry.getRootFiles(type);
  }

  public VirtualFile @NotNull [] getLibraryRoots(Library library, OrderRootType type) {
    return library.getFiles(type);
  }

  VirtualFile @NotNull [] getSdkRoots(@NotNull Sdk entry, OrderRootType type) {
    return entry.getRootProvider().getFiles(type);
  }

  @Nullable
  VirtualFile getContentRoot(ContentEntry contentEntry) {
    return contentEntry.getFile();
  }

  @Nullable
  VirtualFile getSourceRoot(SourceFolder sourceFolder) {
    return sourceFolder.getFile();
  }

  @Nullable 
  public VirtualFile findFile(@NotNull VirtualFileUrl virtualFileUrl) {
    return VirtualFileUrls.getVirtualFile(virtualFileUrl);
  }

  public static boolean ensureValid(@NotNull VirtualFile file, @NotNull Object container, @Nullable Object containerProvider) {
    if (!file.isValid()) {
      if (containerProvider != null) {
        Class<?> providerClass = containerProvider.getClass();
        PluginException.logPluginError(LOG, "Invalid root " + file + " in " + container + " provided by " + providerClass, null, providerClass);
      }
      else {
        LOG.error("Invalid root " + file + " in " + container);
      }
      return false;
    }
    return true;
  }
}
