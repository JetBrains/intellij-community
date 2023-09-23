package com.intellij.smartUpdate

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.update.CommonUpdateProjectAction

const val VCS_UPDATE = "vcs.update"

class VcsUpdateStep : SmartUpdateStep {
  override val id: String = VCS_UPDATE
  override val stepName = SmartUpdateBundle.message("checkbox.update.project")

  override fun performUpdateStep(project: Project, e: AnActionEvent?, onSuccess: () -> Unit) {
    val start = System.currentTimeMillis()
    val action = object : CommonUpdateProjectAction() {
      override fun onSuccess() {
        SmartUpdateUsagesCollector.logUpdate(System.currentTimeMillis() - start)
        onSuccess.invoke()
      }
    }
    action.templatePresentation.text = SmartUpdateBundle.message("action.update.project.text")
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .build()
    val actionEvent = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, dataContext)
    action.actionPerformed(actionEvent)
  }
}
