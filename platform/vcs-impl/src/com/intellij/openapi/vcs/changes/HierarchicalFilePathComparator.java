/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * Compares {@link FilePath FilePaths} hierarchically, i.e. folders always precede files
 * (like in default sorting method of most file managers).
 */
public class HierarchicalFilePathComparator implements Comparator<FilePath> {

  public static final HierarchicalFilePathComparator IGNORE_CASE = new HierarchicalFilePathComparator(true);
  public static final HierarchicalFilePathComparator SYSTEM_CASE_SENSITIVE = new HierarchicalFilePathComparator(!SystemInfo.isFileSystemCaseSensitive);

  private final boolean myIgnoreCase;

  private HierarchicalFilePathComparator(boolean ignoreCase) {
    myIgnoreCase = ignoreCase;
  }

  @Override
  public int compare(@NotNull FilePath filePath1, @NotNull FilePath filePath2) {
    String path1 = FileUtilRt.toSystemIndependentName(filePath1.getPath());
    String path2 = FileUtilRt.toSystemIndependentName(filePath2.getPath());

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

      String name1 = end1 == -1 ? path1.substring(start) : path1.substring(start, end1);
      String name2 = end2 == -1 ? path2.substring(start) : path2.substring(start, end2);

      boolean isDirectory1 = end1 != -1 || filePath1.isDirectory();
      boolean isDirectory2 = end2 != -1 || filePath2.isDirectory();

      if (isDirectory1 != isDirectory2) {
        return isDirectory1 ? -1 : 1;
      }

      return StringUtil.compare(name1, name2, myIgnoreCase);
    }
  }
}
