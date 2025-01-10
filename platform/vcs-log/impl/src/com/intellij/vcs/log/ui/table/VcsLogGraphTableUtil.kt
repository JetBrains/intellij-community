// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.data.LoadingDetails

internal fun GraphTableModel.getCommitId(row: Int): CommitId? {
  return getCommitMetadata(row)?.getKnownCommitId()
}

internal fun VcsCommitMetadata.getKnownCommitId(): CommitId? {
  if (this is LoadingDetails) return null
  return CommitId(id, root)
}