// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector

internal fun setRunSlowCommitChecksAfterCommit(project: Project, value: Boolean) {
  val vcsConfiguration = VcsConfiguration.getInstance(project)
  val currentValue = vcsConfiguration.NON_MODAL_COMMIT_POSTPONE_SLOW_CHECKS
  if (currentValue == value) return

  vcsConfiguration.NON_MODAL_COMMIT_POSTPONE_SLOW_CHECKS = value
  VcsStatisticsCollector.NON_MODAL_COMMIT_SLOW_CHECKS_CHANGED.log(project, value)
}
