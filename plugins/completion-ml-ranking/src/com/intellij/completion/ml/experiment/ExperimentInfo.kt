// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.experiment

data class ExperimentInfo(val inExperiment: Boolean,
                          val version: Int,
                          val shouldRank: Boolean = false,
                          val shouldShowArrows: Boolean = false,
                          val shouldCalculateFeatures: Boolean = false)