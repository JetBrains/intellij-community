package com.intellij.vcs.log.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.VcsUser;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class VcsShortCommitDetailsImpl extends TimedVcsCommitImpl implements VcsShortCommitDetails {

  @NotNull private final String mySubject;
  @NotNull private final VcsUser myAuthor;
  @NotNull private final VirtualFile myRoot;
  @NotNull private final VcsUser myCommitter;
  private final long myAuthorTime;

  public VcsShortCommitDetailsImpl(@NotNull Hash hash, @NotNull List<Hash> parents, long commitTime, @NotNull VirtualFile root,
                                   @NotNull String subject, @NotNull VcsUser author, @NotNull VcsUser committer, long authorTime) {
    super(hash, parents, commitTime);
    myRoot = root;
    mySubject = subject;
    myAuthor = author;
    myCommitter = committer;
    myAuthorTime = authorTime;
  }

  @NotNull
  @Override
  public VirtualFile getRoot() {
    return myRoot;
  }

  @Override
  @NotNull
  public final String getSubject() {
    return mySubject;
  }

  @Override
  @NotNull
  public final VcsUser getAuthor() {
    return myAuthor;
  }

  @NotNull
  @Override
  public VcsUser getCommitter() {
    return myCommitter;
  }

  @Override
  public long getAuthorTime() {
    return myAuthorTime;
  }

  @Override
  public long getCommitTime() {
    return getTimestamp();
  }

  @Override
  public String toString() {
    return getId().toShortString() + "(" + getSubject() + ")";
  }

}
