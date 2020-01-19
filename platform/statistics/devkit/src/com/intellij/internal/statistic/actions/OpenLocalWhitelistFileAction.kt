// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions

import com.intellij.icons.AllIcons
import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.actions.OpenWhitelistFileAction.Companion.openFileInEditor
import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogTestWhitelistPersistence
import com.intellij.internal.statistic.eventLog.whitelist.WhitelistTestGroupStorage
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.JBColor
import com.intellij.ui.LayeredIcon
import com.intellij.ui.TextIcon
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import java.awt.Font

class OpenLocalWhitelistFileAction(private val myRecorderId: String = "FUS")
  : AnAction(StatisticsBundle.message("stats.open.0.local.whitelist.file", myRecorderId)) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val file = EventLogTestWhitelistPersistence(myRecorderId).whitelistFile
    openFileInEditor(file, project)
  }

  override fun update(e: AnActionEvent) {
    val localWhitelistSize = WhitelistTestGroupStorage.getInstance(myRecorderId).size()
    val text = if (localWhitelistSize < 100) localWhitelistSize.toString() else "99+"
    val sizeCountIcon = TextIcon(text, JBColor.DARK_GRAY, UIUtil.getLabelBackground(), 1)
    sizeCountIcon.setFont(Font(UIUtil.getLabelFont().name, Font.BOLD, JBUIScale.scale(9)))
    sizeCountIcon.setInsets(1, 1, 0, 0)
    ICON.setIcon(sizeCountIcon, 2, JBUIScale.scale(10), JBUIScale.scale(10))
    e.presentation.icon = ICON
  }

  companion object {
    private val ICON = LayeredIcon(3)

    init {
      ICON.setIcon(AllIcons.FileTypes.Any_type, 0)
      ICON.setIcon(AllIcons.Actions.Scratch, 1)
    }
  }

}