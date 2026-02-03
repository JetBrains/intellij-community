// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface CommittedChangesListener {
  default void changesLoaded(@NotNull RepositoryLocation location, @NotNull List<CommittedChangeList> changes) {
  }

  default void incomingChangesUpdated(@Nullable List<CommittedChangeList> receivedChanges) {
  }

  default void changesCleared() {
  }

  default void refreshErrorStatusChanged(@Nullable VcsException lastError) {
  }
}
