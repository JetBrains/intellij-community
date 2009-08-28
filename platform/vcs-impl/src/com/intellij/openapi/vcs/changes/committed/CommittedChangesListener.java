package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public interface CommittedChangesListener {
  void changesLoaded(RepositoryLocation location, List<CommittedChangeList> changes);
  void incomingChangesUpdated(@Nullable final List<CommittedChangeList> receivedChanges);
  void refreshErrorStatusChanged(@Nullable VcsException lastError);
}
