/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListChange;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.HierarchicalFilePathComparator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class ChangesComparator {
  private static final Comparator<VirtualFile> VIRTUAL_FILE_FLAT = new VirtualFileComparator(true);
  private static final Comparator<VirtualFile> VIRTUAL_FILE_TREE = new VirtualFileComparator(false);
  private static final Comparator<Change> CHANGE_FLAT = new ChangeComparator(true);
  private static final Comparator<Change> CHANGE_TREE = new ChangeComparator(false);

  @NotNull
  public static Comparator<Change> getInstance(boolean flattened) {
    return flattened ? CHANGE_FLAT : CHANGE_TREE;
  }

  @NotNull
  public static Comparator<VirtualFile> getVirtualFileComparator(boolean flattened) {
    return flattened ? VIRTUAL_FILE_FLAT : VIRTUAL_FILE_TREE;
  }


  private static int comparePaths(@NotNull FilePath filePath1, @NotNull FilePath filePath2, boolean flattened) {
    if (!flattened) {
      return HierarchicalFilePathComparator.IGNORE_CASE.compare(filePath1, filePath2);
    }
    else {
      int delta = filePath1.getName().compareToIgnoreCase(filePath2.getName());
      if (delta != 0) return delta;
      return filePath1.getPath().compareTo(filePath2.getPath());
    }
  }

  private static class VirtualFileComparator implements Comparator<VirtualFile> {
    private final boolean myFlattened;

    public VirtualFileComparator(boolean flattened) {
      myFlattened = flattened;
    }

    @Override
    public int compare(VirtualFile o1, VirtualFile o2) {
      return comparePaths(VcsUtil.getFilePath(o1), VcsUtil.getFilePath(o2), myFlattened);
    }
  }

  private static class ChangeComparator implements Comparator<Change> {
    private final boolean myFlattened;

    public ChangeComparator(boolean flattened) {
      myFlattened = flattened;
    }

    @Override
    public int compare(Change o1, Change o2) {
      int delta = comparePaths(ChangesUtil.getFilePath(o1), ChangesUtil.getFilePath(o2), myFlattened);
      if (delta != 0) return delta;

      if (o1 instanceof ChangeListChange || o2 instanceof ChangeListChange) {
        if (o1 instanceof ChangeListChange && o2 instanceof ChangeListChange) {
          String changeList1 = ((ChangeListChange)o1).getChangeListName();
          String changeList2 = ((ChangeListChange)o2).getChangeListName();
          return changeList1.compareToIgnoreCase(changeList2);
        }
        else {
          return o1 instanceof ChangeListChange ? 1 : -1;
        }
      }

      return 0;
    }
  }
}
