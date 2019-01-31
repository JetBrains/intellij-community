/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.zmlx.hg4idea.log;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.VcsChangesLazilyParsedDetails;
import com.intellij.vcs.log.impl.VcsFileStatusInfo;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.provider.HgChangeProvider;

import java.util.List;

import static org.zmlx.hg4idea.log.HgHistoryUtil.createChange;

public class HgCommit extends VcsChangesLazilyParsedDetails {
  @NotNull private final HgRevisionNumber myRevisionNumber;

  public HgCommit(@NotNull Project project, VirtualFile root, @NotNull Hash hash,
                  @NotNull List<Hash> parentsHashes,
                  @NotNull HgRevisionNumber vcsRevisionNumber,
                  @NotNull VcsUser author,
                  long time,
                  List<List<VcsFileStatusInfo>> reportedChanges) {
    super(hash, parentsHashes, time, root, vcsRevisionNumber.getSubject(), author, vcsRevisionNumber.getCommitMessage(), author, time);
    myRevisionNumber = vcsRevisionNumber;
    myChanges.set(reportedChanges.isEmpty() ? EMPTY_CHANGES : new UnparsedChanges(project, reportedChanges));
  }

  private class UnparsedChanges extends VcsChangesLazilyParsedDetails.UnparsedChanges {
    private UnparsedChanges(@NotNull Project project,
                            @NotNull List<List<VcsFileStatusInfo>> changesOutput) {
      super(project, changesOutput);
    }

    @NotNull
    @Override
    protected List<Change> parseStatusInfo(@NotNull List<VcsFileStatusInfo> changes, int parentIndex) {
      List<Change> result = ContainerUtil.newArrayList();
      for (VcsFileStatusInfo info : changes) {
        String filePath = info.getFirstPath();
        HgRevisionNumber parentRevision = myRevisionNumber.getParents().isEmpty() ? null : myRevisionNumber.getParents().get(parentIndex);
        switch (info.getType()) {
          case MODIFICATION:
            result.add(createChange(myProject, getRoot(), filePath, parentRevision, filePath, myRevisionNumber, FileStatus.MODIFIED));
            break;
          case NEW:
            result.add(createChange(myProject, getRoot(), null, null, filePath, myRevisionNumber, FileStatus.ADDED));
            break;
          case DELETED:
            result.add(createChange(myProject, getRoot(), filePath, parentRevision, null, myRevisionNumber, FileStatus.DELETED));
            break;
          case MOVED:
            result.add(createChange(myProject, getRoot(), filePath, parentRevision, info.getSecondPath(), myRevisionNumber,
                                    HgChangeProvider.RENAMED));
            break;
        }
      }
      return result;
    }
  }
}
