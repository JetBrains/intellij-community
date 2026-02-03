// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.provider;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.vcs.CommittedChangeListForRevision;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgVcs;

import java.util.Collection;
import java.util.Date;

public class HgCommittedChangeList extends CommittedChangeListForRevision {
  private static final @NlsSafe String DEFAULT_BRANCH = "default";

  private final @NotNull HgVcs myVcs;
  private final @NotNull String myBranch;

  public HgCommittedChangeList(@NotNull HgVcs vcs, @NotNull HgRevisionNumber revision, @NotNull String branch, String comment,
                               String committerName, Date commitDate, Collection<Change> changes) {
    super(revision.asString() + ": " + comment, comment, committerName, commitDate, changes, revision);
    myVcs = vcs;
    myBranch = StringUtil.isEmpty(branch) ? DEFAULT_BRANCH : branch;
  }

  @Override
  public @NotNull HgRevisionNumber getRevisionNumber() {
    return (HgRevisionNumber)super.getRevisionNumber();
  }

  @Override
  public @NotNull String getBranch() {
    return myBranch;
  }

  @Override
  public AbstractVcs getVcs() {
    return myVcs;
  }

  @Override
  public String toString() {
    return getComment();
  }
}
