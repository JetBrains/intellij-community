package com.intellij.openapi.vcs;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface VcsOutgoingChangesProvider <T extends CommittedChangeList> extends VcsProviderMarker {
  Pair<VcsRevisionNumber, List<T>> getOutgoingChanges(final VirtualFile vcsRoot, final boolean findRemote) throws VcsException;
  @Nullable
  VcsRevisionNumber getMergeBaseNumber(final VirtualFile anyFileUnderRoot) throws VcsException;
}
