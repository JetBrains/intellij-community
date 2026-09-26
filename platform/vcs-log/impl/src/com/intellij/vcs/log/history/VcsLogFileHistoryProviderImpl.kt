// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.vcs.log.VcsLogFileHistoryProvider
import com.intellij.vcs.log.impl.VcsProjectLog

internal class VcsLogFileHistoryProviderImpl(private val project: Project) : VcsLogFileHistoryProvider {
  override fun canShowFileHistory(paths: Collection<FilePath>, revisionNumber: VcsRevisionNumber?): Boolean {
    return VcsProjectLog.getInstance(project).canShowFileHistory(paths, revisionNumber)
  }

  override fun showFileHistory(paths: Collection<FilePath>, revisionNumber: VcsRevisionNumber?) {
    VcsProjectLog.getInstance(project).openFileHistory(paths, revisionNumber)
  }

  override fun showFileHistory(paths: Collection<FilePath>, revisionNumber: VcsRevisionNumber?, revisionToSelect: VcsRevisionNumber) {
    VcsProjectLog.getInstance(project).openFileHistory(paths, revisionNumber, revisionToSelect)
  }
}