// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tracing.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Service(Service.Level.APP)
internal class TracingService {
  companion object {
    fun getInstance() : TracingService {
      return ApplicationManager.getApplication().getService(TracingService::class.java)
    }

    fun createPath(kind: TraceKind): Path {
      val tracesDirPath = getTracesDirPath()
      val subDir = when (kind) {
        TraceKind.Jps -> JPS_TRACE_DIR_NAME
        TraceKind.Ide -> IDE_TRACE_DIR_NAME
        TraceKind.Merged -> MERGED_TRACE_DIR_NAME
      }
      return tracesDirPath.resolve(subDir).resolve(getNewTraceFileName())
    }

    private val fileNameDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    private fun getNewTraceFileName() = "trace_${LocalDateTime.now().format(fileNameDateTimeFormatter)}.json"

    private fun getTracesDirPath() : Path {
      return Paths.get(PathManager.getHomePath()).resolve(COMMON_TRACING_DIR_NAME)
    }

    private const val COMMON_TRACING_DIR_NAME: String = "tracing"
    private const val IDE_TRACE_DIR_NAME: String = "ide"
    private const val JPS_TRACE_DIR_NAME: String = "jps"
    private const val MERGED_TRACE_DIR_NAME: String = "merged"
  }

  private val lock = Any()
  private var traces = ArrayList<Path>()
  private var jpsTrace: Path? = null

  fun isTracingEnabled() : Boolean {
    return synchronized(lock) {
      TracingPersistentStateComponent.getInstance().state.isEnabled
    }
  }

  fun setTracingEnabled(enabled: Boolean) {
    synchronized(lock) {
      TracingPersistentStateComponent.getInstance().state.isEnabled = enabled
      if (!enabled) {
        traces.clear()
      }
    }
  }

  fun registerIdeTrace(filePath: Path) {
    synchronized(lock) {
      traces.add(filePath)
    }
  }

  fun registerJpsTrace(filePath: Path) {
    synchronized(lock) {
      bindJpsTraceIfExistsToCurrentSession()
      jpsTrace = filePath
    }
  }

  fun bindJpsTraceIfExistsToCurrentSession() {
    synchronized(lock) {
      val jpsTrace = jpsTrace
      if (jpsTrace != null) {
        traces.add(jpsTrace)
        this.jpsTrace = null
      }
    }
  }

  fun drainFilesToMerge() : List<Path> {
    synchronized(lock) {
      val tracesToMerge = traces
      traces = ArrayList()
      return tracesToMerge
    }
  }

  fun clearPathsToMerge() {
    synchronized(lock) {
      traces.clear()
    }
  }

  enum class TraceKind {
    Jps,
    Ide,
    Merged,
  }
}