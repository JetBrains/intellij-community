package com.intellij.openapi.vcs;

import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;

import java.util.Collections;
import java.util.List;

public class FilterDescendantVirtualFiles extends AbstractFilterChildren<VirtualFile> {
  private final static FilterDescendantVirtualFiles ourInstance = new FilterDescendantVirtualFiles();

  private FilterDescendantVirtualFiles() {
  }

  protected void sortAscending(final List<VirtualFile> virtualFiles) {
    Collections.sort(virtualFiles, FilePathComparator.getInstance());
  }

  protected boolean isAncestor(final VirtualFile parent, final VirtualFile child) {
    return VfsUtil.isAncestor(parent, child, false);
  }

  public static void filter(final List<VirtualFile> in) {
    ourInstance.doFilter(in);
  }
}
