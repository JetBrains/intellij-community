// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.impl.FileTypeAssocTable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class DirectoryInfoWithExcludePatterns extends DirectoryInfoImpl {
  private static final Logger LOG = Logger.getInstance(DirectoryInfoWithExcludePatterns.class);
  @Nullable private final FileTypeAssocTable<Boolean> myContentExcludePatterns;
  @Nullable private final Condition<? super VirtualFile> myLibraryExcludeCondition;

  DirectoryInfoWithExcludePatterns(@NotNull VirtualFile root, Module module, VirtualFile contentRoot, VirtualFile sourceRoot,
                                   @Nullable SourceFolder sourceFolder, VirtualFile libraryClassRoot, boolean inModuleSource,
                                   boolean inLibrarySource, boolean isExcluded,
                                   @Nullable FileTypeAssocTable<Boolean> contentExcludePatterns,
                                   @Nullable Condition<? super VirtualFile> libraryExcludeCondition,
                                   @Nullable String unloadedModuleName) {
    super(root, module, contentRoot, sourceRoot, sourceFolder, libraryClassRoot, inModuleSource, inLibrarySource, isExcluded, unloadedModuleName);
    myContentExcludePatterns = contentExcludePatterns;
    myLibraryExcludeCondition = libraryExcludeCondition;

    if (myContentExcludePatterns == null && myLibraryExcludeCondition == null) {
      LOG.error("Directory info of '" + root + "' with exclude patterns have no any exclude patterns: " + this);
    }
  }

  @Override
  public boolean isInLibrarySource(@NotNull VirtualFile file) {
    return super.isInLibrarySource(file) && !isExcludedByCondition(file, myLibraryExcludeCondition);
  }

  private boolean isExcludedByCondition(@NotNull VirtualFile file, @Nullable Condition<? super VirtualFile> condition) {
    if (condition == null) return false;

    VirtualFile current = getPhysicalFile(file);
    while (current != null && !myRoot.equals(current)) {
      if (condition.value(current)) {
        return true;
      }
      current = current.getParent();
    }
    if (current == null) {
      LOG.error("File " + file + " is not under this directory (" + myRoot + ")");
    }
    return false;
  }

  private boolean isExcludedByPatterns(@NotNull VirtualFile file, @Nullable FileTypeAssocTable<Boolean> patterns) {
    return patterns != null && isExcludedByCondition(file, f -> patterns.findAssociatedFileType(f.getNameSequence()) != null);
  }

  private static VirtualFile getPhysicalFile(VirtualFile file) {
    return file instanceof VirtualFileWindow ? ((VirtualFileWindow)file).getDelegate() : file;
  }

  @Override
  public boolean isExcluded(@NotNull VirtualFile file) {
    if (isExcluded()) return true;
    if (myLibraryExcludeCondition == null && myContentExcludePatterns == null) {
      LOG.error("Directory info of '" + getRoot() + "' with exclude patterns have no any exclude patterns: " + this);
      return false;
    }

    boolean inContent = getContentRoot() != null;
    if (!inContent && !super.isInLibrarySource(file)) return false;

    VirtualFile current = getPhysicalFile(file);
    while (current != null && !myRoot.equals(current)) {
      CharSequence name = current.getNameSequence();
      boolean excludedFromModule = myContentExcludePatterns != null && myContentExcludePatterns.findAssociatedFileType(name) != null;
      boolean excludedFromLibrary = myLibraryExcludeCondition != null && myLibraryExcludeCondition.value(current);
      if ((!inContent || excludedFromModule) && (!super.isInLibrarySource(file) || excludedFromLibrary)) {
        return true;
      }
      current = current.getParent();
    }
    if (current == null) {
      IllegalArgumentException e = new IllegalArgumentException("File " + file + " is not under this directory (" + myRoot + ")");
      LOG.warn(e);
      throw e;
    }
    return false;
  }

  @Override
  public boolean isInModuleSource(@NotNull VirtualFile file) {
    return super.isInModuleSource(file) && !isExcludedByPatterns(file, myContentExcludePatterns);
  }
}
