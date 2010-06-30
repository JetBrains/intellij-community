/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package git4idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.GitBinaryHandler;
import git4idea.commands.GitCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;

/**
 * Git file revision
 */
public class GitFileRevision implements VcsFileRevision, Comparable<VcsFileRevision> {
  /**
   * encoding to be used for binary output
   */
  @SuppressWarnings({"HardCodedStringLiteral"}) private final static Charset BIN_ENCODING = Charset.forName("ISO-8859-1");
  private final FilePath path;
  private final GitRevisionNumber revision;
  private final String author;
  private final String message;
  private byte[] content;
  private final Project project;
  private final String branch;

  public GitFileRevision(@NotNull Project project, @NotNull FilePath path, @NotNull GitRevisionNumber revision) {
    this(project, path, revision, null, null, null);
  }

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

  /**
   * @return file path
   */
  public FilePath getPath() {
    return path;
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
    final VirtualFile root = GitUtil.getGitRoot(path);
    GitBinaryHandler h = new GitBinaryHandler(project, root, GitCommand.SHOW);
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters(revision.getRev() + ":" + GitUtil.relativePath(root, path));
    content = h.run();
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
