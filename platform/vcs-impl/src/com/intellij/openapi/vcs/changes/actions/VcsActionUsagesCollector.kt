// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change

private const val VCS_GROUP = "vcs"
private const val REFRESH_ACTION_ID = "changes.view.refresh"

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

  val data = FeatureUsageData()
    .addData("wasUpdatingBefore", wasUpdatingBefore)
    .addData("changesDelta", changes.size)
    .addData("unversionedDelta", unversioned.size)

  FUCounterUsageLogger.getInstance().logEvent(project, VCS_GROUP, REFRESH_ACTION_ID, data)
}