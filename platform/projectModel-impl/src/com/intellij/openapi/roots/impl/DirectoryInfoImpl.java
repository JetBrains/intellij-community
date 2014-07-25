/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class DirectoryInfoImpl extends DirectoryInfo {
  public static final int MAX_ROOT_TYPE_ID = Byte.MAX_VALUE;
  private final VirtualFile myRoot;//original project root for which this information is calculated
  private final Module module; // module to which content it belongs or null
  private final VirtualFile libraryClassRoot; // class root in library
  private final VirtualFile contentRoot;
  private final VirtualFile sourceRoot;
  private final boolean myInModuleSource;
  private final boolean myInLibrarySource;
  private final boolean myExcluded;
  private final byte mySourceRootTypeId;

  DirectoryInfoImpl(@NotNull VirtualFile root, Module module, VirtualFile contentRoot, VirtualFile sourceRoot, VirtualFile libraryClassRoot,
                    boolean inModuleSource, boolean inLibrarySource, boolean isExcluded, int sourceRootTypeId) {
    myRoot = root;
    this.module = module;
    this.libraryClassRoot = libraryClassRoot;
    this.contentRoot = contentRoot;
    this.sourceRoot = sourceRoot;
    myInModuleSource = inModuleSource;
    myInLibrarySource = inLibrarySource;
    myExcluded = isExcluded;
    if (sourceRootTypeId > MAX_ROOT_TYPE_ID) {
      throw new IllegalArgumentException(
        "Module source root type id " + sourceRootTypeId + " exceeds the maximum allowable value (" + MAX_ROOT_TYPE_ID + ")");
    }
    mySourceRootTypeId = (byte)sourceRootTypeId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return myRoot.equals(((DirectoryInfoImpl)o).myRoot);
  }

  @Override
  public int hashCode() {
    return myRoot.hashCode();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "DirectoryInfo{" +
           "module=" + getModule() +
           ", isInModuleSource=" + isInModuleSource() +
           ", rootTypeId=" + getSourceRootTypeId() +
           ", isInLibrarySource=" + isInLibrarySource() +
           ", isExcludedFromModule=" + isExcluded() +
           ", libraryClassRoot=" + getLibraryClassRoot() +
           ", contentRoot=" + getContentRoot() +
           ", sourceRoot=" + getSourceRoot() +
           "}";
  }

  public boolean isInProject() {
    return !isExcluded();
  }

  public boolean isIgnored() {
    return false;
  }

  @Nullable
  public VirtualFile getSourceRoot() {
    return sourceRoot;
  }

  public VirtualFile getLibraryClassRoot() {
    return libraryClassRoot;
  }

  @Nullable
  public VirtualFile getContentRoot() {
    return contentRoot;
  }

  public boolean isInModuleSource() {
    return myInModuleSource;
  }

  public boolean isInLibrarySource() {
    return myInLibrarySource;
  }

  public boolean isExcluded() {
    return myExcluded;
  }

  public Module getModule() {
    return module;
  }

  public int getSourceRootTypeId() {
    return mySourceRootTypeId;
  }

  @NotNull
  public VirtualFile getRoot() {
    return myRoot;
  }
}
