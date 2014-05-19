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

  public VcsShortCommitDetailsImpl(@NotNull Hash hash, @NotNull List<Hash> parents, long timeStamp, @NotNull VirtualFile root,
                                   @NotNull String subject, @NotNull VcsUser author) {
    super(hash, parents, timeStamp);
    myRoot = root;
    mySubject = subject;
    myAuthor = author;
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

  @Override
  public String toString() {
    return getId().toShortString() + "(" + getSubject() + ")";
  }

}
