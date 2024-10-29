package com.intellij.smartUpdate

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.update.ActionInfo
import com.intellij.openapi.vcs.update.CommonUpdateProjectAction
import com.intellij.openapi.vcs.update.ScopeInfo
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ComponentPredicate
import javax.swing.JComponent

const val VCS_UPDATE = "vcs.update"

class VcsUpdateStep : SmartUpdateStep {
  private lateinit var showOptionsListener: (Boolean) -> Unit
  override val id: String = VCS_UPDATE
  override val stepName = SmartUpdateBundle.message("checkbox.update.project")

  override fun performUpdateStep(project: Project, e: AnActionEvent?, onSuccess: () -> Unit) {
    val start = System.currentTimeMillis()
    val action = object : CommonUpdateProjectAction() {
      override fun isShowOptions(project: Project?) = false

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

  override fun getDetailsComponent(project: Project): JComponent? {
    if (!ActionInfo.UPDATE.showOptions(project)) return super.getDetailsComponent(project)
    return panel {
      row { label(SmartUpdateBundle.message("warning.default.update.options.will.be.applied")) }
      row { link(SmartUpdateBundle.message("label.change.options")) { showOptionsDialog(project) } }
    }
  }

  private fun showOptionsDialog(project: Project) {
    val map = CommonUpdateProjectAction().getConfigurableToEnvMap(project)
    ActionInfo.UPDATE.createOptionsDialog(project, map, ScopeInfo.PROJECT.getScopeName(DataContext.EMPTY_CONTEXT, ActionInfo.UPDATE)).show()
    showOptionsListener.invoke(ActionInfo.UPDATE.showOptions(project))
  }

  override fun detailsVisible(project: Project): ComponentPredicate {
    return object : ComponentPredicate() {
      override fun addListener(listener: (Boolean) -> Unit) {
        showOptionsListener = listener
      }

      override fun invoke() = ActionInfo.UPDATE.showOptions(project)
    }
  }
}
