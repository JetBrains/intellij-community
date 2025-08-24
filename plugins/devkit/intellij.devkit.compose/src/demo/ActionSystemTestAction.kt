package com.intellij.devkit.compose.demo

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.Messages.showMessageDialog
import org.jetbrains.idea.devkit.util.PsiUtil

internal class ActionSystemTestAction : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null && PsiUtil.isPluginProject(e.project!!)
  }

  override fun actionPerformed(anActionEvent: AnActionEvent) {
    thisLogger().debug(anActionEvent.getData(COMPONENT_DATA_KEY))

    showMessageDialog(anActionEvent.getData(COMPONENT_DATA_KEY), "Action System Test", null)
  }

  companion object {
    val COMPONENT_DATA_KEY = DataKey.create<String>("COMPONENT")
  }
}
