// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class CommittedChangesAdapter implements CommittedChangesListener {
  @Override
  public void changesLoaded(RepositoryLocation location, List<CommittedChangeList> changes) {
  }

  @Override
  public void incomingChangesUpdated(@Nullable final List<CommittedChangeList> receivedChanges) {
  }

  @Override
  public void changesCleared() {
  }

  @Override
  public void presentationChanged() {
  }

  @Override
  public void refreshErrorStatusChanged(@Nullable VcsException lastError) {
  }
}