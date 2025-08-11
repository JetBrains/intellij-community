// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs;

import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.vcs.changes.ChangesDataKeys;
import com.intellij.util.Consumer;
import com.intellij.vcs.commit.CommitWorkflowHandler;
import com.intellij.vcs.commit.CommitWorkflowUi;
import org.jetbrains.annotations.ApiStatus;


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

  DataKey<VirtualFile> VCS_VIRTUAL_FILE = DataKey.create("VCS_VIRTUAL_FILE");
  DataKey<FilePath> FILE_PATH = DataKey.create("FILE_PATH");

  @ApiStatus.Internal DataKey<Iterable<FilePath>> FILE_PATHS = ChangesDataKeys.FILE_PATHS;
  /**
   * Lazily iterable {@link com.intellij.openapi.actionSystem.CommonDataKeys#VIRTUAL_FILE_ARRAY}.
   *
   * @see com.intellij.openapi.vcs.VcsVirtualFilesRule
   */
  DataKey<Iterable<VirtualFile>> VIRTUAL_FILES = ChangesDataKeys.VIRTUAL_FILES;

  DataKey<ChangeList[]> CHANGE_LISTS = DataKey.create("vcs.ChangeList");
  /**
   * Selected changes. In some cases, may return all changes if selection is empty.
   *
   * @see #SELECTED_CHANGES
   */
  DataKey<Change[]> CHANGES = ChangesDataKeys.CHANGES;
  /**
   * Selected changes only.
   */
  DataKey<Change[]> SELECTED_CHANGES = ChangesDataKeys.SELECTED_CHANGES;
  /**
   * Same as {@link #SELECTED_CHANGES}.
   */
  DataKey<Change[]> SELECTED_CHANGES_IN_DETAILS = ChangesDataKeys.SELECTED_CHANGES_IN_DETAILS;
  /**
   * For multiple selection, return selected changes.
   * For singular selection, return all changes and set selected index.
   * For empty selection, return all changes.
   *
   * @see com.intellij.openapi.vcs.changes.VcsChangesSelectionRule
   * @see com.intellij.openapi.vcs.changes.ui.VcsTreeModelData#getListSelectionOrAll
   */
  DataKey<ListSelection<Change>> CHANGES_SELECTION = ChangesDataKeys.CHANGES_SELECTION;
  /**
   * Explicitly selected changes.
   * <p>
   * When a node in a tree is selected, {@link #SELECTED_CHANGES} will return all changes underneath.
   * This key will return selected nodes only.
   * This difference might be important when {@link AbstractVcs#areDirectoriesVersionedItems()} is {@code true}.
   */
  DataKey<Change[]> CHANGE_LEAD_SELECTION = ChangesDataKeys.CHANGE_LEAD_SELECTION;

  DataKey<Change> CURRENT_CHANGE = DataKey.create("vcs.CurrentChange");
  DataKey<VirtualFile> CURRENT_UNVERSIONED = DataKey.create("ChangeListView.CurrentUnversionedFile");

  DataKey<String> PRESET_COMMIT_MESSAGE = DataKey.create("PRESET_COMMIT_MESSAGE");
  DataKey<CommitMessageI> COMMIT_MESSAGE_CONTROL = DataKey.create("COMMIT_MESSAGE_CONTROL");
  DataKey<Document> COMMIT_MESSAGE_DOCUMENT = DataKey.create("COMMIT_MESSAGE_DOCUMENT");
  DataKey<CommitWorkflowHandler> COMMIT_WORKFLOW_HANDLER = DataKey.create("Vcs.CommitWorkflowHandler");
  DataKey<CommitWorkflowUi> COMMIT_WORKFLOW_UI = DataKey.create("Vcs.CommitWorkflowUI");

  DataKey<VcsHistorySession> HISTORY_SESSION = DataKey.create("VCS_HISTORY_SESSION");
  /**
   * true - if content has no matching local root (ex: history for remote repository without checking it out).
   */
  DataKey<Boolean> VCS_NON_LOCAL_HISTORY_SESSION = DataKey.create("VCS_NON_LOCAL_HISTORY_SESSION");
  DataKey<VcsHistoryProvider> HISTORY_PROVIDER = DataKey.create("VCS_HISTORY_PROVIDER");

  DataKey<Consumer<String>> REMOTE_HISTORY_CHANGED_LISTENER = DataKey.create("REMOTE_HISTORY_CHANGED_LISTENER");
  DataKey<RepositoryLocation> REMOTE_HISTORY_LOCATION = DataKey.create("REMOTE_HISTORY_LOCATION");
}
