// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Contract;

import java.util.List;

public final class FilterDescendantVirtualFiles extends AbstractFilterChildren<VirtualFile> {
  private static final FilterDescendantVirtualFiles ourInstance = new FilterDescendantVirtualFiles();

  private FilterDescendantVirtualFiles() {
  }

  @Override
  @Contract(mutates = "param1")
  protected void sortAscending(final List<? extends VirtualFile> virtualFiles) {
    virtualFiles.sort(FilePathComparator.getInstance());
  }

  @Override
  protected boolean isAncestor(final VirtualFile parent, final VirtualFile child) {
    return VfsUtil.isAncestor(parent, child, false);
  }

  public static void filter(final List<? extends VirtualFile> in) {
    ourInstance.doFilter(in);
  }
}
