// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.vcs.CommittedChangeListForRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitVcs;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Date;

public class GitCommittedChangeList extends CommittedChangeListForRevision {

  private final boolean myModifiable;
  private final AbstractVcs myVcs;

  public GitCommittedChangeList(@NotNull String name,
                                @NotNull String comment,
                                @NotNull String committerName,
                                @NotNull GitRevisionNumber revisionNumber,
                                @NotNull Date commitDate,
                                @NotNull Collection<Change> changes,
                                @NotNull GitVcs vcs,
                                boolean isModifiable) {
    super(name, comment, committerName, commitDate, changes, revisionNumber);
    myVcs = vcs;
    myModifiable = isModifiable;
  }

  @Override
  public boolean isModifiable() {
    return myModifiable;
  }

  @Override
  public long getNumber() {
    @NonNls String revisionNumber = getRevisionNumber().asString();
    // TODO: wrong braces?
    return Long.parseLong(revisionNumber.substring(0, 15), 16) << 4 + Integer.parseInt(revisionNumber.substring(15, 16), 16);
  }

  @Override
  public @NotNull GitRevisionNumber getRevisionNumber() {
    return (GitRevisionNumber)super.getRevisionNumber();
  }

  @Override
  public AbstractVcs getVcs() {
    return myVcs;
  }
}
