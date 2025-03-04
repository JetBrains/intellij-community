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

  fun reportCoverageLoadException(reason: String, coverageFile: File, e: Exception? = null)
}

interface CoverageLoadErrorReporter {
  fun reportError(reason: String)
  fun reportError(e: Exception)
  fun reportWarning(reason: String, e: Exception? = null)
  fun reportWarning(e: Exception)
}

class DummyCoverageLoadErrorReporter: CoverageLoadErrorReporter {
  override fun reportError(reason: String) {}
  override fun reportError(e: Exception) {}
  override fun reportWarning(reason: String, e: Exception?) {}
  override fun reportWarning(e: Exception) {}
}

class CoverageLoadErrorReporterImplementation(
  private val coverageLoaderListener: CoverageLoadListener,
  private val reportFile: File
): CoverageLoadErrorReporter {
  override fun reportError(reason: String): Unit = coverageLoaderListener.reportCoverageLoadException(reason, reportFile)
  override fun reportError(e: Exception): Unit = coverageLoaderListener.reportCoverageLoadException(e.toReason(), reportFile)
  override fun reportWarning(reason: String, e: Exception?): Unit = coverageLoaderListener.reportCoverageLoadException(reason, reportFile, e)
  override fun reportWarning(e: Exception): Unit = coverageLoaderListener.reportCoverageLoadException(e.toReason(), reportFile, e)
}

sealed class LoadCoverageResult(val projectData: ProjectData?)

class SuccessLoadCoverageResult(projectData: ProjectData) : LoadCoverageResult(projectData)

class FailedLoadCoverageResult @JvmOverloads constructor(
  val reason: String,
  val exception: Exception? = null,
  projectData: ProjectData? = null
): LoadCoverageResult(projectData) {
  @JvmOverloads constructor(e: Exception, recordException: Boolean, projectData: ProjectData? = null):
    this(e.toReason(), if (recordException) e else null, projectData)
}

fun Exception.toReason(): String = message ?: javaClass.name