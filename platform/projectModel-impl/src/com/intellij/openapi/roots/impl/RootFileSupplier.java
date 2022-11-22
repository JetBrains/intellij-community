// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.model.ModelBranch;
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.workspaceModel.ide.impl.UtilsKt;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
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
    return UtilsKt.getVirtualFile(virtualFileUrl);
  }

  public static RootFileSupplier forBranch(ModelBranch branch) {
    return new RootFileSupplier() {
      @Override
      public VirtualFile @NotNull [] getExcludedRoots(LibraryEx library) {
        return ContainerUtil.mapNotNull(library.getExcludedRootUrls(), this::findFileByUrl).toArray(VirtualFile.EMPTY_ARRAY);
      }

      @Override
      protected VirtualFile @NotNull [] getLibraryRoots(LibraryOrSdkOrderEntry entry, OrderRootType type) {
        return ContainerUtil.mapNotNull(entry.getRootUrls(type), this::findFileByUrl).toArray(VirtualFile.EMPTY_ARRAY);
      }

      @Override
      public VirtualFile @NotNull [] getLibraryRoots(Library library, OrderRootType type) {
        return ContainerUtil.mapNotNull(library.getUrls(type), this::findFileByUrl).toArray(VirtualFile.EMPTY_ARRAY);
      }

      @Override
      VirtualFile @NotNull [] getSdkRoots(@NotNull Sdk sdk, OrderRootType type) {
        return ContainerUtil.mapNotNull(sdk.getRootProvider().getUrls(type), this::findFileByUrl).toArray(VirtualFile.EMPTY_ARRAY);
      }

      @Override
      protected @Nullable VirtualFile getContentRoot(ContentEntry contentEntry) {
        return findFileByUrl(contentEntry.getUrl());
      }

      @Override
      protected @Nullable VirtualFile getSourceRoot(SourceFolder sourceFolder) {
        return findFileByUrl(sourceFolder.getUrl());
      }

      @Override
      public @Nullable VirtualFile findFile(@NotNull VirtualFileUrl virtualFileUrl) {
        return findFileByUrl(virtualFileUrl.getUrl());
      }

      @Override
      protected @NotNull List<@NotNull VirtualFile> getUnloadedContentRoots(UnloadedModuleDescription description) {
        return ContainerUtil.mapNotNull(description.getContentRoots(), p -> findFileByUrl(p.getUrl()));
      }

      @Override
      @Nullable
      public VirtualFile correctRoot(@NotNull VirtualFile file, @NotNull Object container, @Nullable Object containerProvider) {
        file = super.correctRoot(file, container, containerProvider);
        if (file != null) {
          file = branch.findFileCopy(file);
          if (!file.isValid()) {
            return null;
          }
        }
        return file;
      }

      @Override
      public @Nullable VirtualFile findFileByUrl(String url) {
        return branch.findFileByUrl(url);
      }

    };
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
