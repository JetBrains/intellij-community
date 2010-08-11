/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public interface CommittedChangesProvider<T extends CommittedChangeList, U extends ChangeBrowserSettings> extends VcsProviderMarker {
  U createDefaultSettings();
  ChangesBrowserSettingsEditor<U> createFilterUI(final boolean showDateFilter);

  @Nullable
  RepositoryLocation getLocationFor(FilePath root);
  @Nullable
  RepositoryLocation getLocationFor(final FilePath root, final String repositoryPath);

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
}
