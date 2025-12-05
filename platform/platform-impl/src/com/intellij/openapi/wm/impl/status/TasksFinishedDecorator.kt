// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.wm.impl.status.ProcessPopup.hideSeparator
import com.intellij.openapi.wm.impl.status.ProcessPopup.isProgressIndicator
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JPanel
import javax.swing.SwingConstants


/**
 * Adds an "all tasks finished" label when there are no background tasks running
 */
internal class TasksFinishedDecorator(private val panel: JPanel) {
  private val finishedTasksLabel = createTasksFinishedLabel()


  fun indicatorAdded() {
    panel.remove(finishedTasksLabel)
  }

  fun indicatorRemoved() {
    if (panel.components.any { isProgressIndicator(it) }) {
      return
    }
    panel.add(finishedTasksLabel, 0, 0)
  }


  private fun createTasksFinishedLabel(): JBLabel = JBLabel().apply {
    text = IdeBundle.message("all.background.tasks.completed")
    icon = AllIcons.Status.Success
    horizontalAlignment = SwingConstants.LEFT

    setBorder(JBUI.Borders.empty(8, 12))
    foreground = UIUtil.getLabelInfoForeground()
    hideSeparator(this)
    if (ExperimentalUI.isNewUI()) {
      setOpaque(false)
    }
  }
}
