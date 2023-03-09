// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import com.intellij.vcs.log.TimedVcsCommit;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class VcsLogSorter {

  public static @NotNull <Commit extends TimedVcsCommit> List<Commit> sortByDateTopoOrder(@NotNull Collection<Commit> commits) {
    return new VcsLogJoiner.NewCommitIntegrator<>(new ArrayList<>(), commits).getResultList();
  }
}
