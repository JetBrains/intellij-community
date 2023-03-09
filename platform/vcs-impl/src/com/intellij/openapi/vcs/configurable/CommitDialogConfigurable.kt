// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.configurable

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.BoundCompositeSearchableConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.containers.mapNotNullLoggingErrors
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.commit.*
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler.Companion.getDefaultCommitActionName
import com.intellij.vcs.commit.message.CommitMessageInspectionsPanel
import org.jetbrains.annotations.NonNls
import java.io.File

class CommitDialogConfigurable(private val project: Project)
  : BoundCompositeSearchableConfigurable<UnnamedConfigurable>(VcsBundle.message("commit.dialog.configurable"), HELP_ID, ID) {

  override fun createConfigurables(): List<UnnamedConfigurable> {
    val allVcses = ProjectLevelVcsManager.getInstance(project).allActiveVcss.toList()
    val checkinPanel = SettingsMockCheckinPanel(project)
    val commitContext = CommitContext()
    val checkinHandlers = AbstractCommitWorkflow.getCommitHandlers(allVcses, checkinPanel, commitContext)
    return checkinHandlers.mapNotNullLoggingErrors(LOG) { it.beforeCheckinSettings }
  }

  override fun createPanel(): DialogPanel {
    val disposable = disposable!!
    val appSettings = VcsApplicationSettings.getInstance()
    val settings = VcsConfiguration.getInstance(project)

    return panel {
      row {
        checkBox(VcsBundle.message("settings.commit.without.dialog"))
          .comment(VcsBundle.message("settings.commit.without.dialog.applies.to.git.mercurial"))
          .bindSelected({ appSettings.COMMIT_FROM_LOCAL_CHANGES }, { CommitModeManager.setCommitFromLocalChanges(project, it) })
      }

      row {
        checkBox(VcsBundle.message("checkbox.clear.initial.commit.message"))
          .bindSelected(settings::CLEAR_INITIAL_COMMIT_MESSAGE)
      }

      group(VcsBundle.message("settings.commit.message.inspections")) {
        row {
          val panel = CommitMessageInspectionsPanel(project)
          Disposer.register(disposable, panel)
          cell(panel)
            .align(AlignX.FILL)
            .onApply { panel.apply() }
            .onReset { panel.reset() }
            .onIsModified { panel.isModified }
        }.resizableRow()
      }

      if (configurables.isNotEmpty()) {
        val actionName = UIUtil.removeMnemonic(getDefaultCommitActionName(emptyList()))
        group(CommitOptionsPanel.commitChecksGroupTitle(project, actionName)) {
          for (configurable in configurables) {
            appendDslConfigurable(configurable)
          }
        }
      }
    }
  }

  private class SettingsMockCheckinPanel(private val project: Project) : CheckinProjectPanel {
    override fun getComponent() = null
    override fun getPreferredFocusedComponent() = null

    override fun getCommitWorkflowHandler(): CommitWorkflowHandler = NullCommitWorkflowHandler
    override fun getProject() = project

    override fun vcsIsAffected(name: String) = false
    override fun hasDiffs() = false
    override fun getRoots() = emptyList<VirtualFile>()
    override fun getVirtualFiles() = emptyList<VirtualFile>()
    override fun getSelectedChanges() = emptyList<Change>()
    override fun getFiles() = emptyList<File>()

    override fun getCommitActionName(): String = getDefaultCommitActionName(emptyList())
    override fun setCommitMessage(currentDescription: String) {}
    override fun getCommitMessage() = ""

    override fun saveState() {}
    override fun restoreState() {}
  }

  companion object {
    private val LOG = logger<CommitDialogConfigurable>()

    const val ID: @NonNls String = "project.propVCSSupport.CommitDialog"
    private const val HELP_ID: @NonNls String = "reference.settings.VCS.CommitDialog"
  }
}
