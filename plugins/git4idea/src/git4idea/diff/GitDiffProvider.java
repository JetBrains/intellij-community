package git4idea.diff;
/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 *
 * Copyright 2007 Decentrix Inc
 * Copyright 2007 Aspiro AS
 * Copyright 2008 MQSoftware
 * Copyright 2008 JetBrains s.r.o.
 *
 * Authors: gevession, Erlend Simonsen & Mark Scott
 *
 * This code was originally derived from the MKS & Mercurial IDEA VCS plugins
 */

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.*;
import git4idea.history.GitHistoryUtils;
import git4idea.i18n.GitBundle;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/**
 * Git diff provider
 */
public class GitDiffProvider implements DiffProvider {
  /**
   * The context project
   */
  private final Project myProject;
  /**
   * The status manager for the project
   */
  private final FileStatusManager myStatusManager;
  /**
   *
   */
  private static final Set<FileStatus> ourGoodStatuses;

  static {
    ourGoodStatuses = new THashSet<FileStatus>();
    ourGoodStatuses.addAll(
      Arrays.asList(FileStatus.NOT_CHANGED, FileStatus.DELETED, FileStatus.MODIFIED, FileStatus.MERGE, FileStatus.MERGED_WITH_CONFLICTS));
  }

  /**
   * A constructor
   *
   * @param project the context project
   */
  public GitDiffProvider(@NotNull Project project) {
    myProject = project;
    myStatusManager = FileStatusManager.getInstance(myProject);
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  public VcsRevisionNumber getCurrentRevision(VirtualFile file) {
    if (file.isDirectory()) {
      return null;
    }
    try {
      return GitHistoryUtils.getCurrentRevision(myProject, VcsUtil.getFilePath(file.getPath()));
    }
    catch (VcsException e) {
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  public ItemLatestState getLastRevision(VirtualFile file) {
    if (file.isDirectory()) {
      return null;
    }
    if (!ourGoodStatuses.contains(myStatusManager.getStatus(file))) {
      return null;
    }
    try {
      return GitHistoryUtils.getLastRevision(myProject, VcsUtil.getFilePath(file.getPath()));
    }
    catch (VcsException e) {
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  public ContentRevision createFileContent(VcsRevisionNumber revisionNumber, VirtualFile selectedFile) {
    if (selectedFile.isDirectory()) {
      return null;
    }
    final String path = selectedFile.getPath();
    if (GitUtil.gitRootOrNull(selectedFile) == null) {
      return null;
    }
    try {
      FilePath filePath = VcsUtil.getFilePath(path);
      for (VcsFileRevision f : GitHistoryUtils.history(myProject, filePath)) {
        GitFileRevision gitRevision = (GitFileRevision)f;
        if (f.getRevisionNumber().equals(revisionNumber)) {
          return new GitContentRevision(gitRevision.getPath(), (GitRevisionNumber)revisionNumber, myProject, selectedFile.getCharset());
        }
      }
      GitContentRevision candidate =
        new GitContentRevision(filePath, (GitRevisionNumber)revisionNumber, myProject, selectedFile.getCharset());
      try {
        candidate.getContent();
        return candidate;
      }
      catch (VcsException e) {
        // file does not exists
      }
    }
    catch (VcsException e) {
      GitVcs.getInstance(myProject).showErrors(Collections.singletonList(e), GitBundle.message("diff.find.error", path));
    }
    return null;
  }

  public ItemLatestState getLastRevision(FilePath filePath) {
    if (filePath.isDirectory()) {
      return null;
    }
    final VirtualFile vf = filePath.getVirtualFile();
    if (vf != null) {
      if (! ourGoodStatuses.contains(myStatusManager.getStatus(vf))) {
        return null;
      }
    }
    try {
      return GitHistoryUtils.getLastRevision(myProject, filePath);
    }
    catch (VcsException e) {
      return null;
    }
  }

  public VcsRevisionNumber getLatestCommittedRevision(VirtualFile vcsRoot) {
    // todo
    return null;
  }
}
