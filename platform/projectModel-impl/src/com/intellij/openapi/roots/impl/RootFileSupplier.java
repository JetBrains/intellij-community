// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.model.ModelBranch;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class RootFileSupplier {
  private static final Logger LOG = Logger.getInstance(RootFileSupplier.class);
  static final RootFileSupplier INSTANCE = new RootFileSupplier();

  @NotNull
  List<@NotNull VirtualFile> getUnloadedContentRoots(UnloadedModuleDescription description) {
    return ContainerUtil.mapNotNull(description.getContentRoots(), VirtualFilePointer::getFile);
  }

  @Nullable
  VirtualFile correctRoot(@NotNull VirtualFile file, @NotNull Object container, @Nullable Object containerProvider) {
    if (!ensureValid(file, container, containerProvider)) {
      return null;
    }
    return file;
  }

  @Nullable
  VirtualFile findFileByUrl(String url) {
    return VirtualFileManager.getInstance().findFileByUrl(url);
  }

  VirtualFile @NotNull [] getExcludedRoots(LibraryEx library) {
    return library.getExcludedRoots();
  }

  VirtualFile @NotNull [] getLibraryRoots(LibraryOrSdkOrderEntry entry, OrderRootType type) {
    return entry.getRootFiles(type);
  }

  @Nullable
  VirtualFile getContentRoot(ContentEntry contentEntry) {
    return contentEntry.getFile();
  }

  @Nullable
  VirtualFile getSourceRoot(SourceFolder sourceFolder) {
    return sourceFolder.getFile();
  }

  static RootFileSupplier forBranch(ModelBranch branch) {
    return new RootFileSupplier() {
      @Override
      protected VirtualFile @NotNull [] getExcludedRoots(LibraryEx library) {
        return ContainerUtil.mapNotNull(library.getExcludedRootUrls(), this::findFileByUrl).toArray(VirtualFile.EMPTY_ARRAY);
      }

      @Override
      protected VirtualFile @NotNull [] getLibraryRoots(LibraryOrSdkOrderEntry entry, OrderRootType type) {
        return ContainerUtil.mapNotNull(entry.getRootUrls(type), this::findFileByUrl).toArray(VirtualFile.EMPTY_ARRAY);
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
      protected @NotNull List<@NotNull VirtualFile> getUnloadedContentRoots(UnloadedModuleDescription description) {
        return ContainerUtil.mapNotNull(description.getContentRoots(), p -> findFileByUrl(p.getUrl()));
      }

      @Override
      protected @Nullable VirtualFile correctRoot(@NotNull VirtualFile file, @NotNull Object container, @Nullable Object containerProvider) {
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
      protected @Nullable VirtualFile findFileByUrl(String url) {
        return branch.findFileByUrl(url);
      }

    };
  }

  static boolean ensureValid(@NotNull VirtualFile file, @NotNull Object container, @Nullable Object containerProvider) {
    if (!file.isValid()) {
      if (containerProvider != null) {
        LOG.error("Invalid root " + file + " in " + container + " provided by " + containerProvider.getClass());
      }
      else {
        LOG.error("Invalid root " + file + " in " + container);
      }
      return false;
    }
    return true;
  }
}
