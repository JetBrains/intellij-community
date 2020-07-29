// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data;

import com.intellij.vcs.log.TimedVcsCommit;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class VcsLogSorter {

  @NotNull
  public static <Commit extends TimedVcsCommit> List<Commit> sortByDateTopoOrder(@NotNull Collection<Commit> commits) {
    return new VcsLogJoiner.NewCommitIntegrator<>(new ArrayList<>(), commits).getResultList();
  }
}
