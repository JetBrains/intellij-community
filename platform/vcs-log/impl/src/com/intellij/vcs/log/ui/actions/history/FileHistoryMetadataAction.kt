// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions.history

import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.data.DataGetter
import com.intellij.vcs.log.data.VcsLogData

abstract class FileHistoryMetadataAction : FileHistoryOneCommitAction<VcsCommitMetadata>() {
  override fun getDetailsGetter(logData: VcsLogData): DataGetter<VcsCommitMetadata> = logData.miniDetailsGetter
}
