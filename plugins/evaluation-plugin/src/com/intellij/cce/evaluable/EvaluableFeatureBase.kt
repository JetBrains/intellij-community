// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable

import com.intellij.cce.report.BasicFileReportGenerator
import com.intellij.cce.report.FileReportGenerator
import com.intellij.cce.report.GeneratorDirectories
import com.intellij.cce.workspace.storages.FeaturesStorage
import com.intellij.cce.workspace.storages.FullLineLogsStorage

abstract class EvaluableFeatureBase<T : EvaluationStrategy>(override val name: String) : EvaluableFeature<T> {

  override fun getFileReportGenerator(filterName: String,
                                      comparisonFilterName: String,
                                      featuresStorages: List<FeaturesStorage>,
                                      fullLineStorages: List<FullLineLogsStorage>,
                                      dirs: GeneratorDirectories): FileReportGenerator =
    BasicFileReportGenerator(filterName, comparisonFilterName, featuresStorages, dirs)
}
