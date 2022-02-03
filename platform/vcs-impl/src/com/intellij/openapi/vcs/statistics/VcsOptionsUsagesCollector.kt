// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.statistics

import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.beans.*
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.ignore.IgnoredToExcludedSynchronizerConstants.ASKED_MARK_IGNORED_FILES_AS_EXCLUDED_PROPERTY
import org.jetbrains.annotations.NonNls
import java.util.*

@NonNls
class VcsOptionsUsagesCollector : ProjectUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(project: Project): MutableSet<MetricEvent> {
    val set = HashSet<MetricEvent>()

    val conf = VcsConfiguration.getInstance(project)
    val confDefault = VcsConfiguration()

    addBoolIfDiffers(set, conf, confDefault, { it.OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT }, OFFER_MOVE_PARTIALLY_COMMITTED)
    addConfirmationIfDiffers(set, conf, confDefault, { it.MOVE_TO_FAILED_COMMIT_CHANGELIST }, OFFER_MOVE_FAILED_COMMITTED)
    addConfirmationIfDiffers(set, conf, confDefault, { it.REMOVE_EMPTY_INACTIVE_CHANGELISTS }, OFFER_REMOVE_EMPTY_CHANGELIST)

    addBoolIfDiffers(set, conf, confDefault, { it.MAKE_NEW_CHANGELIST_ACTIVE }, CHANGELIST_MAKE_NEW_ACTIVE)
    addBoolIfDiffers(set, conf, confDefault, { it.PRESELECT_EXISTING_CHANGELIST }, CHANGELIST_PRESELECT_EXISTING)

    addBoolIfDiffers(set, conf, confDefault, { it.CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT }, COMMIT_BEFORE_CHECK_CODE_SMELL)
    addBoolIfDiffers(set, conf, confDefault, { it.CHECK_CODE_CLEANUP_BEFORE_PROJECT_COMMIT }, COMMIT_BEFORE_CHECK_CODE_CLEANUP)
    addBoolIfDiffers(set, conf, confDefault, { it.CHECK_NEW_TODO }, COMMIT_BEFORE_CHECK_TODO)
    addBoolIfDiffers(set, conf, confDefault, { it.FORCE_NON_EMPTY_COMMENT }, COMMIT_BEFORE_CHECK_NON_EMPTY_COMMENT)
    addBoolIfDiffers(set, conf, confDefault, { it.OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT }, COMMIT_BEFORE_OPTIMIZE_IMPORTS)
    addBoolIfDiffers(set, conf, confDefault, { it.REFORMAT_BEFORE_PROJECT_COMMIT }, COMMIT_BEFORE_REFORMAT_PROJECT)
    addBoolIfDiffers(set, conf, confDefault, { it.REARRANGE_BEFORE_PROJECT_COMMIT }, COMMIT_BEFORE_REARRANGE)

    addBoolIfDiffers(set, conf, confDefault, { it.CLEAR_INITIAL_COMMIT_MESSAGE }, COMMIT_CLEAR_INITIAL_COMMENT)
    addBoolIfDiffers(set, conf, confDefault, { it.USE_COMMIT_MESSAGE_MARGIN }, COMMIT_USE_RIGHT_MARGIN)
    addBoolIfDiffers(set, conf, confDefault, { it.SHOW_UNVERSIONED_FILES_WHILE_COMMIT }, COMMIT_SHOW_UNVERSIONED)

    addBoolIfDiffers(set, conf, confDefault, { it.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN }, SHOW_CHANGES_PREVIEW)
    addBoolIfDiffers(set, conf, confDefault, { it.INCLUDE_TEXT_INTO_SHELF }, INCLUDE_TEXT_INTO_SHELF)
    addBoolIfDiffers(set, conf, confDefault, { it.CHECK_LOCALLY_CHANGED_CONFLICTS_IN_BACKGROUND }, CHECK_CONFLICTS_IN_BACKGROUND)

    addExternalFilesActionsStatistics(project, set, conf, confDefault)
    addProjectConfigurationFilesActionsStatistics(project, set)
    addIgnoredToExcludeSynchronizerActionsStatistics(project, set)

    return set
  }

  private fun addIgnoredToExcludeSynchronizerActionsStatistics(project: Project, set: HashSet<MetricEvent>) {
    addBooleanPropertyIfDiffers(project, set, ASKED_MARK_IGNORED_FILES_AS_EXCLUDED_PROPERTY, false, ASKED_ADD_EXTERNAL_FILES)
  }

  private fun addProjectConfigurationFilesActionsStatistics(project: Project, set: HashSet<MetricEvent>) {
    //SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY can be set automatically to true without ASKED_SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY
    //Such case should be filtered in order to check only a user manual interaction.
    val askedToShare = booleanPropertyIfDiffers(project, ASKED_SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY, false)
    if (askedToShare != null) {
      if (!addBooleanPropertyIfDiffers(project, set, SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY, false, SHARE_PROJECT_CONFIGURATION_FILES)) {
        set.add(ASKED_SHARE_PROJECT_CONFIGURATION_FILES.metric(askedToShare))
      }
    }
  }

  private fun addExternalFilesActionsStatistics(project: Project, set: HashSet<MetricEvent>, conf: VcsConfiguration, confDefault: VcsConfiguration) {
    addBoolIfDiffers(set, conf, confDefault, { it.ADD_EXTERNAL_FILES_SILENTLY }, ADD_EXTERNAL_FILES_SILENTLY)
    if (!conf.ADD_EXTERNAL_FILES_SILENTLY) {
      addBooleanPropertyIfDiffers(project, set, ASKED_ADD_EXTERNAL_FILES_PROPERTY, false, ASKED_ADD_EXTERNAL_FILES)
    }
  }

  private fun addBooleanPropertyIfDiffers(project: Project, set: HashSet<MetricEvent>,
                                          property: String, defaultValue: Boolean, eventId: EventId1<Boolean>): Boolean {
    val value = booleanPropertyIfDiffers(project, property, defaultValue)
    if (value != null) {
      return set.add(eventId.metric(value))
    }

    return false
  }

  private fun booleanPropertyIfDiffers(project: Project, property: String, defaultValue: Boolean): Boolean? {
    val value = PropertiesComponent.getInstance(project).getBoolean(property, defaultValue)
    return if (value != defaultValue) value else null
  }

  companion object {
    private val GROUP = EventLogGroup("vcs.settings", 3)
    private val OFFER_MOVE_PARTIALLY_COMMITTED = GROUP.registerVarargEvent("offer.move.partially.committed", EventFields.Enabled)

    private val OFFER_MOVE_FAILED_COMMITTED = GROUP.registerEvent("offer.move.failed.committed", EventFields.Enum("value", ConfirmationOption::class.java))
    private val OFFER_REMOVE_EMPTY_CHANGELIST = GROUP.registerEvent("offer.remove.empty.changelist", EventFields.Enum("value", ConfirmationOption::class.java))

    private val CHANGELIST_MAKE_NEW_ACTIVE = GROUP.registerVarargEvent("changelist.make.new.active", EventFields.Enabled)
    private val CHANGELIST_PRESELECT_EXISTING = GROUP.registerVarargEvent("changelist.preselect.existing", EventFields.Enabled)
    private val COMMIT_BEFORE_CHECK_CODE_SMELL = GROUP.registerVarargEvent("commit.before.check.code.smell", EventFields.Enabled)
    private val COMMIT_BEFORE_CHECK_CODE_CLEANUP = GROUP.registerVarargEvent("commit.before.check.code.cleanup", EventFields.Enabled)
    private val COMMIT_BEFORE_CHECK_TODO = GROUP.registerVarargEvent("commit.before.check.todo", EventFields.Enabled)
    private val COMMIT_BEFORE_CHECK_NON_EMPTY_COMMENT = GROUP.registerVarargEvent("commit.before.check.non.empty.comment", EventFields.Enabled)
    private val COMMIT_BEFORE_OPTIMIZE_IMPORTS = GROUP.registerVarargEvent("commit.before.optimize.imports", EventFields.Enabled)
    private val COMMIT_BEFORE_REFORMAT_PROJECT = GROUP.registerVarargEvent("commit.before.reformat.project", EventFields.Enabled)
    private val COMMIT_BEFORE_REARRANGE = GROUP.registerVarargEvent("commit.before.rearrange", EventFields.Enabled)
    private val COMMIT_CLEAR_INITIAL_COMMENT = GROUP.registerVarargEvent("commit.clear.initial.comment", EventFields.Enabled)
    private val COMMIT_USE_RIGHT_MARGIN = GROUP.registerVarargEvent("commit.use.right.margin", EventFields.Enabled)
    private val COMMIT_SHOW_UNVERSIONED = GROUP.registerVarargEvent("commit.show.unversioned", EventFields.Enabled)
    private val SHOW_CHANGES_PREVIEW = GROUP.registerVarargEvent("show.changes.preview", EventFields.Enabled)
    private val INCLUDE_TEXT_INTO_SHELF = GROUP.registerVarargEvent("include.text.into.shelf", EventFields.Enabled)
    private val CHECK_CONFLICTS_IN_BACKGROUND = GROUP.registerVarargEvent("check.conflicts.in.background", EventFields.Enabled)
    private val ADD_EXTERNAL_FILES_SILENTLY = GROUP.registerVarargEvent("add.external.files.silently", EventFields.Enabled)
    private val ASKED_ADD_EXTERNAL_FILES = GROUP.registerEvent("asked.add.external.files", EventFields.Enabled)
    private val SHARE_PROJECT_CONFIGURATION_FILES = GROUP.registerEvent("share.project.configuration.files", EventFields.Enabled)
    private val ASKED_SHARE_PROJECT_CONFIGURATION_FILES = GROUP.registerEvent("asked.share.project.configuration.files", EventFields.Enabled)

    private fun <T> addConfirmationIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                                             valueFunction: Function1<T, VcsShowConfirmationOption.Value>, eventId: EventId1<ConfirmationOption>) {
      addMetricIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction) {
        val value = when (it) {
          VcsShowConfirmationOption.Value.SHOW_CONFIRMATION -> ConfirmationOption.ask // NON-NLS
          VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY -> ConfirmationOption.disabled // NON-NLS
          VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY -> ConfirmationOption.silently // NON-NLS
          else -> ConfirmationOption.unknown // NON-NLS
        }
        return@addMetricIfDiffers eventId.metric(value)
      }
    }

    @Suppress("EnumEntryName")
    private enum class ConfirmationOption {
      ask, disabled, silently, unknown
    }
  }
}
