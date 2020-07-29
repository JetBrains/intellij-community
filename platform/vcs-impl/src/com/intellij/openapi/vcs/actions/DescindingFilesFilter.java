// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VfsUtilCore;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class DescindingFilesFilter {
  private DescindingFilesFilter() {
  }

  public static FilePath @NotNull [] filterDescindingFiles(FilePath @NotNull [] roots, Project project) {
    final List<FilePath> result = new ArrayList<>();
    ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(project);

    Arrays.sort(roots, FilePathComparator.getInstance());
    final Map<VcsKey, List<FilePath>> chains = new HashMap<>();
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
        final List<FilePath> newList = new ArrayList<>();
        newList.add(root);
        chains.put(vcs.getKeyInstanceMethod(), newList);
      } else {
        boolean failed = false;
        for (FilePath chainedPath : chain) {
          if (VfsUtilCore.isAncestor(chainedPath.getIOFile(), root.getIOFile(), false)) {
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

    return result.toArray(new FilePath[0]);
  }

  private static class FilePathComparator implements Comparator<FilePath> {
    private final static FilePathComparator ourInstance = new FilePathComparator();

    public static FilePathComparator getInstance() {
      return ourInstance;
    }

    @Override
    public int compare(@NotNull FilePath fp1, @NotNull FilePath fp2) {
      return fp1.getPath().length() - fp2.getPath().length();
    }
  }
}
