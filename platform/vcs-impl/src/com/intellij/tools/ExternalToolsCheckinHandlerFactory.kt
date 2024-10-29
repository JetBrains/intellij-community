// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tools

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * @author lene
 */
@ApiStatus.Internal
class ExternalToolsCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    val config = ToolsProjectConfig.getInstance(panel.project)
    return object : CheckinHandler() {
      override fun getAfterCheckinConfigurationPanel(parentDisposable: Disposable): RefreshableOnComponent? {
        val toolComboBox = ToolSelectComboBox(panel.project)
        if (toolComboBox.valuableItemCount == 0) {
          return null
        }

        return object : RefreshableOnComponent {
          override fun getComponent(): JComponent {
            return panel {
              row(ToolsBundle.message("tools.after.commit.description")) {
                cell(toolComboBox)
                  .align(AlignX.FILL)
              }
            }
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