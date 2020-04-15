// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.statistics

import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.beans.*
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.ignore.IgnoredToExcludedSynchronizerConstants.ASKED_MARK_IGNORED_FILES_AS_EXCLUDED_PROPERTY
import java.util.*

class VcsOptionsUsagesCollector : ProjectUsagesCollector() {
  override fun getGroupId(): String = "vcs.settings"
  override fun getVersion(): Int = 2

  override fun getMetrics(project: Project): MutableSet<MetricEvent> {
    val set = HashSet<MetricEvent>()

    val conf = VcsConfiguration.getInstance(project)
    val confDefault = VcsConfiguration()

    addBoolIfDiffers(set, conf, confDefault, { it.OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT }, "offer.move.partially.committed")
    addConfirmationIfDiffers(set, conf, confDefault, { it.MOVE_TO_FAILED_COMMIT_CHANGELIST }, "offer.move.failed.committed")
    addConfirmationIfDiffers(set, conf, confDefault, { it.REMOVE_EMPTY_INACTIVE_CHANGELISTS }, "offer.remove.empty.changelist")

    addBoolIfDiffers(set, conf, confDefault, { it.MAKE_NEW_CHANGELIST_ACTIVE }, "changelist.make.new.active")
    addBoolIfDiffers(set, conf, confDefault, { it.PRESELECT_EXISTING_CHANGELIST }, "changelist.preselect.existing")

    addBoolIfDiffers(set, conf, confDefault, { it.PERFORM_UPDATE_IN_BACKGROUND }, "perform.update.in.background")
    addBoolIfDiffers(set, conf, confDefault, { it.PERFORM_COMMIT_IN_BACKGROUND }, "perform.commit.in.background")
    addBoolIfDiffers(set, conf, confDefault, { it.PERFORM_EDIT_IN_BACKGROUND }, "perform.edit.in.background")
    addBoolIfDiffers(set, conf, confDefault, { it.PERFORM_CHECKOUT_IN_BACKGROUND }, "perform.checkout.in.background")
    addBoolIfDiffers(set, conf, confDefault, { it.PERFORM_ADD_REMOVE_IN_BACKGROUND }, "perform.add_remove.in.background")
    addBoolIfDiffers(set, conf, confDefault, { it.PERFORM_ROLLBACK_IN_BACKGROUND }, "perform.rollback.in.background")

    addBoolIfDiffers(set, conf, confDefault, { it.CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT }, "commit.before.check.code.smell")
    addBoolIfDiffers(set, conf, confDefault, { it.CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT }, "commit.before.check.code.cleanup")
    addBoolIfDiffers(set, conf, confDefault, { it.CHECK_NEW_TODO }, "commit.before.check.todo")
    addBoolIfDiffers(set, conf, confDefault, { it.FORCE_NON_EMPTY_COMMENT }, "commit.before.check.non.empty.comment")
    addBoolIfDiffers(set, conf, confDefault, { it.OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT }, "commit.before.optimize.imports")
    addBoolIfDiffers(set, conf, confDefault, { it.REFORMAT_BEFORE_PROJECT_COMMIT }, "commit.before.reformat.project")
    addBoolIfDiffers(set, conf, confDefault, { it.REARRANGE_BEFORE_PROJECT_COMMIT }, "commit.before.rearrange")

    addBoolIfDiffers(set, conf, confDefault, { it.CLEAR_INITIAL_COMMIT_MESSAGE }, "commit.clear.initial.comment")
    addBoolIfDiffers(set, conf, confDefault, { it.USE_COMMIT_MESSAGE_MARGIN }, "commit.use.right.margin")
    addBoolIfDiffers(set, conf, confDefault, { it.SHOW_UNVERSIONED_FILES_WHILE_COMMIT }, "commit.show.unversioned")

    addBoolIfDiffers(set, conf, confDefault, { it.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN }, "show.changes.preview")
    addBoolIfDiffers(set, conf, confDefault, { it.INCLUDE_TEXT_INTO_SHELF }, "include.text.into.shelf")
    addBoolIfDiffers(set, conf, confDefault, { it.CHECK_LOCALLY_CHANGED_CONFLICTS_IN_BACKGROUND }, "check.conflicts.in.background")

    addExternalFilesActionsStatistics(project, set, conf, confDefault)
    addProjectConfigurationFilesActionsStatistics(project, set)
    addIgnoredToExcludeSynchronizerActionsStatistics(project, set)

    return set
  }

  private fun addIgnoredToExcludeSynchronizerActionsStatistics(project: Project, set: HashSet<MetricEvent>) {
    addBooleanPropertyIfDiffers(project, set, ASKED_MARK_IGNORED_FILES_AS_EXCLUDED_PROPERTY, false, "asked.add.external.files")
  }

  private fun addProjectConfigurationFilesActionsStatistics(project: Project, set: HashSet<MetricEvent>) {
    //SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY can be set automatically to true without ASKED_SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY
    //Such case should be filtered in order to check only an user manual interaction.
    val askedToShare = booleanPropertyIfDiffers(project, ASKED_SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY, false)
    if (askedToShare != null) {
      if (!addBooleanPropertyIfDiffers(project, set, SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY, false, "share.project.configuration.files")) {
        set.add(newBooleanMetric("asked.share.project.configuration.files", askedToShare))
      }
    }
  }

  private fun addExternalFilesActionsStatistics(project: Project, set: HashSet<MetricEvent>, conf: VcsConfiguration, confDefault: VcsConfiguration) {
    addBoolIfDiffers(set, conf, confDefault, { it.ADD_EXTERNAL_FILES_SILENTLY }, "add.external.files.silently")
    if (!conf.ADD_EXTERNAL_FILES_SILENTLY) {
      addBooleanPropertyIfDiffers(project, set, ASKED_ADD_EXTERNAL_FILES_PROPERTY, false, "asked.add.external.files")
    }
  }

  private fun addBooleanPropertyIfDiffers(project: Project, set: HashSet<MetricEvent>,
                                          property: String, defaultValue: Boolean, eventId: String): Boolean {
    val value = booleanPropertyIfDiffers(project, property, defaultValue)
    if (value != null) {
      return set.add(newBooleanMetric(eventId, value))
    }

    return false
  }

  private fun booleanPropertyIfDiffers(project: Project, property: String, defaultValue: Boolean): Boolean? {
    val value = PropertiesComponent.getInstance(project).getBoolean(property, defaultValue)
    return if (value != defaultValue) value else null
  }

  companion object {
    private fun <T> addConfirmationIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                                             valueFunction: Function1<T, VcsShowConfirmationOption.Value>, eventId: String) {
      addMetricIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction) {
        val value = when (it) {
          VcsShowConfirmationOption.Value.SHOW_CONFIRMATION -> "ask"
          VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY -> "disabled"
          VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY -> "silently"
          else -> "unknown"
        }
        return@addMetricIfDiffers newMetric(eventId, value)
      }
    }
  }
}
