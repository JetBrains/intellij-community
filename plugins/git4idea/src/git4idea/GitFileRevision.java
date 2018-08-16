// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
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

  @Override
  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return myRevision;
  }

  @Override
  public Date getRevisionDate() {
    return myRevision.getTimestamp();
  }

  @Override
  @Nullable
  public Date getAuthorDate() {
    return myAuthorTime;
  }

  @Override
  @Nullable
  public String getAuthor() {
    return Pair.getFirst(Pair.getFirst(myAuthorAndCommitter));
  }

  @Nullable
  @Override
  public String getAuthorEmail() {
    return Pair.getSecond(Pair.getFirst(myAuthorAndCommitter));
  }

  @Nullable
  @Override
  public String getCommitterName() {
    return Pair.getFirst(Pair.getSecond(myAuthorAndCommitter));
  }

  @Nullable
  @Override
  public String getCommitterEmail() {
    return Pair.getSecond(Pair.getSecond(myAuthorAndCommitter));
  }

  @Override
  @Nullable
  public String getCommitMessage() {
    return myMessage;
  }

  @Override
  @Nullable
  public String getBranchName() {
    return myBranch;
  }

  @Override
  public synchronized byte[] loadContent() throws VcsException {
    VirtualFile root = getRoot();
    return GitFileUtils.getFileContent(myProject, root, myRevision.getRev(), VcsFileUtil.relativePath(root, myPath));
  }

  private VirtualFile getRoot() throws VcsException {
    return myRoot != null ? myRoot : GitUtil.getGitRoot(myPath);
  }

  @Override
  public synchronized byte[] getContent() throws VcsException {
    return loadContent();
  }

  @Override
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
