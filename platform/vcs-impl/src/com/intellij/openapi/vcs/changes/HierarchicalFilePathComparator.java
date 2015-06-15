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
  public static final HierarchicalFilePathComparator SYSTEM_CASE_SENSITIVE = new HierarchicalFilePathComparator(SystemInfo.isFileSystemCaseSensitive);

  private final boolean myIgnoreCase;

  private HierarchicalFilePathComparator(boolean ignoreCase) {
    myIgnoreCase = ignoreCase;
  }

  @Override
  public int compare(@NotNull FilePath filePath1, @NotNull FilePath filePath2) {
    final String path1 = FileUtilRt.toSystemIndependentName(filePath1.getPath());
    final String path2 = FileUtilRt.toSystemIndependentName(filePath2.getPath());

    int index1 = 0;
    int index2 = 0;

    int start = 0;

    while (index1 < path1.length() && index2 < path2.length()) {
      char c1 = path1.charAt(index1);
      char c2 = path2.charAt(index2);

      if (StringUtil.compare(c1, c2, myIgnoreCase) != 0) break;

      if (c1 == '/') start = index1;

      index1++;
      index2++;
    }

    if (index1 == path1.length() && index2 == path2.length()) return 0;
    if (index1 == path1.length()) return -1;
    if (index2 == path2.length()) return 1;

    int end1 = path1.indexOf('/', start + 1);
    int end2 = path2.indexOf('/', start + 1);

    String name1 = end1 == -1 ? path1.substring(start) : path1.substring(start, end1);
    String name2 = end2 == -1 ? path2.substring(start) : path2.substring(start, end2);

    boolean isDirectory1 = end1 != -1 || filePath1.isDirectory();
    boolean isDirectory2 = end2 != -1 || filePath2.isDirectory();

    if (isDirectory1 && !isDirectory2) return -1;
    if (!isDirectory1 && isDirectory2) return 1;

    return StringUtil.compare(name1, name2, myIgnoreCase);
  }
}
