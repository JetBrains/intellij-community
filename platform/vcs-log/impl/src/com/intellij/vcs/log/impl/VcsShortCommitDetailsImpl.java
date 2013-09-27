package com.intellij.vcs.log.impl;

import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.TimedVcsCommit;
import com.intellij.vcs.log.VcsShortCommitDetails;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class VcsShortCommitDetailsImpl implements VcsShortCommitDetails {

  @NotNull private final TimedVcsCommit myTimeCommitParents;
  @NotNull private final String mySubject;
  @NotNull private final String myAuthorName;

  public VcsShortCommitDetailsImpl(@NotNull Hash hash, @NotNull List<Hash> parents, long timeStamp,
                                   @NotNull String subject, @NotNull String authorName) {
    myTimeCommitParents = new TimedVcsCommitImpl(hash, parents, timeStamp);
    mySubject = subject;
    myAuthorName = authorName;
  }

  @NotNull
  @Override
  public Hash getHash() {
    return myTimeCommitParents.getHash();
  }

  @NotNull
  @Override
  public List<Hash> getParents() {
    return myTimeCommitParents.getParents();
  }

  @Override
  public long getAuthorTime() {
    return myTimeCommitParents.getAuthorTime();
  }

  @Override
  @NotNull
  public final String getSubject() {
    return mySubject;
  }

  @Override
  @NotNull
  public final String getAuthorName() {
    return myAuthorName;
  }

}
