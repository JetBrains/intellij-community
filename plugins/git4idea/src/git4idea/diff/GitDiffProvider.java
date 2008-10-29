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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Date;

/**
 * Git diff provider
 */
public class GitDiffProvider implements DiffProvider {
  private final Project project;

  public GitDiffProvider(@NotNull Project proj) {
    project = proj;
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  public VcsRevisionNumber getCurrentRevision(VirtualFile file) {
    return new GitRevisionNumber(GitRevisionNumber.TIP, new Date());
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  public ItemLatestState getLastRevision(VirtualFile file) {
    return new ItemLatestState(new GitRevisionNumber(GitRevisionNumber.TIP, new Date(file.getModificationStamp())), true);
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  public ContentRevision createFileContent(VcsRevisionNumber revisionNumber, VirtualFile selectedFile) {
    final String path = selectedFile.getPath();
    if (GitUtil.gitRootOrNull(selectedFile) == null) {
      return null;
    }
    try {
      for (VcsFileRevision f : GitHistoryUtils.history(project, VcsUtil.getFilePath(path))) {
        GitFileRevision gitRevision = (GitFileRevision)f;
        if (f.getRevisionNumber().equals(revisionNumber)) {
          return new GitContentRevision(gitRevision.getPath(), (GitRevisionNumber)revisionNumber, project);
        }
      }
    }
    catch (VcsException e) {
      GitVcs.getInstance(project).showErrors(Collections.singletonList(e), GitBundle.message("diff.find.error", path));
    }
    return null;
  }
}
