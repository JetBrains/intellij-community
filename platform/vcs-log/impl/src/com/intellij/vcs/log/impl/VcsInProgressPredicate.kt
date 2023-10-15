// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.observable.ActivityInProgressPredicate
import com.intellij.openapi.project.Project

class VcsInProgressPredicate : ActivityInProgressPredicate {

  override val presentableName: String = "vcs-log"

  override suspend fun isInProgress(project: Project): Boolean {
    return project.serviceAsync<VcsInProgressService>().isInProgress()
  }

  override suspend fun awaitConfiguration(project: Project) {
    return project.serviceAsync<VcsInProgressService>().awaitConfiguration()
  }
}