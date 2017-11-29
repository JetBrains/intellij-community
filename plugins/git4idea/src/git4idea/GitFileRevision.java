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
package git4idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsFileRevisionEx;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

public class GitFileRevision extends VcsFileRevisionEx implements Comparable<VcsFileRevision> {

  @NotNull private final Project myProject;
  @NotNull private final FilePath myPath;
  @NotNull private final GitRevisionNumber myRevision;
  @Nullable private final Couple<Couple<String>> myAuthorAndCommitter;
  @Nullable private final String myMessage;
  @Nullable private final String myBranch;
  @Nullable private final Date myAuthorTime;
  @NotNull private final Collection<String> myParents;
  @Nullable private final VirtualFile myRoot;

  public GitFileRevision(@NotNull Project project, @NotNull FilePath path, @NotNull GitRevisionNumber revision) {
    this(project, null, path, revision, null, null, null, null, Collections.emptyList());
  }

  public GitFileRevision(@NotNull Project project, @Nullable VirtualFile root, @NotNull FilePath path, @NotNull GitRevisionNumber revision,
                         @Nullable Couple<Couple<String>> authorAndCommitter, @Nullable String message,
                         @Nullable String branch, @Nullable final Date authorTime, @NotNull Collection<String> parents) {
    myProject = project;
    myRoot = root;
    myPath = path;
    myRevision = revision;
    myAuthorAndCommitter = authorAndCommitter;
    myMessage = message;
    myBranch = branch;
    myAuthorTime = authorTime;
    myParents = parents;
  }

  @Override
  @NotNull
  public FilePath getPath() {
    return myPath;
  }

  @Nullable
  @Override
  public RepositoryLocation getChangedRepositoryPath() {
    return null;
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return myRevision;
  }

  public Date getRevisionDate() {
    return myRevision.getTimestamp();
  }

  @Nullable
  public Date getAuthorDate() {
    return myAuthorTime;
  }

  @Nullable
  public String getAuthor() {
    if (myAuthorAndCommitter != null) {
      return myAuthorAndCommitter.getFirst().getFirst();
    }
    return null;
  }

  @Nullable
  @Override
  public String getAuthorEmail() {
    if (myAuthorAndCommitter != null) {
      return myAuthorAndCommitter.getFirst().getSecond();
    }
    return null;
  }

  @Nullable
  @Override
  public String getCommitterName() {
    if (myAuthorAndCommitter != null) {
      return myAuthorAndCommitter.getSecond() == null ? null : myAuthorAndCommitter.getSecond().getFirst();
    }
    return null;
  }

  @Nullable
  @Override
  public String getCommitterEmail() {
    if (myAuthorAndCommitter != null) {
      return myAuthorAndCommitter.getSecond() == null ? null : myAuthorAndCommitter.getSecond().getSecond();
    }
    return null;
  }

  @Nullable
  public String getCommitMessage() {
    return myMessage;
  }

  @Nullable
  public String getBranchName() {
    return myBranch;
  }

  public synchronized byte[] loadContent() throws VcsException {
    VirtualFile root = getRoot();
    return GitFileUtils.getFileContent(myProject, root, myRevision.getRev(), VcsFileUtil.relativePath(root, myPath));
  }

  private VirtualFile getRoot() throws VcsException {
    return myRoot != null ? myRoot : GitUtil.getGitRoot(myPath);
  }

  public synchronized byte[] getContent() throws VcsException {
    return loadContent();
  }

  public int compareTo(VcsFileRevision rev) {
    if (rev instanceof GitFileRevision) return myRevision.compareTo(((GitFileRevision)rev).myRevision);
    return getRevisionDate().compareTo(rev.getRevisionDate());
  }

  @Override
  public String toString() {
    return myPath.getName() + ":" + myRevision.getShortRev();
  }

  @NotNull
  public Collection<String> getParents() {
    return myParents;
  }

  @NotNull
  public String getHash() {
    return myRevision.getRev();
  }
}
