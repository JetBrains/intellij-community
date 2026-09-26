package com.intellij.smartUpdate

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.update.ActionInfo
import com.intellij.openapi.vcs.update.ScopeInfo
import com.intellij.openapi.vcs.update.VcsUpdateProcess
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ComponentPredicate
import javax.swing.JComponent

private const val VCS_UPDATE = "vcs.update"

internal class VcsUpdateStep : SmartUpdateStep {
  private val actionInfo = ActionInfo.UPDATE
  private val scopeInfo = ScopeInfo.PROJECT

  private lateinit var showOptionsListener: (Boolean) -> Unit
  override val id: String = VCS_UPDATE
  override val stepName = SmartUpdateBundle.message("checkbox.update.project")

  override fun performUpdateStep(project: Project, e: AnActionEvent?, onSuccess: () -> Unit) {
    val start = System.currentTimeMillis()
    val dataContext = SimpleDataContext.getProjectContext(project)
    VcsUpdateProcess.launchUpdate(project, actionInfo, scopeInfo, dataContext, false, SmartUpdateBundle.message("action.update.project.text")) {
      SmartUpdateUsagesCollector.logUpdate(System.currentTimeMillis() - start)
      onSuccess.invoke()
    }
  }

  override fun getDetailsComponent(project: Project): JComponent? {
    if (!actionInfo.showOptions(project)) return super.getDetailsComponent(project)
    return panel {
      row { label(SmartUpdateBundle.message("warning.default.update.options.will.be.applied")) }
      row { link(SmartUpdateBundle.message("label.change.options")) { showOptionsDialog(project) } }
    }
  }

  private fun showOptionsDialog(project: Project) {
    val showOptions = ActionInfo.UPDATE.showOptions(project)
    showOptionsListener.invoke(showOptions)
    if (showOptions) {
      val roots = VcsUpdateProcess.getRoots(project, actionInfo, scopeInfo, SimpleDataContext.getProjectContext(project))
      val spec = VcsUpdateProcess.createUpdateSpec(project, roots, actionInfo)
      VcsUpdateProcess.showOptionsDialog(project, actionInfo, scopeInfo, spec, DataContext.EMPTY_CONTEXT)
    }
  }

  override fun detailsVisible(project: Project): ComponentPredicate {
    return object : ComponentPredicate() {
      override fun addListener(listener: (Boolean) -> Unit) {
        showOptionsListener = listener
      }

      override fun invoke() = actionInfo.showOptions(project)
    }
  }
}
