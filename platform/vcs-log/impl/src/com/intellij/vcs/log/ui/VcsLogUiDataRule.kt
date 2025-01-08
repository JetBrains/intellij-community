// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui

import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.UiDataRule
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.history.FileHistoryUi

internal class VcsLogUiDataRule : UiDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    val logUi = snapshot[VcsLogDataKeys.VCS_LOG_UI]
    sink[VcsLogInternalDataKeys.FILE_HISTORY_UI] = logUi as? FileHistoryUi
    sink[VcsLogInternalDataKeys.MAIN_UI] = logUi as? MainVcsLogUi
    sink[VcsLogInternalDataKeys.LOG_UI_EX] = logUi as? VcsLogUiEx
  }
}