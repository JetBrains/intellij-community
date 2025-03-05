// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.versionBrowser;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

public class CommittedChangeListImpl implements CommittedChangeList {
  private final String myCommitterName;
  private final Date myCommitDate;
  private final String myName;
  private String myComment;
  private final long myNumber;
  protected ArrayList<Change> myChanges;

  public CommittedChangeListImpl(final String name, final String comment, final String committerName,
                                 final long number, final Date commitDate, final Collection<Change> changes) {
    myCommitterName = committerName;
    myCommitDate = commitDate;
    myName = name;
    myComment = comment;
    myChanges = new ArrayList<>(changes);
    myNumber = number;
  }

  @Override
  public String getCommitterName() {
    return myCommitterName;
  }

  @Override
  public Date getCommitDate() {
    return myCommitDate;
  }

  @Override
  public long getNumber() {
    return myNumber;
  }

  @Override
  public String getBranch() {
    return null;
  }

  @Override
  public AbstractVcs getVcs() {
    return null;
  }

  @Override
  public boolean isModifiable() {
    return true;
  }

  @Override
  public void setDescription(String newMessage) {
    myComment = newMessage;
  }

  @Override
  public Collection<Change> getChanges() {
    return myChanges;
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public String getComment() {
    return myComment;
  }
}
