// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.changes

import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ChangesDataKeys {
  @JvmField
  val CHANGES: DataKey<Array<Change>> = DataKey.create("vcs.Change")

  @JvmField
  val SELECTED_CHANGES: DataKey<Array<Change>> = DataKey.create("ChangeListView.SelectedChange")

  @JvmField
  val SELECTED_CHANGES_IN_DETAILS: DataKey<Array<Change>> = DataKey.create("ChangeListView.SelectedChangesWithMovedSubtrees")

  @JvmField
  val CHANGES_SELECTION: DataKey<ListSelection<Change>> = DataKey.create("vcs.ChangesSelection")

  @JvmField
  val CHANGE_LEAD_SELECTION: DataKey<Array<Change>> = DataKey.create("ChangeListView.ChangeLeadSelection")

  @JvmField
  val FILE_PATHS: DataKey<Iterable<FilePath>> = DataKey.create("VCS_FILE_PATHS")

  @JvmField
  val VIRTUAL_FILES: DataKey<Iterable<VirtualFile>> = DataKey.create("VCS_VIRTUAL_FILES")
}
