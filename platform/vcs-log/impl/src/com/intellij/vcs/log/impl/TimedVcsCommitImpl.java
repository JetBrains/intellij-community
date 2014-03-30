package com.intellij.vcs.log.impl;

import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.TimedVcsCommit;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class TimedVcsCommitImpl extends VcsCommitImpl implements TimedVcsCommit {

  private final long myTime;

  public TimedVcsCommitImpl(@NotNull Hash hash, @NotNull List<Hash> parents, long timeStamp) {
    super(hash, parents);
    myTime = timeStamp;
  }

  @Override
  public final long getTime() {
    return myTime;
  }

  @Override
  public String toString() {
    return super.toString() + ":" + myTime;
  }

}
