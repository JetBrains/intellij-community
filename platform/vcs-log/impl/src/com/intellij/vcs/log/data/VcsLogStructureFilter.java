/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class VcsLogStructureFilter implements VcsLogDetailsFilter {

  @NotNull private final Collection<VirtualFile> myFiles;
  @NotNull private final MultiMap<VirtualFile, VirtualFile> myFilesByRoots;

  public VcsLogStructureFilter(@NotNull Collection<VirtualFile> files, Collection<VirtualFile> roots) {
    myFiles = files;
    myFilesByRoots = groupFilesByVcsRoots(files, roots);
  }

  private static MultiMap<VirtualFile, VirtualFile> groupFilesByVcsRoots(Collection<VirtualFile> files, Collection<VirtualFile> roots) {
    MultiMap<VirtualFile, VirtualFile> grouped = MultiMap.create();
    for (VirtualFile file : files) {
      VirtualFile root = findBestRoot(file, roots);
      if (root != null) {
        grouped.putValue(root, file);
      }
    }
    return grouped;
  }

  @Nullable
  private static VirtualFile findBestRoot(@NotNull VirtualFile file, @NotNull Collection<VirtualFile> roots) {
    VirtualFile candidate = null;
    for (VirtualFile root : roots) {
      if (VfsUtilCore.isAncestor(root, file, false)) {
        if (candidate == null || VfsUtilCore.isAncestor(candidate, root, true)) {
          candidate = root;
        }
      }
    }
    return candidate;
  }

  @Override
  public boolean matches(@NotNull VcsFullCommitDetails details) {
    for (Change change : details.getChanges()) {
      ContentRevision before = change.getBeforeRevision();
      if (before != null && matches(before.getFile().getPath())) {
        return true;
      }
      ContentRevision after = change.getAfterRevision();
      if (after != null && matches(after.getFile().getPath())) {
        return true;
      }
    }
    return false;
  }

  private boolean matches(final String path) {
    return ContainerUtil.find(myFiles, new Condition<VirtualFile>() {
      @Override
      public boolean value(VirtualFile file) {
        return FileUtil.isAncestor(file.getPath(), path, false);
      }
    }) != null;
  }

  @NotNull
  public Collection<VirtualFile> getFiles(@NotNull VirtualFile root) {
    return myFilesByRoots.get(root);
  }

}
