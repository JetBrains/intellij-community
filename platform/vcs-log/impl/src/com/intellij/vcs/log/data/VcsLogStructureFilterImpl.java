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
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogDetailsFilter;
import com.intellij.vcs.log.VcsLogStructureFilter;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

public class VcsLogStructureFilterImpl implements VcsLogDetailsFilter, VcsLogStructureFilter {
  @NotNull private final Collection<FilePath> myFiles;

  public VcsLogStructureFilterImpl(@NotNull Set<VirtualFile> files) {
    this(ContainerUtil.map(files, file -> VcsUtil.getFilePath(file)));
  }

  public VcsLogStructureFilterImpl(@NotNull Collection<FilePath> files) {
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
  public String toString() {
    return "files:" + myFiles;
  }
}
