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

/**
 * @author nik
 */
public class DirectoryInfoWithExcludePatterns extends DirectoryInfoImpl {
  private static final Logger LOG = Logger.getInstance(DirectoryInfoWithExcludePatterns.class);
  private final FileTypeAssocTable<Boolean> myExcludePatterns;

  public DirectoryInfoWithExcludePatterns(@NotNull VirtualFile root, Module module, VirtualFile contentRoot, VirtualFile sourceRoot,
                                          VirtualFile libraryClassRoot, boolean inModuleSource, boolean inLibrarySource, boolean isExcluded,
                                          int sourceRootTypeId, FileTypeAssocTable<Boolean> excludePatterns) {
    super(root, module, contentRoot, sourceRoot, libraryClassRoot, inModuleSource, inLibrarySource, isExcluded, sourceRootTypeId);
    myExcludePatterns = excludePatterns;
  }

  @Override
  public boolean isExcluded(@NotNull VirtualFile file) {
    if (myExcluded) return true;
    VirtualFile current = file;
    while (current != null && !myRoot.equals(current)) {
      if (myExcludePatterns.findAssociatedFileType(current.getNameSequence()) != null) {
        return true;
      }
      current = current.getParent();
    }
    if (current == null) {
      LOG.error("File " + file + " is not under this directory (" + myRoot + ")");
    }
    return false;
  }
}
