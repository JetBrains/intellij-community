package git4idea;
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
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    return new GitContentRevision(VcsUtil.getFilePath(selectedFile.getPath()), (GitRevisionNumber)revisionNumber, project);
  }
}
