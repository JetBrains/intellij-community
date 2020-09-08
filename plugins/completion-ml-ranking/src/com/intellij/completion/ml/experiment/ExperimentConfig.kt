// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.experiment

data class ExperimentConfig(val version: Int,
                            val groups: List<ExperimentGroupConfig>,
                            val languages: List<ExperimentLanguageConfig>) {
  companion object {
    private val DISABLED_EXPERIMENT = ExperimentConfig(version = 2, groups = emptyList(), languages = emptyList())
    fun disabledExperiment(): ExperimentConfig = DISABLED_EXPERIMENT
  }
}

data class ExperimentGroupConfig(val number: Int,
                                 val description: String,
                                 val useMLRanking: Boolean,
                                 val showArrows: Boolean,
                                 val calculateFeatures: Boolean)

data class ExperimentLanguageConfig(val id: String,
                                    val experimentBucketsCount: Int,
                                    val includeGroups: List<Int>)