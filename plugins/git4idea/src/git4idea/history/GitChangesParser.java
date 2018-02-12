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
package git4idea.history;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class GitChangesParser {

  @NotNull
  public static List<Change> parse(@NotNull Project project,
                                   @NotNull VirtualFile root,
                                   @NotNull List<GitLogStatusInfo> statusInfos,
                                   @NotNull String hash,
                                   @NotNull Date date,
                                   @Nullable String parentsHash) throws VcsException {
    GitRevisionNumber thisRevision = new GitRevisionNumber(hash, date);
    GitRevisionNumber parentRevision = parentsHash == null ? null : new GitRevisionNumber(parentsHash);

    List<Change> result = new ArrayList<>();
    for (GitLogStatusInfo statusInfo : statusInfos) {
      result.add(parseChange(project, root, thisRevision, parentRevision, statusInfo));
    }
    return result;
  }

  @NotNull
  private static Change parseChange(@NotNull Project project,
                                    @NotNull VirtualFile vcsRoot,
                                    @NotNull VcsRevisionNumber thisRevision,
                                    @Nullable VcsRevisionNumber parentRevision,
                                    @NotNull GitLogStatusInfo statusInfo) throws VcsException {
    final ContentRevision before;
    final ContentRevision after;
    FileStatus status = null;
    final String path = statusInfo.getFirstPath();

    switch (statusInfo.getType()) {
      case ADDED:
        before = null;
        status = FileStatus.ADDED;
        after = GitContentRevision.createRevision(vcsRoot, path, thisRevision, project, false, true);
        break;
      case UNRESOLVED:
        status = FileStatus.MERGED_WITH_CONFLICTS;
      case MODIFIED:
        if (status == null) {
          status = FileStatus.MODIFIED;
        }
        final FilePath filePath = GitContentRevision.createPath(vcsRoot, path, true, true);
        before = GitContentRevision.createRevision(vcsRoot, path, parentRevision, project, false, true);
        after = GitContentRevision.createRevision(filePath, thisRevision, project, null);
        break;
      case DELETED:
        status = FileStatus.DELETED;
        final FilePath filePathDeleted = GitContentRevision.createPath(vcsRoot, path, true, true);
        before = GitContentRevision.createRevision(filePathDeleted, parentRevision, project, null);
        after = null;
        break;
      case COPIED:
      case RENAMED:
        status = FileStatus.MODIFIED;
        String secondPath = statusInfo.getSecondPath();
        final FilePath filePathAfterRename = GitContentRevision.createPath(vcsRoot, secondPath == null ? path : secondPath,
                                                                           false, true);
        before = GitContentRevision.createRevision(vcsRoot, path, parentRevision, project, true, true);
        after = GitContentRevision.createRevision(filePathAfterRename, thisRevision, project, null);
        break;
      case TYPE_CHANGED:
        status = FileStatus.MODIFIED;
        final FilePath filePath2 = GitContentRevision.createPath(vcsRoot, path, true, true);
        before = GitContentRevision.createRevision(vcsRoot, path, parentRevision, project, false, true);
        after = GitContentRevision.createRevision(filePath2, thisRevision, project, null);
        break;
      default:
        throw new AssertionError("Unknown file status: " + statusInfo);
    }
    return new Change(before, after, status);
  }
}
