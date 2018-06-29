// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class DirectoryInfoImpl extends DirectoryInfo {
  protected final VirtualFile myRoot;//original project root for which this information is calculated
  private final Module module; // module to which content it belongs or null
  private final VirtualFile libraryClassRoot; // class root in library
  private final VirtualFile contentRoot;
  private final VirtualFile sourceRoot;
  private final SourceFolder sourceRootFolder;
  
  protected final boolean myInModuleSource;
  protected final boolean myInLibrarySource;
  protected final boolean myExcluded;
  private final String myUnloadedModuleName;

  DirectoryInfoImpl(@NotNull VirtualFile root, Module module, VirtualFile contentRoot,
                    VirtualFile sourceRoot, @Nullable SourceFolder sourceRootFolder, VirtualFile libraryClassRoot,
                    boolean inModuleSource, boolean inLibrarySource, boolean isExcluded, @Nullable String unloadedModuleName) {
    myRoot = root;
    this.module = module;
    this.libraryClassRoot = libraryClassRoot;
    this.contentRoot = contentRoot;
    this.sourceRoot = sourceRoot;
    this.sourceRootFolder = sourceRootFolder;
    myInModuleSource = inModuleSource;
    myInLibrarySource = inLibrarySource;
    myExcluded = isExcluded;
    myUnloadedModuleName = unloadedModuleName;
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
           ", isInModuleSource=" + myInModuleSource +
           ", rootType=" + (sourceRootFolder == null ? null : sourceRootFolder.getRootType()) +
           ", isExcludedFromModule=" + myExcluded +
           ", libraryClassRoot=" + getLibraryClassRoot() +
           ", contentRoot=" + getContentRoot() +
           ", sourceRoot=" + getSourceRoot() +
           "}";
  }

  @Override
  public boolean isInProject(@NotNull VirtualFile file) {
    return !isExcluded(file);
  }

  public boolean isIgnored() {
    return false;
  }

  @Nullable
  public VirtualFile getSourceRoot() {
    return sourceRoot;
  }

  @Nullable
  public SourceFolder getSourceRootFolder() {
    return sourceRootFolder;
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

  public boolean isInLibrarySource(@NotNull VirtualFile file) {
    return myInLibrarySource;
  }

  public boolean isExcluded() {
    return myExcluded;
  }

  @Override
  public boolean isExcluded(@NotNull VirtualFile file) {
    return myExcluded;
  }

  @Override
  public boolean isInModuleSource(@NotNull VirtualFile file) {
    return myInModuleSource;
  }

  public Module getModule() {
    return module;
  }

  @Override
  public String getUnloadedModuleName() {
    return myUnloadedModuleName;
  }

  @NotNull
  public VirtualFile getRoot() {
    return myRoot;
  }
}
