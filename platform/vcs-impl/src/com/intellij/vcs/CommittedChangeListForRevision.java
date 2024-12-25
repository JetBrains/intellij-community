// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.LongRevisionNumber;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;
import com.intellij.openapi.vcs.versionBrowser.VcsRevisionNumberAware;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Date;

public class CommittedChangeListForRevision extends CommittedChangeListImpl implements VcsRevisionNumberAware {

  private final @NotNull VcsRevisionNumber myRevisionNumber;

  public CommittedChangeListForRevision(@NotNull String subject,
                                        @NotNull String comment,
                                        @NotNull String committerName,
                                        @NotNull Date commitDate,
                                        @NotNull Collection<Change> changes,
                                        @NotNull VcsRevisionNumber revisionNumber) {
    super(subject, comment, committerName, getLong(revisionNumber), commitDate, changes);
    myRevisionNumber = revisionNumber;
  }

  @Override
  public @NotNull VcsRevisionNumber getRevisionNumber() {
    return myRevisionNumber;
  }

  private static long getLong(@NotNull VcsRevisionNumber number) {
    if (number instanceof LongRevisionNumber) return ((LongRevisionNumber)number).getLongRevisionNumber();
    return 0;
  }
}
