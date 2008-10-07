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
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import git4idea.commands.GitCommand;
import git4idea.config.GitVcsSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Date;

/**
 * Git file revision
 */
public class GitFileRevision implements VcsFileRevision, Comparable<VcsFileRevision> {
  private final FilePath path;
  private final GitRevisionNumber revision;
  private final String author;
  private final String message;
  private byte[] content;
  private final Project project;
  private final String branch;

  public GitFileRevision(@NotNull Project project,
                         @NotNull FilePath path,
                         @NotNull GitRevisionNumber revision,
                         @Nullable String author,
                         @Nullable String message,
                         @Nullable String branch) {
    this.project = project;
    this.path = path;
    this.revision = revision;
    this.author = author;
    this.message = message;
    this.branch = branch;
  }

  public VcsRevisionNumber getRevisionNumber() {
    return revision;
  }

  public Date getRevisionDate() {
    return revision.getTimestamp();
  }

  public String getAuthor() {
    return author;
  }

  public String getCommitMessage() {
    return message;
  }

  public String getBranchName() {
    return branch;
  }

  public synchronized void loadContent() throws VcsException {
    GitCommand command = new GitCommand(project, GitVcsSettings.getInstance(project), GitUtil.getVcsRoot(project, path));
    String c = command.getContents(path.getPath(), revision.getRev());
    if (c != null && c.length() > 0) {
      content = c.getBytes();
    }
    else {
      content = null;
    }
  }

  public synchronized byte[] getContent() throws IOException {
    if (content == null) {
      try {
        loadContent();
      }
      catch (VcsException e) {
        throw new IOException(e.getMessage());
      }
    }
    return content;
  }

  public int compareTo(VcsFileRevision rev) {
    if (rev instanceof GitFileRevision) return revision.compareTo(((GitFileRevision)rev).revision);
    return getRevisionDate().compareTo(rev.getRevisionDate());
  }
}
