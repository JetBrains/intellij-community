// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * not a bug: Orders directories, shortest paths first, files are left as is.
 * correct alphabet ordering is not here
 */
public class VirtualFileHierarchicalComparator implements Comparator<VirtualFile> {
  private static final VirtualFileHierarchicalComparator ourInstance = new VirtualFileHierarchicalComparator();

  public static VirtualFileHierarchicalComparator getInstance() {
    return ourInstance;
  }

  @Override
  public int compare(@NotNull VirtualFile vf1, @NotNull VirtualFile vf2) {
    boolean isDir1 = vf1.isDirectory();
    boolean isDir2 = vf2.isDirectory();

    if (!isDir1 && !isDir2) return 0;
    if (isDir1 && !isDir2) return -1;
    if (!isDir1) return 1;

    return Integer.compare(vf1.getPath().length(), vf2.getPath().length());
  }
}
