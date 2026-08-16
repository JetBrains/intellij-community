// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.report

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.xml.NanoXmlUtil
import org.jetbrains.annotations.VisibleForTesting
import java.io.UncheckedIOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Heuristic detection of [JUnit-style XML](https://github.com/testmoapp/junitxml) reports.
 *
 * Checks whether the first element is `testsuite` or `testsuites`.
 */
object JUnitReportXmlDetector {
  internal fun looksLikeJUnitReportFile(project: Project, file: VirtualFile): Boolean =
    project.service<DetectionCache>().getOrScheduleDetection(file) == true

  private fun detectJUnitReportFile(file: VirtualFile): Boolean {
    if (!file.isValid || file.isDirectory || !file.name.endsWith(".xml", ignoreCase = true)) return false
    return try {
      looksLikeJUnitReportXml(file)
    }
    catch (_: UncheckedIOException) {
      false
    }
  }

  @VisibleForTesting
  @JvmStatic
  fun looksLikeJUnitReportXml(file: VirtualFile): Boolean {
    val rootTagName = NanoXmlUtil.parseHeader(file).rootTagLocalName
    return rootTagName == "testsuite" || rootTagName == "testsuites"
  }

  /**
   * Handle the detection request, and cache detection results.
   */
  @Service(Service.Level.PROJECT)
  private class DetectionCache(private val project: Project) : Disposable {
    private val detectionCache = CollectionFactory.createConcurrentWeakMap<VirtualFile, CachedDetection>()
    private val pendingRequests = ConcurrentHashMap.newKeySet<DetectionRequest>()

    fun getOrScheduleDetection(file: VirtualFile): Boolean? {
      return getCachedDetection(file).also { cachedDetection ->
        if (cachedDetection == null) {
          scheduleDetection(file)
        }
      }
    }

    private fun getCachedDetection(file: VirtualFile): Boolean? {
      if (!isCandidate(file)) return false

      val cached = detectionCache[file] ?: return null
      if (cached.revision == (file.modificationStamp to file.length)) return cached.matches

      detectionCache.remove(file, cached)
      return null
    }

    private fun scheduleDetection(file: VirtualFile) {
      if (getCachedDetection(file) != null) return

      val request = DetectionRequest(file, file.modificationStamp to file.length)
      if (!pendingRequests.add(request)) return

      ReadAction.nonBlocking<Boolean> {
        detectJUnitReportFile(file)
      }
        .expireWith(this)
        .finishOnUiThread(ModalityState.any()) { matches ->
          pendingRequests.remove(request)
          if (!project.isDisposed && file.isValid) {
            // Cache only results produced for the current revision.
            if (request.revision == (file.modificationStamp to file.length)) {
              detectionCache[file] = CachedDetection(request.revision, matches)
            }
            // Refresh after a stale result too, so the provider can schedule detection for the current revision.
            EditorNotifications.getInstance(project).updateNotifications(file)
          }
        }
        .submit(AppExecutorUtil.getAppExecutorService())
    }

    override fun dispose() {
      detectionCache.clear()
      pendingRequests.clear()
    }

    private fun isCandidate(file: VirtualFile): Boolean =
      file.isValid && !file.isDirectory && file.name.endsWith(".xml", ignoreCase = true)

    /**
     * Model the result of the detection.
     */
    private data class CachedDetection(val revision: Pair<Long, Long>, val matches: Boolean)

    /**
     * Model a request for a specific file at specific revision.
     */
    private data class DetectionRequest(val file: VirtualFile, val revision: Pair<Long, Long>)
  }
}
