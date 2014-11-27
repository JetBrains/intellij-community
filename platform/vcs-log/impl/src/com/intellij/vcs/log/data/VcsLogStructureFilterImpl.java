/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.data;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class VcsLogStructureFilterImpl implements VcsLogDetailsFilter, VcsLogStructureFilter {
  @NotNull private final Set<VirtualFile> myRoots;
  @NotNull private final MultiMap<VirtualFile, VirtualFile> myRootToFiles;

  public VcsLogStructureFilterImpl(@NotNull Set<VirtualFile> roots,
                                   @NotNull MultiMap<VirtualFile, VirtualFile> rootToFiles) {
    myRoots = roots;
    myRootToFiles = rootToFiles;
  }

  @NotNull
  @Override
  public Collection<VirtualFile> getFiles(@NotNull VirtualFile root) {
    return myRootToFiles.get(root);
  }

  @Override
  public Collection<VirtualFile> getRoots() {
    return ContainerUtil.union(myRoots, myRootToFiles.keySet());
  }

  @Override
  public boolean matches(@NotNull VcsCommitMetadata details) {
    if (myRoots.contains(details.getRoot())) return true;

    if ((details instanceof VcsFullCommitDetails)) {
      for (Change change : ((VcsFullCommitDetails)details).getChanges()) {
        ContentRevision before = change.getBeforeRevision();
        if (before != null && matches(before.getFile().getPath(), myRootToFiles.get(details.getRoot()))) {
          return true;
        }
        ContentRevision after = change.getAfterRevision();
        if (after != null && matches(after.getFile().getPath(), myRootToFiles.get(details.getRoot()))) {
          return true;
        }
      }
      return false;
    }
    else {
      return false;
    }
  }

  private boolean matches(@NotNull final String path, @NotNull Collection<VirtualFile> files) {
    return ContainerUtil.find(files, new Condition<VirtualFile>() {
      @Override
      public boolean value(VirtualFile file) {
        return FileUtil.isAncestor(file.getPath(), path, false);
      }
    }) != null;
  }

  @NotNull
  public static VcsLogStructureFilterImpl build(@NotNull Collection<VirtualFile> files,
                                        @NotNull VcsLogDataPack dataPack) {
    Set<VirtualFile> roots = dataPack.getLogProviders().keySet();

    Set<VirtualFile> selectedRoots = new HashSet<VirtualFile>();
    MultiMap<VirtualFile, VirtualFile> selectedFiles = new MultiMap<VirtualFile, VirtualFile>();

    for (VirtualFile file : files) {
      if (roots.contains(file)) {
        // no need in details filter
        selectedRoots.add(file);
      }
      else {
        VirtualFile candidateAncestorRoot = null;
        for (VirtualFile root : roots) {
          if (VfsUtilCore.isAncestor(root, file, false)) {
            if (candidateAncestorRoot == null || VfsUtilCore.isAncestor(candidateAncestorRoot, root, false)) {
              candidateAncestorRoot = root;
            }
          }
          else if (VfsUtilCore.isAncestor(file, root, false)) {
            selectedRoots.add(root);
          }
        }

        if (candidateAncestorRoot != null) {
          selectedFiles.putValue(candidateAncestorRoot, file);
        }
      }
    }

    return new VcsLogStructureFilterImpl(selectedRoots, selectedFiles);
  }
}
