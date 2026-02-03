// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.ide.util.treeView.FileNameComparator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.text.CharSequenceSubSequence;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * Compares {@link FilePath FilePaths} hierarchically, i.e. folders always precede files
 * (like in default sorting method of most file managers).
 */
@ApiStatus.Internal
public class HierarchicalFilePathComparator implements Comparator<FilePath> {
  public static final HierarchicalFilePathComparator CASE_SENSITIVE = new HierarchicalFilePathComparator(false);
  public static final HierarchicalFilePathComparator CASE_INSENSITIVE = new HierarchicalFilePathComparator(true);

  public static final HierarchicalFilePathComparator NATURAL = new HierarchicalFilePathComparator(true) {
    @Override
    protected int compareFileNames(@NotNull CharSequence name1, @NotNull CharSequence name2) {
      return FileNameComparator.getInstance().compare(name1.toString(), name2.toString());
    }
  };

  private final boolean myIgnoreCase;

  private HierarchicalFilePathComparator(boolean ignoreCase) {
    myIgnoreCase = ignoreCase;
  }

  @Override
  public int compare(@NotNull FilePath filePath1, @NotNull FilePath filePath2) {
    String path1 = filePath1.getPath();
    String path2 = filePath2.getPath();

    int commonPrefix = StringUtil.commonPrefixLength(path1, path2, myIgnoreCase);

    if (commonPrefix == path1.length() && commonPrefix == path2.length()) {
      if (filePath1.isDirectory() != filePath2.isDirectory()) {
        return filePath1.isDirectory() ? -1 : 1;
      }
      return 0;
    }
    else if (commonPrefix == path1.length() && path2.charAt(commonPrefix) == '/') {
      return filePath1.isDirectory() ? -1 : 1; // name1 == "", isDirectory2 == true
    }
    else if (commonPrefix == path2.length() && path1.charAt(commonPrefix) == '/') {
      return filePath2.isDirectory() ? 1 : -1; // name2 == "", isDirectory1 == true
    }
    else {
      int start = StringUtil.lastIndexOf(path1, '/', 0, commonPrefix) + 1;
      int end1 = path1.indexOf('/', start);
      int end2 = path2.indexOf('/', start);

      boolean isDirectory1 = end1 != -1 || filePath1.isDirectory();
      boolean isDirectory2 = end2 != -1 || filePath2.isDirectory();
      if (isDirectory1 != isDirectory2) {
        return isDirectory1 ? -1 : 1;
      }

      if (end1 == -1) end1 = path1.length();
      if (end2 == -1) end2 = path2.length();
      CharSequence name1 = new CharSequenceSubSequence(path1, start, end1);
      CharSequence name2 = new CharSequenceSubSequence(path2, start, end2);

      return compareFileNames(name1, name2);
    }
  }

  /**
   * NB: Overriding methods should not return 0, if base method does not.
   */
  protected int compareFileNames(@NotNull CharSequence name1, @NotNull CharSequence name2) {
    return StringUtil.compare(name1, name2, myIgnoreCase);
  }
}
