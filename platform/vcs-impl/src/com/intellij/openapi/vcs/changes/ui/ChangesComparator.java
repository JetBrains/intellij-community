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

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;

import java.util.Comparator;

public class ChangesComparator implements Comparator<Change> {
  private static final ChangesComparator ourFlattenedInstance = new ChangesComparator(false);
  private static final ChangesComparator ourTreeInstance = new ChangesComparator(true);
  private final boolean myTreeCompare;

  public static ChangesComparator getInstance(boolean flattened) {
    if (flattened) {
      return ourFlattenedInstance;
    } else {
      return ourTreeInstance;
    }
  }

  private ChangesComparator(boolean treeCompare) {
    myTreeCompare = treeCompare;
  }

  public int compare(final Change o1, final Change o2) {
    final FilePath filePath1 = ChangesUtil.getFilePath(o1);
    final FilePath filePath2 = ChangesUtil.getFilePath(o2);
    if (myTreeCompare) {
      final String path1 = FileUtilRt.toSystemIndependentName(filePath1.getPath());
      final String path2 = FileUtilRt.toSystemIndependentName(filePath2.getPath());

      int index1 = 0;
      int index2 = 0;

      int start = 0;

      while (index1 < path1.length() && index2 < path2.length()) {
        char c1 = path1.charAt(index1);
        char c2 = path2.charAt(index2);

        if (StringUtil.compare(c1, c2, true) != 0) break;

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

      return name1.compareToIgnoreCase(name2);
    }
    else {
      return filePath1.getName().compareToIgnoreCase(filePath2.getName());
    }
  }
}
