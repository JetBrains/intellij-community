// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataKey.Companion.create
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.VcsLogDiffHandler
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.history.FileHistoryModel
import com.intellij.vcs.log.history.FileHistoryUi
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import org.jetbrains.annotations.ApiStatus

object VcsLogInternalDataKeys {
  @JvmField
  val LOG_MANAGER: DataKey<VcsLogManager> = create("Vcs.Log.Manager")

  @JvmField
  val LOG_UI_PROPERTIES: DataKey<VcsLogUiProperties> = create("Vcs.Log.Ui.Properties")

  @JvmField
  val MAIN_UI: DataKey<MainVcsLogUi> = create("Vcs.Log.Main.Ui")

  @JvmField
  val FILE_HISTORY_UI: DataKey<FileHistoryUi> = create("Vcs.FileHistory.Ui")

  @JvmField
  val LOG_UI_EX: DataKey<VcsLogUiEx> = create("Vcs.Log.UiEx")

  @JvmField
  val LOG_DIFF_HANDLER: DataKey<VcsLogDiffHandler> = create("Vcs.Log.Diff.Handler")

  @JvmField
  val LOG_DATA: DataKey<VcsLogData> = create("Vcs.Log.Data")

  @JvmField
  val VCS_LOG_VISIBLE_ROOTS: DataKey<Set<VirtualFile>> = create("Vcs.Log.Visible.Roots")

  @JvmField
  val FILE_HISTORY_MODEL: DataKey<FileHistoryModel> = create("Vcs.FileHistory.Model")

  @JvmField
  val VCS_LOG_GRAPH_TABLE: DataKey<VcsLogGraphTable> = create("Vcs.Log.Graph.Table")

  /**
   * Storage indices of commits acted upon outside the VCS Log table (e.g., the Reword dialog opened from a
   * post-commit notification). Use as a fallback to [com.intellij.vcs.log.VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION] when the
   * surface has no real log selection but still wants to expose the commits being edited.
   */
  @ApiStatus.Internal
  val VCS_LOG_COMMIT_STORAGE_INDICES: DataKey<List<VcsLogCommitStorageIndex>> = create("Vcs.Log.Commit.Storage.Indices")
}
