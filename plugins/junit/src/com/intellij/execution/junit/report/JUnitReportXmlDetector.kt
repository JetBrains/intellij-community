// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.report

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.LimitedInputStream
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * Heuristic detection of [JUnit-style XML](https://github.com/testmoapp/junitxml) reports.
 *
 * Reads at most [MAX_PREFIX_BYTES] from the start of the file (never the full file),
 * skips XML prolog constructs and comments, then checks whether the first element is
 * `testsuite` or `testsuites` (including prefixed names such as `ns:testsuite`).
 */
object JUnitReportXmlDetector {
  const val MAX_PREFIX_BYTES: Int = 256 * 1024
  private val TESTSUITE = "testsuite".toByteArray(StandardCharsets.US_ASCII)
  private val TESTSUITES = "testsuites".toByteArray(StandardCharsets.US_ASCII)

  @JvmStatic
  fun looksLikeJUnitReportFile(file: VirtualFile): Boolean {
    val project = ProjectLocator.getInstance().guessProjectForFile(file) ?: return false
    return looksLikeJUnitReportFile(project, file)
  }

  internal fun looksLikeJUnitReportFile(project: Project, file: VirtualFile): Boolean =
    project.service<DetectionCache>().getOrScheduleDetection(file) == true

  private fun detectJUnitReportFile(file: VirtualFile): Boolean {
    if (!file.isValid || file.isDirectory || !file.name.endsWith(".xml", ignoreCase = true)) return false
    val prefix = try {
      file.inputStream.use { raw ->
        LimitedInputStream(raw, MAX_PREFIX_BYTES).use { limited ->
          limited.readAllBytes()
        }
      }
    }
    catch (_: IOException) {
      return false
    }

    return looksLikeJUnitReportXml(prefix, prefix.size)
  }

  /**
   * @param contentPrefix first bytes of the file (UTF-8 or ASCII); only indices `< length` are read
   */
  @VisibleForTesting
  @JvmStatic
  fun looksLikeJUnitReportXml(contentPrefix: ByteArray, length: Int): Boolean {
    if (length <= 0) return false
    var i = 0
    if (length >= 3 &&
        contentPrefix[0] == CharsetToolkit.UTF8_BOM[0] &&
        contentPrefix[1] == CharsetToolkit.UTF8_BOM[1] &&
        contentPrefix[2] == CharsetToolkit.UTF8_BOM[2]) {
      i = 3
    }

    while (i < length) {
      while (i < length && contentPrefix[i].toInt() and 0xFF <= ' '.code) i++
      if (i >= length) return false

      when {
        startsWith(contentPrefix, i, length, "<!--") -> {
          val end = indexOf(contentPrefix, i + 4, length, "-->".toByteArray(StandardCharsets.US_ASCII))
          i = if (end < 0) length else end + 3
        }
        startsWith(contentPrefix, i, length, "<?") -> {
          val end = indexOf(contentPrefix, i + 2, length, "?>".toByteArray(StandardCharsets.US_ASCII))
          i = if (end < 0) length else end + 2
        }
        startsWith(contentPrefix, i, length, "<!DOCTYPE") -> {
          i = skipDOCTYPE(contentPrefix, i, length)
        }
        startsWith(contentPrefix, i, length, "<![CDATA[") -> {
          val end = indexOf(contentPrefix, i + 9, length, "]]>".toByteArray(StandardCharsets.US_ASCII))
          i = if (end < 0) length else end + 3
        }
        contentPrefix[i] == '<'.code.toByte() && i + 1 < length && contentPrefix[i + 1] == '/'.code.toByte() -> {
          val end = indexOfByte(contentPrefix, i + 2, length, '>'.code.toByte())
          i = if (end < 0) length else end + 1
        }
        contentPrefix[i] == '<'.code.toByte() -> {
          i++
          val nameStart = i
          while (i < length) {
            val b = contentPrefix[i].toInt() and 0xFF
            if (b <= ' '.code || b == '>'.code || b == '/'.code) break
            i++
          }
          return nameStart < i && isJUnitRootSuiteElement(contentPrefix, nameStart, i)
        }
        else -> {
          val nextLt = indexOfByte(contentPrefix, i, length, '<'.code.toByte())
          i = if (nextLt < 0) length else nextLt
        }
      }
    }
    return false
  }

  private fun isJUnitRootSuiteElement(buf: ByteArray, nameStart: Int, nameEnd: Int): Boolean {
    var localStart = nameStart
    for (p in nameStart until nameEnd) {
      if (buf[p] == ':'.code.toByte()) {
        localStart = p + 1
        break
      }
    }
    val localLen = nameEnd - localStart
    return regionEquals(buf, localStart, localLen, TESTSUITE) ||
           regionEquals(buf, localStart, localLen, TESTSUITES)
  }

  private fun regionEquals(buf: ByteArray, start: Int, len: Int, ascii: ByteArray): Boolean {
    if (len != ascii.size) return false
    for (j in ascii.indices) {
      if (buf[start + j] != ascii[j]) return false
    }
    return true
  }

  private fun skipDOCTYPE(buf: ByteArray, from: Int, length: Int): Int {
    var i = from
    var quote: Byte? = null
    while (i < length) {
      val b = buf[i]
      when {
        quote != null -> {
          if (b == quote) quote = null
        }
        b == '\''.code.toByte() || b == '"'.code.toByte() -> quote = b
        b == '>'.code.toByte() -> return i + 1
      }
      i++
    }
    return length
  }

  private fun startsWith(buf: ByteArray, offset: Int, length: Int, s: String): Boolean {
    val bytes = s.toByteArray(StandardCharsets.US_ASCII)
    if (offset + bytes.size > length) return false
    for (j in bytes.indices) {
      if (buf[offset + j] != bytes[j]) return false
    }
    return true
  }

  private fun indexOf(buf: ByteArray, from: Int, length: Int, needle: ByteArray): Int {
    outer@ for (i in from..length - needle.size) {
      for (j in needle.indices) {
        if (buf[i + j] != needle[j]) continue@outer
      }
      return i
    }
    return -1
  }

  private fun indexOfByte(buf: ByteArray, from: Int, length: Int, b: Byte): Int {
    for (i in from until length) {
      if (buf[i] == b) return i
    }
    return -1
  }

  /**
   * Handle the detection request, and cache detection results.
   */
  @Service(Service.Level.PROJECT)
  private class DetectionCache(private val project: Project) : Disposable {
    private val detectionCache = ConcurrentHashMap<VirtualFile, CachedDetection>()
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
