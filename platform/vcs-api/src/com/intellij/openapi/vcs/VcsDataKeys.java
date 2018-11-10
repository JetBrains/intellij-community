// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs;

import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author yole
 */
public interface VcsDataKeys {
  DataKey<File[]> IO_FILE_ARRAY = DataKey.create("IO_FILE_ARRAY");
  DataKey<File> IO_FILE = DataKey.create("IO_FILE");
  DataKey<VcsKey> VCS = DataKey.create("VCS");
  DataKey<Boolean> VCS_NON_LOCAL_HISTORY_SESSION = DataKey.create("VCS_NON_LOCAL_HISTORY_SESSION");
  DataKey<VcsHistorySession> HISTORY_SESSION = DataKey.create("VCS_HISTORY_SESSION");
  DataKey<VcsFileRevision> VCS_FILE_REVISION = DataKey.create("VCS_FILE_REVISION");
  DataKey<VcsFileRevision[]> VCS_FILE_REVISIONS = DataKey.create("VCS_FILE_REVISIONS");
  DataKey<VirtualFile> VCS_VIRTUAL_FILE = DataKey.create("VCS_VIRTUAL_FILE");
  DataKey<FilePath> FILE_PATH = DataKey.create("FILE_PATH");
  DataKey<FilePath[]> FILE_PATH_ARRAY = DataKey.create("FILE_PATH_ARRAY");
  DataKey<Object> FILE_HISTORY_PANEL = DataKey.create("FILE_HISTORY_PANEL");
  DataKey<ChangeList[]> CHANGE_LISTS = DataKey.create("vcs.ChangeList");
  DataKey<Change> CURRENT_CHANGE = DataKey.create("vcs.CurrentChange");
  DataKey<Change[]> CHANGES = DataKey.create("vcs.Change");
  DataKey<ListSelection<Change>> CHANGES_SELECTION = DataKey.create("vcs.ChangesSelection");
  DataKey<Change[]> CHANGES_WITH_MOVED_CHILDREN = DataKey.create("ChangeListView.ChangesWithDetails");
  DataKey<Change[]> SELECTED_CHANGES_IN_DETAILS = DataKey.create("ChangeListView.SelectedChangesWithMovedSubtrees");
  @NonNls DataKey<List<VirtualFile>> MODIFIED_WITHOUT_EDITING_DATA_KEY = DataKey.create("ChangeListView.ModifiedWithoutEditing");
  @NonNls DataKey<Boolean> HAVE_MODIFIED_WITHOUT_EDITING = DataKey.create("ChangeListView.HaveModifiedWithoutEditing");
  @NonNls DataKey<Boolean> HAVE_LOCALLY_DELETED = DataKey.create("ChangeListView.HaveLocallyDeleted");
  DataKey<Change[]> SELECTED_CHANGES = DataKey.create("ChangeListView.SelectedChange");
  DataKey<Boolean> HAVE_SELECTED_CHANGES = DataKey.create("ChangeListView.HaveSelectedChanges");
  DataKey<Change[]> CHANGE_LEAD_SELECTION = DataKey.create("ChangeListView.ChangeLeadSelection");
  DataKey<FilePath> UPDATE_VIEW_SELECTED_PATH = DataKey.create("AbstractCommonUpdateAction.UpdateViewSelectedPath");
  DataKey<Iterable<Pair<FilePath, FileStatus>>> UPDATE_VIEW_FILES_ITERABLE = DataKey.create("AbstractCommonUpdateAction.UpdatedFilesIterable");
  DataKey<Object> LABEL_BEFORE = DataKey.create("LABEL_BEFORE");
  DataKey<Object> LABEL_AFTER = DataKey.create("LABEL_AFTER");
  DataKey<String> PRESET_COMMIT_MESSAGE = DataKey.create("PRESET_COMMIT_MESSAGE");
  DataKey<CommitMessageI> COMMIT_MESSAGE_CONTROL = DataKey.create("COMMIT_MESSAGE_CONTROL");
  DataKey<Consumer<String>> REMOTE_HISTORY_CHANGED_LISTENER = DataKey.create("REMOTE_HISTORY_CHANGED_LISTENER");
  DataKey<RepositoryLocation> REMOTE_HISTORY_LOCATION = DataKey.create("REMOTE_HISTORY_LOCATION");
  DataKey<VcsRevisionNumber> VCS_REVISION_NUMBER = DataKey.create("VCS_REVISION_NUMBER");
  DataKey<VcsRevisionNumber[]> VCS_REVISION_NUMBERS = DataKey.create("VCS_REVISION_NUMBERS");
  DataKey<VcsHistoryProvider> HISTORY_PROVIDER = DataKey.create("VCS_HISTORY_PROVIDER");
  DataKey<Stream<VirtualFile>> VIRTUAL_FILE_STREAM = DataKey.create("virtualFileStream");
  DataKey<VirtualFile> CURRENT_UNVERSIONED = DataKey.create("ChangeListView.CurrentUnversionedFile");
}
