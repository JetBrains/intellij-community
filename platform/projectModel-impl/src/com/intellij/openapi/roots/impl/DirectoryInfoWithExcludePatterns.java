/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.impl.FileTypeAssocTable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class DirectoryInfoWithExcludePatterns extends DirectoryInfoImpl {
  private static final Logger LOG = Logger.getInstance(DirectoryInfoWithExcludePatterns.class);
  @Nullable private final FileTypeAssocTable<Boolean> myContentExcludePatterns;
  @Nullable private final FileTypeAssocTable<Boolean> myLibraryExcludePatterns;

  public DirectoryInfoWithExcludePatterns(@NotNull VirtualFile root, Module module, VirtualFile contentRoot, VirtualFile sourceRoot,
                                          VirtualFile libraryClassRoot, boolean inModuleSource, boolean inLibrarySource, boolean isExcluded,
                                          int sourceRootTypeId,
                                          @Nullable FileTypeAssocTable<Boolean> contentExcludePatterns,
                                          @Nullable FileTypeAssocTable<Boolean> libraryExcludePatterns,
                                          @Nullable String unloadedModuleName) {
    super(root, module, contentRoot, sourceRoot, libraryClassRoot, inModuleSource, inLibrarySource, isExcluded, sourceRootTypeId, unloadedModuleName);
    myContentExcludePatterns = contentExcludePatterns;
    myLibraryExcludePatterns = libraryExcludePatterns;
    LOG.assertTrue(myContentExcludePatterns != null || myLibraryExcludePatterns != null,
                   "Directory info of '" + root + "' with exclude patterns have no any exclude patterns: " + toString());
  }

  @Override
  public boolean isInLibrarySource(@NotNull VirtualFile file) {
    return myInLibrarySource && !isExcludedByPatterns(file, myLibraryExcludePatterns);
  }

  private boolean isExcludedByPatterns(@NotNull VirtualFile file, @Nullable FileTypeAssocTable<Boolean> patterns) {
    if (patterns == null) return false;

    VirtualFile current = file;
    while (current != null && !myRoot.equals(current)) {
      if (patterns.findAssociatedFileType(current.getNameSequence()) != null) {
        return true;
      }
      current = current.getParent();
    }
    if (current == null) {
      LOG.error("File " + file + " is not under this directory (" + myRoot + ")");
    }
    return false;
  }

  @Override
  public boolean isExcluded(@NotNull VirtualFile file) {
    if (myExcluded) return true;
    if (myLibraryExcludePatterns == null && myContentExcludePatterns == null) {
      LOG.error("Directory info of '" + getRoot() + "' with exclude patterns have no any exclude patterns: " + toString());
      return false;
    }

    boolean inContent = getContentRoot() != null;
    if (!inContent && !myInLibrarySource) return false;

    VirtualFile current = file;
    while (current != null && !myRoot.equals(current)) {
      CharSequence name = current.getNameSequence();
      boolean excludedFromModule = myContentExcludePatterns != null && myContentExcludePatterns.findAssociatedFileType(name) != null;
      boolean excludedFromLibrary = myLibraryExcludePatterns != null && myLibraryExcludePatterns.findAssociatedFileType(name) != null;
      if ((!inContent || excludedFromModule) && (!myInLibrarySource || excludedFromLibrary)) {
        return true;
      }
      current = current.getParent();
    }
    if (current == null) {
      LOG.error("File " + file + " is not under this directory (" + myRoot + ")");
    }
    return false;
  }

  @Override
  public boolean isInModuleSource(@NotNull VirtualFile file) {
    return myInModuleSource && !isExcludedByPatterns(file, myContentExcludePatterns);
  }
}
