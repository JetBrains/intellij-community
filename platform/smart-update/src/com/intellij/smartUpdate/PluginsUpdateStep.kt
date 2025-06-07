package com.intellij.smartUpdate

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ex.ApplicationManagerEx.getApplicationEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.openapi.updateSettings.impl.getPendingUpdates
import com.intellij.openapi.updateSettings.impl.installUpdates
import com.intellij.ui.SpinningProgressIcon
import org.jetbrains.annotations.Nls
import java.util.function.Consumer
import javax.swing.JComponent

private const val PLUGINS_UPDATE = "plugins.update"

internal class PluginsUpdateStep: SmartUpdateStep {
  override val id = PLUGINS_UPDATE
  override val stepName = SmartUpdateBundle.message("update.plugins")

  override fun performUpdateStep(project: Project, e: AnActionEvent?, onSuccess: () -> Unit) {
    val doUpdate = {
      val updates = getPendingUpdates()
      if (updates.isNullOrEmpty()) {
        onSuccess()
      }
      else {
        val component = e?.dataContext?.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? JComponent
        installUpdates(updates, component, Consumer { restartRequired ->
          if (restartRequired) {
            restartIde(project, SmartUpdateBundle.message("dialog.title.plugin.updates.ready.to.install")) { getApplicationEx().restart(true) }
          }
          else {
            onSuccess()
          }
        })
      }
    }
    if (e == null) {
      UpdateChecker.getUpdates().doWhenProcessed(Runnable(doUpdate))
    }
    else {
      doUpdate()
    }
  }

  override fun getDetailsComponent(project: Project): JComponent {
    val label = hintLabel(SmartUpdateBundle.message("checking.for.updates")).apply {
      icon = SpinningProgressIcon()
    }
    UpdateChecker.getUpdates().doWhenProcessed(Runnable {
      label.text = getDescription()
      label.icon = null
    })
    return label
  }

  @Nls
  private fun getDescription(): String {
    val updates = getPendingUpdates()
    if (updates.isNullOrEmpty()) return SmartUpdateBundle.message("no.updates.available")
    return if (updates.size == 1)
      SmartUpdateBundle.message("update.plugin", updates.first().pluginName) else
      SmartUpdateBundle.message("update.several.plugins", updates.size)
  }
}
