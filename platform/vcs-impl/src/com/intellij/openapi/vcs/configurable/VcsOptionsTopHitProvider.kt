// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.conflicts.ChangelistConflictTracker
import com.intellij.openapi.vcs.contentAnnotation.VcsContentAnnotationSettings
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.ui.layout.*
import com.intellij.vcs.commit.CommitModeManager.Companion.setCommitFromLocalChanges
import com.intellij.vcs.commit.message.CommitMessageInspectionProfile

private val vcsOptionGroupName get() = VcsBundle.message("settings.version.control.option.group")
private val commitMessageOptionGroupName get() = VcsBundle.message("settings.commit.message.option.group")
private val commitOptionGroupName get() = VcsBundle.message("settings.commit.option.group")
private val confirmationOptionGroupName get() = VcsBundle.message("settings.confirmation.option.group")
private val changelistsOptionGroupName get() = VcsBundle.message("settings.changelists.option.group")

private fun vcsConfiguration(project: Project) = VcsConfiguration.getInstance(project)
private fun changelistsOptions(project: Project) = ChangelistConflictTracker.getInstance(project).options

private fun cdLimitMaximumHistory(project: Project): CheckboxDescriptor {
  val vcs = vcsConfiguration(project)
  val name = VcsBundle.message("settings.limit.history.to.n.rows.label", vcs.MAXIMUM_HISTORY_ROWS)
  return CheckboxDescriptor(name, vcs::LIMIT_HISTORY, groupName = vcsOptionGroupName)
}

private fun cdShowChangesLastNDays(project: Project): CheckboxDescriptor {
  val vcsCA = VcsContentAnnotationSettings.getInstance(project)
  val name = VcsBundle.message("settings.show.changed.in.last.n.days.label", vcsCA.limitDays)
  return CheckboxDescriptor(name, PropertyBinding(vcsCA::isShow, vcsCA::setShow), groupName = vcsOptionGroupName)
}

private fun cdShowReadOnlyStatusDialog(project: Project): CheckboxDescriptor {
  val state = (ReadonlyStatusHandler.getInstance(project) as ReadonlyStatusHandlerImpl).state
  val name = VcsBundle.message("checkbox.show.clear.read.only.status.dialog")
  return CheckboxDescriptor(name, state::SHOW_DIALOG, groupName = vcsOptionGroupName)
}

private fun cdShowRightMargin(project: Project): CheckboxDescriptor {
  val margin = CommitMessageInspectionProfile.getBodyRightMargin(project)
  val name = VcsBundle.message("settings.commit.message.show.right.margin.n.columns.label", margin)
  return CheckboxDescriptor(name, vcsConfiguration(project)::USE_COMMIT_MESSAGE_MARGIN, groupName = commitMessageOptionGroupName)
}

// @formatter:off
private fun cdShowDirtyRecursively(project: Project): CheckboxDescriptor =          CheckboxDescriptor(VcsBundle.message("checkbox.show.dirty.recursively"), vcsConfiguration(project)::SHOW_DIRTY_RECURSIVELY, groupName = vcsOptionGroupName)
private fun cdWrapTypingOnRightMargin(project: Project): CheckboxDescriptor =       CheckboxDescriptor(ApplicationBundle.message("checkbox.wrap.typing.on.right.margin"), vcsConfiguration(project)::WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN, groupName = commitMessageOptionGroupName)
private fun cdForceNonEmptyCommitMessage(project: Project): CheckboxDescriptor =    CheckboxDescriptor(VcsBundle.message("checkbox.force.non.empty.messages"), vcsConfiguration(project)::FORCE_NON_EMPTY_COMMENT, groupName = commitMessageOptionGroupName)
private fun cdClearInitialCommitMessage(project: Project): CheckboxDescriptor =     CheckboxDescriptor(VcsBundle.message("checkbox.clear.initial.commit.message"), vcsConfiguration(project)::CLEAR_INITIAL_COMMIT_MESSAGE, groupName = commitMessageOptionGroupName)
private fun cdOfferMoveAnotherChangelist(project: Project): CheckboxDescriptor =    CheckboxDescriptor(VcsBundle.message("checkbox.changelist.move.offer"), vcsConfiguration(project)::OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT, groupName = confirmationOptionGroupName)
private fun cdIncludeShelfBaseContent(project: Project): CheckboxDescriptor =       CheckboxDescriptor(VcsBundle.message("vcs.shelf.store.base.content"), vcsConfiguration(project)::INCLUDE_TEXT_INTO_SHELF, groupName = confirmationOptionGroupName)
private fun cdChangelistConflictDialog(project: Project): CheckboxDescriptor =      CheckboxDescriptor(VcsBundle.message("settings.show.conflict.resolve.dialog.checkbox"), changelistsOptions(project)::SHOW_DIALOG, groupName = changelistsOptionGroupName)
private fun cdChangelistShowConflicts(project: Project): CheckboxDescriptor =       CheckboxDescriptor(VcsBundle.message("settings.highlight.files.with.conflicts.checkbox"), changelistsOptions(project)::HIGHLIGHT_CONFLICTS, groupName = changelistsOptionGroupName)
private fun cdChangelistShowNonCurrent(project: Project): CheckboxDescriptor =      CheckboxDescriptor(VcsBundle.message("settings.highlight.files.from.non.active.changelist.checkbox"), changelistsOptions(project)::HIGHLIGHT_NON_ACTIVE_CHANGELIST, groupName = changelistsOptionGroupName)
private fun cdNonModalCommit(project: Project): CheckboxDescriptor =                CheckboxDescriptor(VcsBundle.message("settings.commit.without.dialog"), PropertyBinding({ VcsApplicationSettings.getInstance().COMMIT_FROM_LOCAL_CHANGES }, { setCommitFromLocalChanges(project, it) }), groupName = commitOptionGroupName)
// @formatter:on

class VcsOptionsTopHitProvider : VcsOptionsTopHitProviderBase() {
  override fun getId(): String {
    return "vcs"
  }

  override fun getOptions(project: Project): Collection<OptionDescription> {
    if (!project.isDefault && !ProjectLevelVcsManager.getInstance(project).hasActiveVcss()) {
      return emptyList()
    }
    return listOf(
      cdLimitMaximumHistory(project),
      cdShowChangesLastNDays(project),
      cdShowReadOnlyStatusDialog(project),
      cdShowRightMargin(project),
      cdShowDirtyRecursively(project),
      cdWrapTypingOnRightMargin(project),
      cdForceNonEmptyCommitMessage(project),
      cdClearInitialCommitMessage(project),
      cdOfferMoveAnotherChangelist(project),
      cdIncludeShelfBaseContent(project),
      cdChangelistConflictDialog(project),
      cdChangelistShowConflicts(project),
      cdChangelistShowNonCurrent(project),
      cdNonModalCommit(project)
    ).map(CheckboxDescriptor::asOptionDescriptor)
  }
}