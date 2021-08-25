// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.util

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsProjectLog
import kotlin.math.max
import kotlin.math.min

fun runWhenVcsAndLogIsReady(project: Project, action: (VcsLogManager) -> Unit) {
  val logManager = VcsProjectLog.getInstance(project).logManager
  if (logManager == null) {
    ProjectLevelVcsManager.getInstance(project).runAfterInitialization {
      runInEdt { VcsProjectLog.runWhenLogIsReady(project) { manager -> action(manager) } }
    }
  }
  else {
    action(logManager)
  }
}

fun IntRange.limitedBy(limit: IntRange): IntRange = max(first, limit.first)..min(last, limit.last)

fun IntRange.expandBy(delta: Int): IntRange = (first - delta)..(last + delta)

operator fun IntRange.contains(value: IntRange): Boolean = value.first in this && value.last in this