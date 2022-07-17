// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public interface CommittedChangesProvider<T extends CommittedChangeList, U extends ChangeBrowserSettings> {
  @NotNull
  default U createDefaultSettings() {
    //noinspection unchecked
    return (U)new ChangeBrowserSettings();
  }

  @NotNull
  ChangesBrowserSettingsEditor<U> createFilterUI(boolean showDateFilter);

  @Nullable
  RepositoryLocation getLocationFor(@NotNull FilePath root);

  /**
   * @deprecated use {@link #getLocationFor(FilePath)}
   */
  @SuppressWarnings("unused")
  @Deprecated(forRemoval = true)
  @Nullable
  default RepositoryLocation getLocationFor(FilePath root, String repositoryPath) {
    return getLocationFor(root);
  }

  @Nullable
  default VcsCommittedListsZipper getZipper() {
    return null;
  }

  @NotNull
  List<T> getCommittedChanges(U settings, RepositoryLocation location, int maxCount) throws VcsException;

  void loadCommittedChanges(U settings,
                            @NotNull RepositoryLocation location,
                            int maxCount,
                            @NotNull AsynchConsumer<? super CommittedChangeList> consumer) throws VcsException;

  ChangeListColumn @NotNull [] getColumns();

  @Nullable
  default VcsCommittedViewAuxiliary createActions(@NotNull DecoratorManager manager, @Nullable RepositoryLocation location) {
    return null;
  }

  /**
   * since may be different for different VCSs
   */
  int getUnlimitedCountValue();

  /**
   * @return required list and path of the target file in that revision (changes when move/rename)
   */
  @Nullable
  Pair<T, FilePath> getOneList(VirtualFile file, VcsRevisionNumber number) throws VcsException;

  @Nullable
  default RepositoryLocation getForNonLocal(@NotNull VirtualFile file) {
    return null;
  }

  /**
   * Return true if this committed changes provider can be used to show the incoming changes.
   * If false is returned, the "Incoming" tab won't be shown in the Changes toolwindow.
   */
  default boolean supportsIncomingChanges() {
    return true;
  }
}
