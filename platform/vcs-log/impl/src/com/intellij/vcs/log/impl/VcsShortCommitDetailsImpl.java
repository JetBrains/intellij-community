package com.intellij.vcs.log.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.TimedVcsCommit;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.VcsUser;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class VcsShortCommitDetailsImpl implements VcsShortCommitDetails {

  @NotNull private final TimedVcsCommit myTimeCommitParents;
  @NotNull private final String mySubject;
  @NotNull private final VcsUser myAuthor;
  @NotNull private final VirtualFile myRoot;

  public VcsShortCommitDetailsImpl(@NotNull Hash hash, @NotNull List<Hash> parents, long timeStamp, @NotNull VirtualFile root,
                                   @NotNull String subject, @NotNull VcsUser author) {
    myTimeCommitParents = new TimedVcsCommitImpl(hash, parents, timeStamp);
    myRoot = root;
    mySubject = subject;
    myAuthor = author;
  }

  @NotNull
  @Override
  public Hash getHash() {
    return myTimeCommitParents.getHash();
  }

  @NotNull
  @Override
  public VirtualFile getRoot() {
    return myRoot;
  }

  @NotNull
  @Override
  public List<Hash> getParents() {
    return myTimeCommitParents.getParents();
  }

  @Override
  public long getTime() {
    return myTimeCommitParents.getTime();
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
    return getHash().toShortString() + "(" + getSubject() + ")";
  }

}
