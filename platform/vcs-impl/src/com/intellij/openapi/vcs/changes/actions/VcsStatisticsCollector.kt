// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.changes.Change
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object VcsStatisticsCollector : CounterUsagesCollector() {
  val GROUP = EventLogGroup("vcs", 17)

  @JvmField
  val UPDATE_ACTIVITY = GROUP.registerIdeActivity("update")

  @JvmField
  val ANNOTATE_ACTIVITY = GROUP.registerIdeActivity("annotate",
                                                    finishEventAdditionalFields = arrayOf(ActionsEventLogGroup.CONTEXT_MENU,
                                                                                          EventFields.ActionPlace))
  val FETCH_ACTIVITY = GROUP.registerIdeActivity("fetch")
  val COMMIT_ACTIVITY = GROUP.registerIdeActivity("commit")

  private val WAS_UPDATING_BEFORE = EventFields.Boolean("wasUpdatingBefore")
  private val CHANGES_DELTA = EventFields.Int("changesDelta")
  private val UNVERSIONED_DELTA = EventFields.Int("unversionedDelta")
  private val CHANGES_VIEW_REFRESH = GROUP.registerVarargEvent("changes.view.refresh", WAS_UPDATING_BEFORE, CHANGES_DELTA,
                                                               UNVERSIONED_DELTA)

  private val NON_MODAL_COMMIT_STATE_CHANGED = GROUP.registerEvent("non.modal.commit.state.changed", EventFields.Enabled)

  val NON_MODAL_COMMIT_SLOW_CHECKS_CHANGED = GROUP.registerEvent("non.modal.commit.slow.checks.changed", EventFields.Enabled)

  val CLONE = GROUP.registerEvent("clone.invoked", EventFields.Class("clone_dialog_extension"))

  @JvmField
  val CLONED_PROJECT_OPENED = GROUP.registerEvent("cloned.project.opened")

  private val CHANGE_LIST_EDITED_PLACE = EventFields.Enum("editingPlace", EditChangeListPlace::class.java)
  @JvmField
  val CHANGE_LIST_COMMENT_EDITED = GROUP.registerEvent("change.list.edit.description", CHANGE_LIST_EDITED_PLACE)
  @JvmField
  val CHANGE_LIST_NAME_EDITED = GROUP.registerEvent("change.list.edit.name")

  private val VCS_FIELD = EventFields.StringValidatedByEnum("vcs", "vcs")
  private val IS_FULL_REFRESH_FIELD = EventFields.Boolean("is_full_refresh")
  private val CLM_REFRESH = GROUP.registerIdeActivity(activityName = "clm.refresh",
                                                      startEventAdditionalFields = arrayOf(VCS_FIELD, IS_FULL_REFRESH_FIELD))

  @JvmStatic
  fun logRefreshActionPerformed(
    project: Project,
    changesBefore: Collection<Change>,
    changesAfter: Collection<Change>,
    unversionedBefore: Collection<FilePath>,
    unversionedAfter: Collection<FilePath>,
    wasUpdatingBefore: Boolean,
  ) {
    val changesDelta = computeDelta(changesBefore, changesAfter)
    val unversionedDelta = computeDelta(unversionedBefore, unversionedAfter)

    CHANGES_VIEW_REFRESH.log(project,
                             WAS_UPDATING_BEFORE.with(wasUpdatingBefore),
                             CHANGES_DELTA.with(changesDelta),
                             UNVERSIONED_DELTA.with(unversionedDelta))
  }

  @JvmStatic
  fun logClmRefresh(project: Project, vcs: AbstractVcs, everythingDirty: Boolean): StructuredIdeActivity {
    return CLM_REFRESH.started(project) {
      listOf(VCS_FIELD.with(vcs.name),
             IS_FULL_REFRESH_FIELD.with(everythingDirty))
    }
  }

  fun logNonModalCommitStateChanged(project: Project?) {
    NON_MODAL_COMMIT_STATE_CHANGED.log(project, VcsApplicationSettings.getInstance().COMMIT_FROM_LOCAL_CHANGES)
  }

  private fun <T> computeDelta(before: Collection<T>, after: Collection<T>): Int {
    val beforeSet = before.toHashSet()
    val afterSet = after.toHashSet()

    var result = 0
    for (value in beforeSet) {
      if (!afterSet.contains(value)) {
        result++
      }
    }
    for (value in afterSet) {
      if (!beforeSet.contains(value)) {
        result++
      }
    }
    return result
  }

  override fun getGroup(): EventLogGroup = GROUP

  enum class EditChangeListPlace {
    EDIT_DIALOG, OTHER
  }
}
