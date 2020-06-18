// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.StatisticsDevKitUtil
import com.intellij.internal.statistic.actions.OpenEventsSchemeFileAction.Companion.openFileInEditor
import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogTestWhitelistPersistence
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class OpenEventsTestSchemeFileAction(private val myRecorderId: String = StatisticsDevKitUtil.DEFAULT_RECORDER)
  : DumbAwareAction(StatisticsBundle.message("stats.open.0.test.scheme.file", myRecorderId),
                    ActionsBundle.message("group.OpenEventsTestSchemeFileAction.description"),
                    AllIcons.FileTypes.Any_type) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val file = EventLogTestWhitelistPersistence(myRecorderId).whitelistFile
    openFileInEditor(file, project)
  }
}