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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VfsUtil;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class LoadedChangeFilter {
  private final List<File> myRoots;

  public LoadedChangeFilter(List<File> roots) {
    myRoots = roots;
  }

  public boolean ok(final CommittedChangeList list) {
    final Collection<Change> changes = list.getChanges();
    for (Change change : changes) {
      if (change.getBeforeRevision() != null) {
        final FilePath path = change.getBeforeRevision().getFile();
        if (ok(path.getIOFile())) {
          return true;
        }
      }
      if (change.getAfterRevision() != null) {
        if (! (change.getBeforeRevision() != null &&
               change.getAfterRevision().getFile().equals(change.getBeforeRevision().getFile()))) {
          final FilePath path = change.getAfterRevision().getFile();
          if (ok(path.getIOFile())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private boolean ok(final File vf) {
    for (File root : myRoots) {
      if (VfsUtil.isAncestor(root, vf, false)) {
        return true;
      }
    }
    return false;
  }
}
