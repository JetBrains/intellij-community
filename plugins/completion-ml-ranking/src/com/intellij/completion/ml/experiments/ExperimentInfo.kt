package com.intellij.completion.ml.experiments

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