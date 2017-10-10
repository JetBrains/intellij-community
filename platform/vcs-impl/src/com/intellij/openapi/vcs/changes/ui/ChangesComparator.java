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
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.HierarchicalFilePathComparator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class ChangesComparator {
  @NotNull
  public static Comparator<Change> getInstance(boolean flattened) {
    return (o1, o2) -> comparePaths(ChangesUtil.getFilePath(o1), ChangesUtil.getFilePath(o2), flattened);
  }

  @NotNull
  public static Comparator<VirtualFile> getVirtualFileComparator(boolean flattened) {
    return (o1, o2) -> comparePaths(VcsUtil.getFilePath(o1), VcsUtil.getFilePath(o2), flattened);
  }

  private static int comparePaths(@NotNull FilePath filePath1, @NotNull FilePath filePath2, boolean flattened) {
    if (!flattened) {
      return HierarchicalFilePathComparator.IGNORE_CASE.compare(filePath1, filePath2);
    }
    else {
      return filePath1.getName().compareToIgnoreCase(filePath2.getName());
    }
  }
}
