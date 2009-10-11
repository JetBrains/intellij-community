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

package com.intellij.openapi.vcs;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.*;

public class OutgoingChangesUtil {
  public static <T extends CommittedChangeList> Collection<Pair<VcsRevisionNumber, List<T>>>
        getVcsRootsForChanges(final AbstractVcs vcs, final Collection<Change> changes) throws VcsException {
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(vcs.getProject());
    final VcsOutgoingChangesProvider provider = vcs.getOutgoingChangesProvider();
    if (provider == null) return Collections.emptyList();

    final VirtualFile[] files = ChangesUtil.getFilesFromChanges(changes);

    final Set<VirtualFile> roots = new HashSet<VirtualFile>();
    for (VirtualFile file : files) {
      final VirtualFile root = vcsManager.getVcsRootFor(file);
      if (root != null) {
        roots.add(root);
      }
    }

    final Collection<Pair<VcsRevisionNumber, List<T>>> result = new ArrayList<Pair<VcsRevisionNumber, List<T>>>(roots.size());
    for (VirtualFile root : roots) {
      final Pair<VcsRevisionNumber, List<T>> pair = provider.getOutgoingChanges(root, true);
      result.add(pair);
    }
    return result;
  }
}
