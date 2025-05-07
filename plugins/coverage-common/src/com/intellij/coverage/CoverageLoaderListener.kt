// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.rt.coverage.data.ProjectData
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import java.io.File

@ApiStatus.Internal
interface CoverageLoadingListener {

  companion object {
    @Topic.ProjectLevel
    @JvmField
    val COVERAGE_TOPIC: Topic<CoverageLoadingListener> = Topic(CoverageLoadingListener::class.java, Topic.BroadcastDirection.NONE)
  }

  fun coverageLoadingStarted(coverageFile: File)

  fun reportCoverageLoaded(result: CoverageLoadingResult, coverageFile: File)

  fun reportCoverageLoadException(reason: String, coverageFile: File, e: Exception? = null)
}

interface CoverageLoadErrorReporter {
  fun reportError(reason: String)
  fun reportError(e: Exception)
  fun reportWarning(reason: String, e: Exception? = null)
  fun reportWarning(e: Exception)
}

@ApiStatus.Internal
class DummyCoverageLoadErrorReporter: CoverageLoadErrorReporter {
  override fun reportError(reason: String) {}
  override fun reportError(e: Exception) {}
  override fun reportWarning(reason: String, e: Exception?) {}
  override fun reportWarning(e: Exception) {}
}

internal class CoverageLoadErrorReporterImplementation(
  private val coverageLoaderListener: CoverageLoadingListener,
  private val reportFile: File
): CoverageLoadErrorReporter {
  override fun reportError(reason: String): Unit = coverageLoaderListener.reportCoverageLoadException(reason, reportFile)
  override fun reportError(e: Exception): Unit = coverageLoaderListener.reportCoverageLoadException(e.toReason(), reportFile)
  override fun reportWarning(reason: String, e: Exception?): Unit = coverageLoaderListener.reportCoverageLoadException(reason, reportFile, e)
  override fun reportWarning(e: Exception): Unit = coverageLoaderListener.reportCoverageLoadException(e.toReason(), reportFile, e)
}

sealed class CoverageLoadingResult(val projectData: ProjectData?)

class SuccessCoverageLoadingResult(projectData: ProjectData) : CoverageLoadingResult(projectData)

class FailedCoverageLoadingResult(
  val reason: String,
  val exception: Exception?,
  projectData: ProjectData?
): CoverageLoadingResult(projectData) {
  constructor(reason: String, exception: Exception?): this(reason, exception, null)
  constructor(reason: String): this(reason, null, null)

  constructor(e: Exception, recordException: Boolean, projectData: ProjectData?):
    this(e.toReason(), if (recordException) e else null, projectData)
  constructor(e: Exception, recordException: Boolean): this(e, recordException, null)
}

private fun Exception.toReason(): String = message ?: javaClass.name