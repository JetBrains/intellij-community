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
 * Authors: gevession, Erlend Simonsen & Mark Scott
 *
 * This code was originally derived from the MKS & Mercurial IDEA VCS plugins
 */

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import git4idea.commands.GitCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Git content revision
 */
public class GitContentRevision implements ContentRevision {
  private final FilePath file;
  private final GitRevisionNumber revision;
  private final Project project;

  public GitContentRevision(@NotNull FilePath file, @NotNull GitRevisionNumber revision, @NotNull Project project) {
    this.project = project;
    this.file = file;
    this.revision = revision;
  }

  @Nullable
  public String getContent() throws VcsException {
    if (file == null || file.isDirectory()) return null;

    GitCommand command = new GitCommand(project, GitVcsSettings.getInstance(project), GitUtil.getVcsRoot(project, file));

    return command.getContents(file.getPath(), revision.getRev());
  }

  @NotNull
  public FilePath getFile() {
    return file;
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return revision;
  }

  public boolean equals(Object obj) {
    if (this == obj) return true;
    if ((obj == null) || (obj.getClass() != getClass())) return false;

    GitContentRevision test = (GitContentRevision)obj;
    return (file.equals(test.file) && revision.equals(test.revision));
  }

  public int hashCode() {
    if (file != null && revision != null) return file.hashCode() + revision.hashCode();
    return 0;
  }
}
