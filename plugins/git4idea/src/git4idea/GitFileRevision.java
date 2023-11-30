// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  private final @NotNull Project myProject;
  private final @NotNull FilePath myPath;
  private final @NotNull GitRevisionNumber myRevision;
  private final @Nullable Couple<Couple<String>> myAuthorAndCommitter;
  private final @Nullable String myMessage;
  private final @Nullable String myBranch;
  private final @Nullable Date myAuthorTime;
  private final @NotNull Collection<String> myParents;
  private final @Nullable VirtualFile myRoot;
  private final boolean myIsDeleted;

  public GitFileRevision(@NotNull Project project, @NotNull FilePath path, @NotNull GitRevisionNumber revision) {
    this(project, null, path, revision);
  }

  public GitFileRevision(@NotNull Project project,
                         @Nullable VirtualFile root,
                         @NotNull FilePath path,
                         @NotNull GitRevisionNumber revision) {
    this(project, root, path, revision, null, null, null, null, Collections.emptyList(), false);
  }

  public GitFileRevision(@NotNull Project project, @Nullable VirtualFile root, @NotNull FilePath path, @NotNull GitRevisionNumber revision,
                         @Nullable Couple<Couple<String>> authorAndCommitter, @Nullable String message,
                         @Nullable String branch, final @Nullable Date authorTime, @NotNull Collection<String> parents, boolean isDeleted) {
    myProject = project;
    myRoot = root;
    myPath = path;
    myRevision = revision;
    myAuthorAndCommitter = authorAndCommitter;
    myMessage = message;
    myBranch = branch;
    myAuthorTime = authorTime;
    myParents = parents;
    myIsDeleted = isDeleted;
  }

  @Override
  public @NotNull FilePath getPath() {
    return myPath;
  }

  @Override
  public @Nullable RepositoryLocation getChangedRepositoryPath() {
    return null;
  }

  @Override
  public @NotNull VcsRevisionNumber getRevisionNumber() {
    return myRevision;
  }

  @Override
  public Date getRevisionDate() {
    return myRevision.getTimestamp();
  }

  @Override
  public @Nullable Date getAuthorDate() {
    return myAuthorTime;
  }

  @Override
  public @Nullable String getAuthor() {
    return Pair.getFirst(Pair.getFirst(myAuthorAndCommitter));
  }

  @Override
  public @Nullable String getAuthorEmail() {
    return Pair.getSecond(Pair.getFirst(myAuthorAndCommitter));
  }

  @Override
  public @Nullable String getCommitterName() {
    return Pair.getFirst(Pair.getSecond(myAuthorAndCommitter));
  }

  @Override
  public @Nullable String getCommitterEmail() {
    return Pair.getSecond(Pair.getSecond(myAuthorAndCommitter));
  }

  @Override
  public @Nullable String getCommitMessage() {
    return myMessage;
  }

  @Override
  public @Nullable String getBranchName() {
    return myBranch;
  }

  @Override
  public synchronized byte @NotNull [] loadContent() throws VcsException {
    VirtualFile root = myRoot != null ? myRoot : GitUtil.getRootForFile(myProject, myPath);
    return GitFileUtils.getFileContent(myProject, root, myRevision.getRev(), VcsFileUtil.relativePath(root, myPath));
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

  public @NotNull Collection<String> getParents() {
    return myParents;
  }

  public @NotNull String getHash() {
    return myRevision.getRev();
  }

  @Override
  public boolean isDeleted() {
    return myIsDeleted;
  }
}
