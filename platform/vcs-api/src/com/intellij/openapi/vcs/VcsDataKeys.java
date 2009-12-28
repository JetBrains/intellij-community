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

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeRequestChain;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.List;

/**
 * @author yole
 */
public interface VcsDataKeys {
  DataKey<File[]> IO_FILE_ARRAY = DataKey.create("IO_FILE_ARRAY");
  DataKey<File> IO_FILE = DataKey.create("IO_FILE");
  DataKey<VcsFileRevision> VCS_FILE_REVISION = DataKey.create("VCS_FILE_REVISION");
  DataKey<VcsFileRevision[]> VCS_FILE_REVISIONS = DataKey.create("VCS_FILE_REVISIONS");
  DataKey<VirtualFile> VCS_VIRTUAL_FILE = DataKey.create("VCS_VIRTUAL_FILE");
  DataKey<FilePath> FILE_PATH = DataKey.create("FILE_PATH");
  DataKey<FilePath[]> FILE_PATH_ARRAY = DataKey.create("FILE_PATH_ARRAY");
  DataKey<Object> FILE_HISTORY_PANEL = DataKey.create("FILE_HISTORY_PANEL");
  DataKey<ChangeList[]> CHANGE_LISTS = DataKey.create("vcs.ChangeList");
  DataKey<Change[]> CHANGES = DataKey.create("vcs.Change");
  DataKey<Change[]> CHANGES_WITH_MOVED_CHILDREN = DataKey.create("ChangeListView.ChangesWithDetails");
  DataKey<Change[]> SELECTED_CHANGES_IN_DETAILS = DataKey.create("ChangeListView.SelectedChangesWithMovedSubtrees");
  @NonNls DataKey<List<Change>> CHANGES_IN_LIST_KEY = DataKey.create("ChangeListView.ChangesInList");
  @NonNls DataKey<List<VirtualFile>> MODIFIED_WITHOUT_EDITING_DATA_KEY = DataKey.create("ChangeListView.ModifiedWithoutEditing");
  DataKey<Change[]> SELECTED_CHANGES = DataKey.create("ChangeListView.SelectedChange");
  DataKey<Change[]> CHANGE_LEAD_SELECTION = DataKey.create("ChangeListView.ChangeLeadSelection");
  DataKey<ChangeRequestChain> DIFF_REQUEST_CHAIN = DataKey.create("diffRequestChain");
  DataKey<String> UPDATE_VIEW_SELECTED_PATH = DataKey.create("AbstractCommonUpdateAction.UpdateViewSelectedPath");
  DataKey<Iterable<VirtualFilePointer>> UPDATE_VIEW_FILES_ITERABLE = DataKey.create("AbstractCommonUpdateAction.UpdatedFilesIterable");
  DataKey<Object> LABEL_BEFORE = DataKey.create("LABEL_BEFORE");
  DataKey<Object> LABEL_AFTER = DataKey.create("LABEL_AFTER");
}
