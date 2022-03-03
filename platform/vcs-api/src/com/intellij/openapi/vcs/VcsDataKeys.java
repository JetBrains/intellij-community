// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs;

import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.vcs.commit.CommitWorkflowHandler;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.util.List;


public interface VcsDataKeys {
  DataKey<VcsKey> VCS = DataKey.create("VCS");

  DataKey<VcsFileRevision> VCS_FILE_REVISION = DataKey.create("VCS_FILE_REVISION");
  DataKey<VcsFileRevision[]> VCS_FILE_REVISIONS = DataKey.create("VCS_FILE_REVISIONS");
  DataKey<VcsRevisionNumber> VCS_REVISION_NUMBER = DataKey.create("VCS_REVISION_NUMBER");
  /**
   * @see com.intellij.openapi.vcs.history.VcsRevisionNumberArrayRule
   */
  DataKey<VcsRevisionNumber[]> VCS_REVISION_NUMBERS = DataKey.create("VCS_REVISION_NUMBERS");
  DataKey<String[]> VCS_COMMIT_SUBJECTS = DataKey.create("VCS_COMMIT_SUBJECTS");

  DataKey<File> IO_FILE = DataKey.create("IO_FILE");
  DataKey<File[]> IO_FILE_ARRAY = DataKey.create("IO_FILE_ARRAY");
  DataKey<VirtualFile> VCS_VIRTUAL_FILE = DataKey.create("VCS_VIRTUAL_FILE");
  DataKey<FilePath> FILE_PATH = DataKey.create("FILE_PATH");

  @ApiStatus.Internal DataKey<Iterable<FilePath>> FILE_PATHS = DataKey.create("VCS_FILE_PATHS");
  /**
   * Lazily iterable {@link com.intellij.openapi.actionSystem.CommonDataKeys#VIRTUAL_FILE_ARRAY}.
   *
   * @see com.intellij.openapi.vcs.VcsVirtualFilesRule
   */
  DataKey<Iterable<VirtualFile>> VIRTUAL_FILES = DataKey.create("VCS_VIRTUAL_FILES");

  DataKey<ChangeList[]> CHANGE_LISTS = DataKey.create("vcs.ChangeList");
  /**
   * Selected changes. In some cases, may return all changes if selection is empty.
   *
   * @see #SELECTED_CHANGES
   */
  DataKey<Change[]> CHANGES = DataKey.create("vcs.Change");
  /**
   * Selected changes only.
   */
  DataKey<Change[]> SELECTED_CHANGES = DataKey.create("ChangeListView.SelectedChange");
  /**
   * Same as {@link #SELECTED_CHANGES}.
   */
  DataKey<Change[]> SELECTED_CHANGES_IN_DETAILS = DataKey.create("ChangeListView.SelectedChangesWithMovedSubtrees");
  /**
   * For multiple selection, return selected changes.
   * For singular selection, return all changes and set selected index.
   * For empty selection, return all changes.
   *
   * @see com.intellij.openapi.vcs.changes.VcsChangesSelectionRule
   * @see com.intellij.openapi.vcs.changes.ui.VcsTreeModelData#getListSelectionOrAll
   */
  DataKey<ListSelection<Change>> CHANGES_SELECTION = DataKey.create("vcs.ChangesSelection");
  /**
   * Explicitly selected changes.
   * <p>
   * When a node in a tree is selected, {@link #SELECTED_CHANGES} will return all changes underneath.
   * This key will return selected nodes only.
   * This difference might be important when {@link AbstractVcs#areDirectoriesVersionedItems()} is {@code true}.
   */
  DataKey<Change[]> CHANGE_LEAD_SELECTION = DataKey.create("ChangeListView.ChangeLeadSelection");
  /**
   * Can be used to ensure that directory flags for SVN are initialized.
   * Is potentially slow and should not be used in {@link com.intellij.openapi.actionSystem.AnAction#update}, use {@link #CHANGES} instead.
   */
  DataKey<Change[]> CHANGES_WITH_MOVED_CHILDREN = DataKey.create("ChangeListView.ChangesWithDetails");
  DataKey<List<VirtualFile>> MODIFIED_WITHOUT_EDITING_DATA_KEY = DataKey.create("ChangeListView.ModifiedWithoutEditing");

  /**
   * Fast check for {@link #CHANGES} non-emptiness.
   */
  DataKey<Boolean> HAVE_SELECTED_CHANGES = DataKey.create("ChangeListView.HaveSelectedChanges");
  /**
   * Fast check for {@link #MODIFIED_WITHOUT_EDITING_DATA_KEY} non-emptiness.
   */
  DataKey<Boolean> HAVE_MODIFIED_WITHOUT_EDITING = DataKey.create("ChangeListView.HaveModifiedWithoutEditing");
  /**
   * Fast check for {@link com.intellij.openapi.vcs.changes.ui.ChangesListView#MISSING_FILES_DATA_KEY} non-emptiness.
   * <p>
   * See 80ee021430c03deef9b4378d124e1b603e207955 for {@link com.intellij.openapi.vcs.changes.ui.ChangesListView#LOCALLY_DELETED_CHANGES}
   * vs {@link com.intellij.openapi.vcs.changes.ui.ChangesListView#MISSING_FILES_DATA_KEY} origins.
   */
  DataKey<Boolean> HAVE_LOCALLY_DELETED = DataKey.create("ChangeListView.HaveLocallyDeleted");

  DataKey<Change> CURRENT_CHANGE = DataKey.create("vcs.CurrentChange");
  DataKey<VirtualFile> CURRENT_UNVERSIONED = DataKey.create("ChangeListView.CurrentUnversionedFile");

  DataKey<String> PRESET_COMMIT_MESSAGE = DataKey.create("PRESET_COMMIT_MESSAGE");
  DataKey<CommitMessageI> COMMIT_MESSAGE_CONTROL = DataKey.create("COMMIT_MESSAGE_CONTROL");
  DataKey<CommitWorkflowHandler> COMMIT_WORKFLOW_HANDLER = DataKey.create("Vcs.CommitWorkflowHandler");

  DataKey<VcsHistorySession> HISTORY_SESSION = DataKey.create("VCS_HISTORY_SESSION");
  /**
   * true - if content has no matching local root (ex: history for remote repository without checking it out).
   */
  DataKey<Boolean> VCS_NON_LOCAL_HISTORY_SESSION = DataKey.create("VCS_NON_LOCAL_HISTORY_SESSION");
  DataKey<VcsHistoryProvider> HISTORY_PROVIDER = DataKey.create("VCS_HISTORY_PROVIDER");

  DataKey<Consumer<String>> REMOTE_HISTORY_CHANGED_LISTENER = DataKey.create("REMOTE_HISTORY_CHANGED_LISTENER");
  DataKey<RepositoryLocation> REMOTE_HISTORY_LOCATION = DataKey.create("REMOTE_HISTORY_LOCATION");
}
