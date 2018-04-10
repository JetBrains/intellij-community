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
import com.intellij.vcs.log.impl.VcsStatusDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
                  List<List<HgFileStatusInfo>> reportedChanges) {
    super(hash, parentsHashes, time, root, vcsRevisionNumber.getSubject(), author, vcsRevisionNumber.getCommitMessage(), author, time);
    myRevisionNumber = vcsRevisionNumber;
    myChanges.set(reportedChanges.isEmpty() ? EMPTY_CHANGES : new UnparsedChanges(project, reportedChanges));
  }

  private class UnparsedChanges extends VcsChangesLazilyParsedDetails.UnparsedChanges<HgFileStatusInfo> {
    private UnparsedChanges(@NotNull Project project,
                            @NotNull List<List<HgFileStatusInfo>> changesOutput) {
      super(project, changesOutput, new HgChangesDescriptor());
    }

    @NotNull
    @Override
    protected List<Change> parseStatusInfo(@NotNull List<HgFileStatusInfo> changes, int parentIndex) {
      List<Change> result = ContainerUtil.newArrayList();
      for (HgFileStatusInfo info : changes) {
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

  private static class HgChangesDescriptor extends VcsStatusDescriptor<HgFileStatusInfo> {
    @NotNull
    @Override
    protected HgFileStatusInfo createStatus(@NotNull Change.Type type, @NotNull String path, @Nullable String secondPath) {
      return new HgFileStatusInfo(type, path, secondPath);
    }

    @NotNull
    @Override
    public String getFirstPath(@NotNull HgFileStatusInfo info) {
      return info.getFirstPath();
    }

    @Nullable
    @Override
    public String getSecondPath(@NotNull HgFileStatusInfo info) {
      return info.getSecondPath();
    }

    @NotNull
    @Override
    public Change.Type getType(@NotNull HgFileStatusInfo info) {
      return info.getType();
    }
  }

  public static class HgFileStatusInfo {
    @NotNull private final Change.Type myType;
    @NotNull private final String myFirstPath;
    @Nullable private final String mySecondPath;

    public HgFileStatusInfo(@NotNull Change.Type type, @NotNull String firstPath, @Nullable String secondPath) {
      myType = type;
      myFirstPath = firstPath;
      mySecondPath = secondPath;
    }

    @NotNull
    public Change.Type getType() {
      return myType;
    }

    @NotNull
    public String getFirstPath() {
      return myFirstPath;
    }

    @Nullable
    public String getSecondPath() {
      return mySecondPath;
    }
  }
}
