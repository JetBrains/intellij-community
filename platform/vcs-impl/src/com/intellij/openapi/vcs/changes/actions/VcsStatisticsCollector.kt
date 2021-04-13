// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.internal.statistic.IdeActivityDefinition
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change

class VcsStatisticsCollector : CounterUsagesCollector() {
  companion object {
    @JvmField
    val GROUP = EventLogGroup("vcs", 8)

    @JvmField
    val UPDATE_ACTIVITY = GROUP.registerIdeActivity("update")

    @JvmField
    val FETCH_ACTIVITY = GROUP.registerIdeActivity("fetch")

    @JvmField
    val COMMIT_ACTIVITY = GROUP.registerIdeActivity("commit")

    private val WAS_UPDATING_BEFORE = EventFields.Boolean("wasUpdatingBefore")
    private val CHANGES_DELTA = EventFields.Int("changesDelta")
    private val UNVERSIONED_DELTA = EventFields.Int("unversionedDelta")
    private val CHANGES_VIEW_REFRESH = GROUP.registerVarargEvent("changes.view.refresh", WAS_UPDATING_BEFORE, CHANGES_DELTA, UNVERSIONED_DELTA)

    val NON_MODAL_COMMIT_STATE_CHANGED = GROUP.registerEvent("non.modal.commit.state.changed", EventFields.Enabled)
    val NON_MODAL_COMMIT_PROMOTION_SHOWN = GROUP.registerEvent("non.modal.commit.promotion.shown")
    val NON_MODAL_COMMIT_PROMOTION_ACCEPTED = GROUP.registerEvent("non.modal.commit.promotion.accepted")
    val NON_MODAL_COMMIT_PROMOTION_REJECTED = GROUP.registerEvent("non.modal.commit.promotion.rejected")

    @JvmStatic
    fun logRefreshActionPerformed(project: Project,
                                  changesBefore: Collection<Change>,
                                  changesAfter: Collection<Change>,
                                  unversionedBefore: Collection<FilePath>,
                                  unversionedAfter: Collection<FilePath>,
                                  wasUpdatingBefore: Boolean) {

      val changes: MutableSet<Change> = (changesBefore union changesAfter).toMutableSet()
      changes.removeAll(changesBefore intersect changesAfter)

      val unversioned: MutableSet<FilePath> = (unversionedBefore union unversionedAfter).toMutableSet()
      unversioned.removeAll(unversionedBefore intersect unversionedAfter)

      CHANGES_VIEW_REFRESH.log(project, WAS_UPDATING_BEFORE.with(wasUpdatingBefore), CHANGES_DELTA.with(changes.size),
                               UNVERSIONED_DELTA.with(unversioned.size))
    }
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}