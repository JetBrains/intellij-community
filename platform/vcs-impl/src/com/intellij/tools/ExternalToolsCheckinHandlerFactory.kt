// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tools

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * @author lene
 */
class ExternalToolsCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    val config = ToolsProjectConfig.getInstance(panel.project)
    return object : CheckinHandler() {
      override fun getAfterCheckinConfigurationPanel(parentDisposable: Disposable): RefreshableOnComponent? {
        val label = JLabel(ToolsBundle.message("tools.after.commit.description"))

        val toolComboBox = ToolSelectComboBox(panel.project)

        val layout = BorderLayout()
        layout.vgap = 3
        val panel = JPanel(layout)
        panel.add(label, BorderLayout.NORTH)
        panel.add(toolComboBox, BorderLayout.CENTER)
        toolComboBox.border = BorderFactory.createEmptyBorder(0, 0, 3, 0)

        if (toolComboBox.valuableItemCount == 0) {
          return null
        }

        return object : RefreshableOnComponent {
          override fun getComponent(): JComponent {
            return panel
          }

          override fun saveState() {
            val tool = toolComboBox.selectedTool
            config.setAfterCommitToolId(tool?.actionId)
          }

          override fun restoreState() {
            val id = config.afterCommitToolsId
            toolComboBox.selectTool(id)
          }
        }
      }

      override fun checkinSuccessful() {
        val id = config.afterCommitToolsId ?: return
        DataManager.getInstance()
          .dataContextFromFocusAsync
          .onSuccess { context -> UIUtil.invokeAndWaitIfNeeded(Runnable { ToolAction.runTool(id, context) }) }
      }
    }
  }
}