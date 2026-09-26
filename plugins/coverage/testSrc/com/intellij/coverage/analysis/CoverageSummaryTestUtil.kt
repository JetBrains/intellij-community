// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis

import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking

@Suppress("unused")
internal object CoverageSummaryTestUtil {
  @JvmStatic
  @Suppress("RAW_RUN_BLOCKING")
  fun build(suite: CoverageSuitesBundle, project: Project, collector: CoverageInfoCollector) {
    runBlocking {
      JavaCoverageSummaryBuilder.build(suite, project, collector)
    }
  }
}
