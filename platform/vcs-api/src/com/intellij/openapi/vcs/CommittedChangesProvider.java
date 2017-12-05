// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.committed.DecoratorManager;
import com.intellij.openapi.vcs.changes.committed.VcsCommittedListsZipper;
import com.intellij.openapi.vcs.changes.committed.VcsCommittedViewAuxiliary;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.ChangesBrowserSettingsEditor;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.AsynchConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public interface CommittedChangesProvider<T extends CommittedChangeList, U extends ChangeBrowserSettings> extends VcsProviderMarker {
  @NotNull
  default U createDefaultSettings() {
    //noinspection unchecked
    return (U)new ChangeBrowserSettings();
  }

  ChangesBrowserSettingsEditor<U> createFilterUI(final boolean showDateFilter);

  @Nullable
  RepositoryLocation getLocationFor(FilePath root);

  @SuppressWarnings("unused")
  @Deprecated // Required for compatibility with external plugins.
  @Nullable
  default RepositoryLocation getLocationFor(final FilePath root, final String repositoryPath) {
    return getLocationFor(root);
  }

  @Nullable
  VcsCommittedListsZipper getZipper();

  List<T> getCommittedChanges(U settings, RepositoryLocation location, final int maxCount) throws VcsException;

  void loadCommittedChanges(U settings, RepositoryLocation location, final int maxCount, final AsynchConsumer<CommittedChangeList> consumer) throws VcsException;

  ChangeListColumn[] getColumns();

  @Nullable
  VcsCommittedViewAuxiliary createActions(final DecoratorManager manager, final RepositoryLocation location);

  /**
   * since may be different for different VCSs
   */
  int getUnlimitedCountValue();

  /**
   * @return required list and path of the target file in that revision (changes when move/rename)
   */
  @Nullable
  Pair<T, FilePath> getOneList(final VirtualFile file, final VcsRevisionNumber number) throws VcsException;

  RepositoryLocation getForNonLocal(final VirtualFile file);

  /**
   * Return true if this committed changes provider can be used to show the incoming changes.
   * If false is returned, the "Incoming" tab won't be shown in the Changes toolwindow.
   */
  boolean supportsIncomingChanges();
}
