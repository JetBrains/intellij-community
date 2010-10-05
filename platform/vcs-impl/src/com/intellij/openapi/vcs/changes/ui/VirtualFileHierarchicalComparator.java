/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vfs.VirtualFile;

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

  public int compare(final VirtualFile vf1, final VirtualFile vf2) {
    final boolean isDir1 = vf1.isDirectory();
    final boolean isDir2 = vf2.isDirectory();

    if ((! isDir1) && (! isDir2)) return 0;
    if (isDir1 && (! isDir2)) return -1;
    if ((! isDir1) && isDir2) return 1;

    final int diff = vf1.getPath().length() - vf2.getPath().length();
    return diff == 0 ? 0 : (diff < 0 ? -1 : 1);
  }
}
