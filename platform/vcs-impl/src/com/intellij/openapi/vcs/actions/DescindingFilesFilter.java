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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VfsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DescindingFilesFilter {
  private DescindingFilesFilter() {
  }

  @NotNull
  public static FilePath[] filterDescindingFiles(@NotNull FilePath[] roots, Project project) {
    final List<FilePath> result = new LinkedList<FilePath>();
    ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(project);

    Arrays.sort(roots, FilePathComparator.getInstance());
    final Map<VcsKey, List<FilePath>> chains = new HashMap<VcsKey, List<FilePath>>();
    for (FilePath root : roots) {
      final AbstractVcs vcs = manager.getVcsFor(root);
      if (vcs == null) continue;
      if (vcs.allowsNestedRoots()) {
        // just put into result: nested roots are allowed
        result.add(root);
        continue;
      }
      //if (pathsFilter != null && (! pathsFilter.convert(new Pair<FilePath, AbstractVcs>(root, vcs)))) continue;
      
      final List<FilePath> chain = chains.get(vcs.getKeyInstanceMethod());
      if (chain == null) {
        final LinkedList<FilePath> newList = new LinkedList<FilePath>();
        newList.add(root);
        chains.put(vcs.getKeyInstanceMethod(), newList);
      } else {
        boolean failed = false;
        for (FilePath chainedPath : chain) {
          if (VfsUtil.isAncestor(chainedPath.getIOFile(), root.getIOFile(), false)) {
            // do not take this root
            failed = true;      
            break;
          }
        }
        if (! failed) {
          chain.add(root);
        }
      }
    }

    for (List<FilePath> filePaths : chains.values()) {
      result.addAll(filePaths);
    }

    return result.toArray(new FilePath[result.size()]);
  }

  private static class FilePathComparator implements Comparator<FilePath> {
    private final static FilePathComparator ourInstance = new FilePathComparator();

    public static FilePathComparator getInstance() {
      return ourInstance;
    }

    public int compare(final FilePath fp1, final FilePath fp2) {
      return fp1.getIOFile().getAbsolutePath().length() - fp2.getIOFile().getAbsolutePath().length();
    }
  }
}
