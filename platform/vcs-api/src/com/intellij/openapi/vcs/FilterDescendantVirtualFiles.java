/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
