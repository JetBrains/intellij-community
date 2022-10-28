// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.configurable

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler.Companion.getDefaultCommitActionName
import com.intellij.vcs.commit.CommitModeManager
import com.intellij.vcs.commit.CommitOptionsPanel
import com.intellij.vcs.commit.message.CommitMessageInspectionsPanel
import org.jetbrains.annotations.NonNls

class CommitDialogConfigurable(private val project: Project)
  : BoundSearchableConfigurable(VcsBundle.message("commit.dialog.configurable"), HELP_ID, ID) {
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

      val actionName = UIUtil.removeMnemonic(getDefaultCommitActionName(emptyList()))
      group(CommitOptionsPanel.commitChecksGroupTitle(project, actionName)) {
        val panel = CommitOptionsConfigurable(project)
        Disposer.register(disposable, panel)
        row {
          cell(panel)
            .align(AlignX.FILL)
            .onApply { panel.apply() }
            .onReset { panel.reset() }
            .onIsModified { panel.isModified }
        }.resizableRow()
      }
    }
  }

  companion object {
    const val ID: @NonNls String = "project.propVCSSupport.CommitDialog"
    private const val HELP_ID: @NonNls String = "reference.settings.VCS.CommitDialog"
  }
}
