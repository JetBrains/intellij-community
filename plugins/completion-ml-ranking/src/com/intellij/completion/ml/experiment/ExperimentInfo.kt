// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.experiment

import com.intellij.completion.ml.common.CurrentProjectInfo
import com.intellij.openapi.project.Project

data class ExperimentInfo(val inExperiment: Boolean,
                          val version: Int,
                          val shouldRank: Boolean = false,
                          val shouldShowArrows: Boolean = false,
                          val shouldCalculateFeatures: Boolean = false,
                          private val shouldLogElementFeatures: Boolean = false) {

  fun shouldLogSessions(project: Project): Boolean = inExperiment || CurrentProjectInfo.getInstance(project).isIdeaProject

  fun shouldLogElementFeatures(project: Project): Boolean =
    !inExperiment || shouldCalculateFeatures && (shouldLogElementFeatures || CurrentProjectInfo.getInstance(project).isIdeaProject)
}