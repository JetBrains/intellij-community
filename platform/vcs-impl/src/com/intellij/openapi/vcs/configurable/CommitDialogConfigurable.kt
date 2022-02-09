// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.configurable

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsShowConfirmationOption
import com.intellij.openapi.vcs.changes.conflicts.ChangelistConflictConfigurable
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.commit.CommitModeManager
import com.intellij.vcs.commit.getDefaultCommitActionName
import com.intellij.vcs.commit.message.CommitMessageInspectionsPanel
import org.jetbrains.annotations.NonNls

class CommitDialogConfigurable(private val project: Project)
  : BoundSearchableConfigurable(VcsBundle.message("commit.dialog.configurable"), HELP_ID, ID) {
  override fun createPanel(): DialogPanel {
    val disposable = disposable!!
    val appSettings = VcsApplicationSettings.getInstance()
    val settings = VcsConfiguration.getInstance(project)
    val changelistsEnabled = ChangelistConflictConfigurable.ChangeListsEnabledPredicate(project, disposable)

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
      row {
        checkBox(VcsBundle.message("checkbox.force.non.empty.messages"))
          .bindSelected(settings::FORCE_NON_EMPTY_COMMENT)
      }
      row {
        checkBox(VcsBundle.message("settings.show.unversioned.files"))
          .bindSelected(settings::SHOW_UNVERSIONED_FILES_WHILE_COMMIT)
      }

      row {
        checkBox(VcsBundle.message("checkbox.changelist.move.offer"))
          .bindSelected(settings::OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT)
      }.enabledIf(changelistsEnabled)
      row(VcsBundle.message("create.changelist.on.failed.commit")) {
        comboBox(EnumComboBoxModel(VcsShowConfirmationOption.Value::class.java),
                 SimpleListCellRenderer.create("", VcsShowConfirmationOption::getConfirmationOptionText))
          .bindItem(settings::MOVE_TO_FAILED_COMMIT_CHANGELIST)
      }.enabledIf(changelistsEnabled)

      group(VcsBundle.message("settings.commit.message.inspections")) {
        row {
          val panel = CommitMessageInspectionsPanel(project)
          Disposer.register(disposable, panel)
          cell(panel)
            .horizontalAlign(HorizontalAlign.FILL)
            .onApply { panel.apply() }
            .onReset { panel.reset() }
            .onIsModified { panel.isModified }
        }.resizableRow()
      }

      val actionName = UIUtil.removeMnemonic(getDefaultCommitActionName())
      group(VcsBundle.message("border.standard.checkin.options.group", actionName)) {
        val panel = CommitOptionsConfigurable(project)
        Disposer.register(disposable, panel)
        row {
          cell(panel)
            .horizontalAlign(HorizontalAlign.FILL)
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
