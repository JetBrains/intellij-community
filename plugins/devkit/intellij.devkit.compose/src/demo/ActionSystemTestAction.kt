package com.intellij.devkit.compose.demo

import com.intellij.devkit.compose.DevkitComposeBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.Messages.showMessageDialog
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.util.PsiUtil

internal val JEWEL_COMPONENT_DATA_KEY = DataKey.create<@Nls String>("COMPONENT")

internal class ActionSystemTestAction : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null && PsiUtil.isPluginProject(e.project!!)
  }

  @Suppress("HardCodedStringLiteral")
  override fun actionPerformed(anActionEvent: AnActionEvent) {
    thisLogger().debug(anActionEvent.getData(JEWEL_COMPONENT_DATA_KEY))

    showMessageDialog(anActionEvent.getData(JEWEL_COMPONENT_DATA_KEY),
                      DevkitComposeBundle.message("jewel.dialog.title.action.system.test"), null)
  }
}
