// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface VcsCommittedListsZipper {
  @NotNull
  Pair<List<RepositoryLocationGroup>, List<RepositoryLocation>> groupLocations(@NotNull List<? extends RepositoryLocation> in);

  @NotNull
  CommittedChangeList zip(@NotNull RepositoryLocationGroup group, @NotNull List<? extends CommittedChangeList> lists);

  long getNumber(@NotNull CommittedChangeList list);
}
