// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.impl.VcsFileStatusInfo;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class GitChangesParser {
  private static final Logger LOG = Logger.getInstance(GitChangesParser.class);

  @NotNull
  public static List<Change> parse(@NotNull Project project,
                                   @NotNull VirtualFile root,
                                   @NotNull List<VcsFileStatusInfo> statusInfos,
                                   @NotNull String hash,
                                   @NotNull Date date,
                                   @Nullable String parentsHash) {
    GitRevisionNumber thisRevision = new GitRevisionNumber(hash, date);
    GitRevisionNumber parentRevision = parentsHash == null ? null : new GitRevisionNumber(parentsHash);

    List<Change> result = new ArrayList<>();
    for (VcsFileStatusInfo statusInfo : statusInfos) {
      result.add(parseChange(project, root, thisRevision, parentRevision, statusInfo));
    }
    return result;
  }

  @NotNull
  private static Change parseChange(@NotNull Project project,
                                    @NotNull VirtualFile vcsRoot,
                                    @NotNull VcsRevisionNumber thisRevision,
                                    @Nullable VcsRevisionNumber parentRevision,
                                    @NotNull VcsFileStatusInfo statusInfo) {
    final ContentRevision before;
    final ContentRevision after;
    final String path = statusInfo.getFirstPath();
    final FilePath filePath = GitContentRevision.createPath(vcsRoot, path);

    FileStatus status;
    switch (statusInfo.getType()) {
      case NEW -> {
        before = null;
        status = FileStatus.ADDED;
        after = GitContentRevision.createRevision(filePath, thisRevision, project);
      }
      case MODIFICATION -> {
        status = FileStatus.MODIFIED;
        before = GitContentRevision.createRevision(filePath, parentRevision, project);
        after = GitContentRevision.createRevision(filePath, thisRevision, project);
      }
      case DELETED -> {
        status = FileStatus.DELETED;
        before = GitContentRevision.createRevision(filePath, parentRevision, project);
        after = null;
      }
      case MOVED -> {
        status = FileStatus.MODIFIED;
        String secondPath = statusInfo.getSecondPath();
        final FilePath filePathAfterRename = secondPath == null ? filePath : GitContentRevision.createPath(vcsRoot, secondPath);
        before = GitContentRevision.createRevision(filePath, parentRevision, project);
        after = GitContentRevision.createRevision(filePathAfterRename, thisRevision, project);
      }
      default -> throw new AssertionError("Unknown file status: " + statusInfo);
    }
    return new Change(before, after, status);
  }

  @NotNull
  static Change.Type getChangeType(@NotNull GitChangeType type) {
    return switch (type) {
      case ADDED -> Change.Type.NEW;
      case TYPE_CHANGED, MODIFIED -> Change.Type.MODIFICATION;
      case DELETED -> Change.Type.DELETED;
      case COPIED, RENAMED -> Change.Type.MOVED;
      default -> {
        LOG.error("Unknown git change type: " + type);
        yield Change.Type.MODIFICATION;
      }
    };
  }
}
