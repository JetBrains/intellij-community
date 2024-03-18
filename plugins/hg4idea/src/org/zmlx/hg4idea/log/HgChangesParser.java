// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.log;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.impl.VcsChangesLazilyParsedDetails;
import com.intellij.vcs.log.impl.VcsFileStatusInfo;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.provider.HgChangeProvider;

import java.util.ArrayList;
import java.util.List;

import static org.zmlx.hg4idea.log.HgHistoryUtil.createChange;

class HgChangesParser implements VcsChangesLazilyParsedDetails.ChangesParser {
  private final @NotNull HgRevisionNumber myRevisionNumber;

  HgChangesParser(@NotNull HgRevisionNumber revisionNumber) {
    myRevisionNumber = revisionNumber;
  }

  @Override
  public List<Change> parseStatusInfo(@NotNull Project project,
                                      @NotNull VcsShortCommitDetails commit,
                                      @NotNull List<VcsFileStatusInfo> changes,
                                      int parentIndex) {
    List<Change> result = new ArrayList<>();
    for (VcsFileStatusInfo info : changes) {
      String filePath = info.getFirstPath();
      HgRevisionNumber parentRevision =
        myRevisionNumber.getParents().isEmpty() ? null : myRevisionNumber.getParents().get(parentIndex);
      Change change = switch (info.getType()) {
        case MODIFICATION ->
          createChange(project, commit.getRoot(), filePath, parentRevision, filePath, myRevisionNumber, FileStatus.MODIFIED);
        case NEW -> createChange(project, commit.getRoot(), null, null, filePath, myRevisionNumber, FileStatus.ADDED);
        case DELETED -> createChange(project, commit.getRoot(), filePath, parentRevision, null, myRevisionNumber, FileStatus.DELETED);
        case MOVED -> createChange(project, commit.getRoot(), filePath, parentRevision, info.getSecondPath(), myRevisionNumber,
                                   HgChangeProvider.FileStatuses.RENAMED);
      };
      result.add(change);
    }
    return result;
  }
}
