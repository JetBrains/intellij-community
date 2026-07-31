// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis

import com.intellij.coverage.CoverageDataManager
import com.intellij.coverage.CoverageLogger
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.coverage.view.CoverageClassStructure
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlin.system.measureTimeMillis

internal fun createJavaCoverageRenewRequest(
  annotator: JavaCoverageAnnotator,
  project: Project,
  suite: CoverageSuitesBundle,
  dataManager: CoverageDataManager,
): Runnable = Runnable {
  val collector = JavaCoverageAnnotator.JavaCoverageInfoCollector(annotator)
  val timeMs = measureTimeMillis {
    runBlockingCancellable {
      JavaCoverageSummaryBuilder.build(suite, project, collector)
      val structure = CoverageClassStructure(project, annotator, suite)
      Disposer.register(annotator, structure)
      annotator.updateStructure(structure)
    }
    dataManager.triggerPresentationUpdate()
  }

  val annotatedClasses = annotator.classesCoverage.size
  val loadedClasses = suite.coverageData?.classesNumber ?: 0
  CoverageLogger.logReportBuilding(project, timeMs, annotatedClasses, loadedClasses)
}
