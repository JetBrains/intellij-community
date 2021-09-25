// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class NonProjectDirectoryInfo extends DirectoryInfo {
  static final NonProjectDirectoryInfo IGNORED = new NonProjectDirectoryInfo("ignored") {
    @Override
    public boolean isIgnored() {
      return true;
    }
  };
  static final NonProjectDirectoryInfo EXCLUDED = new NonProjectDirectoryInfo("excluded from project") {
    @Override
    public boolean isExcluded() {
      return true;
    }
  };
  static final NonProjectDirectoryInfo NOT_UNDER_PROJECT_ROOTS = new NonProjectDirectoryInfo("not under project roots"){
    @Override
    boolean hasContentEntriesBeneath() {
      return false;
    }
  };
  static final NonProjectDirectoryInfo INVALID = new NonProjectDirectoryInfo("invalid");
  // there are some content entries strictly beneath this dir and it is guaranteed there are no content entries above
  static final NonProjectDirectoryInfo OUTSIDE_PROJECT_ROOTS_BUT_HAS_CONTENT_BENEATH = new NonProjectDirectoryInfo("outside project root but has content beneath");
  private final String myDebugName;

  private NonProjectDirectoryInfo(@NotNull String debugName) {
    myDebugName = debugName;
  }

  @Override
  public boolean isInProject(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public String toString() {
    return "DirectoryInfo: " + myDebugName;
  }

  @Override
  public boolean equals(Object o) {
    return this == o;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public boolean isIgnored() {
    return false;
  }

  @Override
  @Nullable
  public VirtualFile getSourceRoot() {
    return null;
  }

  @Override
  public VirtualFile getLibraryClassRoot() {
    return null;
  }

  @Override
  @Nullable
  public VirtualFile getContentRoot() {
    return null;
  }

  @Override
  public boolean isInLibrarySource(@NotNull VirtualFile file) {
    return false;
  }

  public boolean isExcluded() {
    return false;
  }

  @Override
  public boolean isExcluded(@NotNull VirtualFile file) {
    return isExcluded();
  }

  @Override
  public boolean isInModuleSource(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public Module getModule() {
    return null;
  }

  @Override
  public String getUnloadedModuleName() {
    return null;
  }

  @Nullable
  @Override
  public SourceFolder getSourceRootFolder() {
    return null;
  }

  @Override
  public boolean processContentBeneathExcluded(@NotNull VirtualFile dir, @NotNull Processor<? super VirtualFile> processor) {
    return true;
  }

  boolean hasContentEntriesBeneath() {
    return true;
  }

  static class WithBeneathInfo extends NonProjectDirectoryInfo {
    @NotNull private final NonProjectDirectoryInfo myDelegate;
    // List of all DirectoryInfos which root is a child of myRoot and which have content root
    // Only ever filled for excluded DirectoryInfos (isExcluded() == true)
    final List<DirectoryInfoImpl> myContentInfosBeneath = new SmartList<>();

    WithBeneathInfo(@NotNull NonProjectDirectoryInfo delegate) {
      super(delegate.myDebugName);
      myDelegate = delegate;
    }

    @Override
    public boolean processContentBeneathExcluded(@NotNull VirtualFile dir,
                                                 @NotNull Processor<? super VirtualFile> processor) {
      return isExcluded(dir) &&
             ContainerUtil.process(myContentInfosBeneath, child -> !VfsUtilCore.isAncestor(dir, child.myRoot, false) || processor.process(child.myRoot));
    }

    @Override
    public boolean isInProject(@NotNull VirtualFile file) {
      return myDelegate.isInProject(file);
    }

    @Override
    public String toString() {
      return "WithBeneathInfo: "+myDelegate+ " with roots beneath: "+myContentInfosBeneath;
    }

    @Override
    public boolean isIgnored() {
      return myDelegate.isIgnored();
    }

    @Override
    @Nullable
    public VirtualFile getSourceRoot() {
      return myDelegate.getSourceRoot();
    }

    @Override
    public VirtualFile getLibraryClassRoot() {
      return myDelegate.getLibraryClassRoot();
    }

    @Override
    @Nullable
    public VirtualFile getContentRoot() {
      return myDelegate.getContentRoot();
    }

    @Override
    public boolean isInLibrarySource(@NotNull VirtualFile file) {
      return myDelegate.isInLibrarySource(file);
    }

    @Override
    public boolean isExcluded() {
      return myDelegate.isExcluded();
    }

    @Override
    public boolean isExcluded(@NotNull VirtualFile file) {
      return myDelegate.isExcluded(file);
    }

    @Override
    public boolean isInModuleSource(@NotNull VirtualFile file) {
      return myDelegate.isInModuleSource(file);
    }

    @Override
    public Module getModule() {
      return myDelegate.getModule();
    }

    @Override
    public String getUnloadedModuleName() {
      return myDelegate.getUnloadedModuleName();
    }

    @Override
    @Nullable
    public SourceFolder getSourceRootFolder() {
      return myDelegate.getSourceRootFolder();
    }

    @Override
    public boolean hasLibraryClassRoot() {
      return myDelegate.hasLibraryClassRoot();
    }
  }
}
