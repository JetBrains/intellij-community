package com.intellij.smartUpdate

import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.actions.SettingsEntryPointAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.restartOrNotify
import org.jetbrains.annotations.Nls
import org.jetbrains.ide.ToolboxSettingsActionRegistry
import javax.swing.JComponent
import javax.swing.JLabel

const val IDE_UPDATE = "ide.update"

class IdeUpdateStep: SmartUpdateStep {
  override val id = IDE_UPDATE
  override val stepName: String = SmartUpdateBundle.message("checkbox.update.ide")

  override fun performUpdateStep(project: Project, e: AnActionEvent?, onSuccess: () -> Unit) {
    val updateAction = getUpdateAction()
    if (updateAction != null && e != null) {
      updateAction.actionPerformed(e)
      project.service<SmartUpdate>().restartRequested = true
    }
    else onSuccess()
  }

  override fun getDetailsComponent(project: Project): JComponent {
    return JLabel(getDescription())
  }

  @Nls
  private fun getDescription(): String {
    val updateAction = getUpdateAction()
    if (updateAction == null) return SmartUpdateBundle.message("no.updates.available")
    return updateAction.templatePresentation.text
  }
}

private fun getUpdateAction() = service<ToolboxSettingsActionRegistry>().getActions().find { it.isIdeUpdate }

fun restartIde(project: Project, updateAction: SettingsEntryPointAction.UpdateAction) {
    restartOrNotify(project, true) {
      beforeRestart()
      val event = AnActionEvent.createFromDataContext("", null, SimpleDataContext.getProjectContext(project))
      updateAction.actionPerformed(event)
    }
}

fun beforeRestart() {
  RecentProjectsManagerBase.getInstanceEx().forceReopenProjects()
  PropertiesComponent.getInstance().setValue(IDE_RESTARTED_KEY, true)
}
