// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions.history

import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogCommitDataCache
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.data.VcsLogData
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class FileHistoryMetadataAction : FileHistoryOneCommitAction<VcsCommitMetadata>() {
  override fun getCache(logData: VcsLogData): VcsLogCommitDataCache<VcsCommitMetadata> = logData.miniDetailsGetter

  override fun loadData(logData: VcsLogData,
                        selection: VcsLogCommitSelection,
                        onSuccess: (List<VcsCommitMetadata>) -> Unit,
                        onError: (Throwable) -> Unit) {
    logData.miniDetailsGetter.loadCommitsData(selection.ids, { details -> onSuccess(details) }, { t -> onError(t) }, null)
  }
}
