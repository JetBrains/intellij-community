// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions

import com.intellij.icons.AllIcons
import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.actions.OpenWhitelistFileAction.Companion.openFileInEditor
import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogTestWhitelistPersistence
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.LayeredIcon

class OpenLocalWhitelistFileAction(private val myRecorderId: String = "FUS")
  : AnAction(StatisticsBundle.message("stats.open.0.local.whitelist.file", myRecorderId), null, ICON) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val file = EventLogTestWhitelistPersistence(myRecorderId).whitelistFile
    openFileInEditor(file, project)
  }

  companion object {
    private val ICON = LayeredIcon(AllIcons.FileTypes.Any_type, AllIcons.Actions.Scratch)
  }

}