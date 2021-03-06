// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.util.treeView.FileNameComparator;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListChange;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.HierarchicalFilePathComparator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public final class ChangesComparator {
  private static final Comparator<VirtualFile> VIRTUAL_FILE_FLAT = new VirtualFileComparator(true);
  private static final Comparator<VirtualFile> VIRTUAL_FILE_TREE = new VirtualFileComparator(false);
  private static final Comparator<Change> CHANGE_FLAT = new ChangeComparator(true);
  private static final Comparator<Change> CHANGE_TREE = new ChangeComparator(false);
  private static final Comparator<FilePath> FILE_PATH_FLAT = new FilePathComparator(true);
  private static final Comparator<FilePath> FILE_PATH_TREE = new FilePathComparator(false);

  @NotNull
  public static Comparator<Change> getInstance(boolean flattened) {
    return flattened ? CHANGE_FLAT : CHANGE_TREE;
  }

  @NotNull
  public static Comparator<VirtualFile> getVirtualFileComparator(boolean flattened) {
    return flattened ? VIRTUAL_FILE_FLAT : VIRTUAL_FILE_TREE;
  }

  @NotNull
  public static Comparator<FilePath> getFilePathComparator(boolean flattened) {
    return flattened ? FILE_PATH_FLAT : FILE_PATH_TREE;
  }

  private static int comparePaths(@NotNull FilePath filePath1, @NotNull FilePath filePath2, boolean flattened) {
    if (flattened) {
      int delta = FileNameComparator.INSTANCE.compare(filePath1.getName(), filePath2.getName());
      if (delta != 0) return delta;
    }
    return HierarchicalFilePathComparator.NATURAL.compare(filePath1, filePath2);
  }

  private static class VirtualFileComparator implements Comparator<VirtualFile> {
    private final boolean myFlattened;

    VirtualFileComparator(boolean flattened) {
      myFlattened = flattened;
    }

    @Override
    public int compare(VirtualFile o1, VirtualFile o2) {
      return comparePaths(VcsUtil.getFilePath(o1), VcsUtil.getFilePath(o2), myFlattened);
    }
  }

  private static class FilePathComparator implements Comparator<FilePath> {
    private final boolean myFlattened;

    FilePathComparator(boolean flattened) {
      myFlattened = flattened;
    }

    @Override
    public int compare(FilePath o1, FilePath o2) {
      return comparePaths(o1, o2, myFlattened);
    }
  }

  private static class ChangeComparator implements Comparator<Change> {
    private final boolean myFlattened;

    ChangeComparator(boolean flattened) {
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
