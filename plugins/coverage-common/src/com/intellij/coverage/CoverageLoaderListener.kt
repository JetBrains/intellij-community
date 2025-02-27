// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.rt.coverage.data.ProjectData
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import java.io.File

@ApiStatus.Internal
interface CoverageLoadListener {

  companion object {
    @Topic.ProjectLevel
    @JvmField
    val COVERAGE_TOPIC: Topic<CoverageLoadListener> = Topic(CoverageLoadListener::class.java, Topic.BroadcastDirection.NONE)
  }

  fun coverageLoadingStarted(coverageFile: File)

  fun reportCoverageLoaded(result: LoadCoverageResult, coverageFile: File)

  fun reportCoverageLoadException( message: String, e: Exception, coverageFile: File)
}

class CoverageLoadErrorReporter(
  private val coverageLoaderListener: CoverageLoadListener,
  private val reportFile: File)
{
  fun reportError(message: String, e: Exception) {
    coverageLoaderListener.reportCoverageLoadException(message, e, reportFile)
  }
}

sealed class LoadCoverageResult(val projectData: ProjectData?)

class SuccessLoadCoverageResult(projectData: ProjectData) : LoadCoverageResult(projectData)

class FailedLoadCoverageResult(projectData: ProjectData?, val message: String, val exception: Exception) : LoadCoverageResult(projectData)