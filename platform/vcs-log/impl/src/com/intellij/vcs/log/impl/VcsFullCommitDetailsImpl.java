package com.intellij.vcs.log.impl;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class VcsFullCommitDetailsImpl extends VcsShortCommitDetailsImpl implements VcsFullCommitDetails {

  @NotNull private final String myFullMessage;

  @NotNull private final String myAuthorEmail;
  @NotNull private final String myCommitterName;
  @NotNull private final String myCommitterEmail;
  private final long myCommitTime;

  @NotNull private final Collection<Change> myChanges;

  public VcsFullCommitDetailsImpl(@NotNull Hash hash, @NotNull List<Hash> parents, long authorTime, @NotNull String subject,
                                  @NotNull String authorName, @NotNull String authorEmail, @NotNull String message,
                                  @NotNull String committerName,
                                  @NotNull String committerEmail, long commitTime, @NotNull List<Change> changes) {
    super(hash, parents, authorTime, subject, authorName);
    myAuthorEmail = authorEmail;
    myCommitterName = committerName;
    myCommitterEmail = committerEmail;
    myCommitTime = commitTime;
    myFullMessage = message;
    myChanges = changes;
  }

  @Override
  @NotNull
  public final String getFullMessage() {
    return myFullMessage;
  }

  @Override
  @NotNull
  public final Collection<Change> getChanges() {
    return myChanges;
  }

  @Override
  @NotNull
  public String getAuthorEmail() {
    return myAuthorEmail;
  }

  @Override
  @NotNull
  public String getCommitterName() {
    return myCommitterName;
  }

  @Override
  @NotNull
  public String getCommitterEmail() {
    return myCommitterEmail;
  }

  @Override
  public long getCommitTime() {
    return myCommitTime;
  }
}
