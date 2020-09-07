// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible.filters;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogDetailsFilter;
import com.intellij.vcs.log.VcsLogStructureFilter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@ApiStatus.Internal
public class VcsLogStructureFilterImpl implements VcsLogDetailsFilter, VcsLogStructureFilter {
  @NotNull private final Collection<FilePath> myFiles;

  /**
   * Use {@link VcsLogFilterObject#fromPaths(Collection)}
   */
  protected VcsLogStructureFilterImpl(@NotNull Collection<FilePath> files) {
    myFiles = files;
  }

  @NotNull
  @Override
  public Collection<FilePath> getFiles() {
    return myFiles;
  }

  @Override
  public boolean matches(@NotNull VcsCommitMetadata details) {
    if ((details instanceof VcsFullCommitDetails)) {
      for (Change change : ((VcsFullCommitDetails)details).getChanges()) {
        ContentRevision before = change.getBeforeRevision();
        if (before != null && matches(before.getFile().getPath())) {
          return true;
        }
        ContentRevision after = change.getAfterRevision();
        if (after != null && matches(after.getFile().getPath())) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean matches(@NotNull final String path) {
    return ContainerUtil.find(myFiles, (Condition<VirtualFile>)file -> FileUtil.isAncestor(file.getPath(), path, false)) != null;
  }

  @Override
  @NonNls
  public String toString() {
    return "files:" + myFiles;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VcsLogStructureFilterImpl filter = (VcsLogStructureFilterImpl)o;
    return Comparing.haveEqualElements(getFiles(), filter.getFiles());
  }

  @Override
  public int hashCode() {
    return Comparing.unorderedHashcode(getFiles());
  }
}
