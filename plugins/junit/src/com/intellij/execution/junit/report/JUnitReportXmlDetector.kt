// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.report

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.LimitedInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Heuristic detection of [JUnit-style XML](https://github.com/testmoapp/junitxml) reports.
 *
 * Reads at most [MAX_PREFIX_BYTES] from the start of the file (never the full file),
 * skips XML prolog constructs and comments, then checks whether the first element is
 * `testsuite` or `testsuites` (including prefixed names such as `ns:testsuite`).
 */
object JUnitReportXmlDetector {
  const val MAX_PREFIX_BYTES: Int = 256 * 1024

  private val DETECTION_CACHE_KEY = Key.create<DetectionCache>("junit.report.xml.detection")
  private val DEFER_ATTEMPT_KEY = Key.create<AtomicInteger>("junit.report.xml.detection.defer")

  private data class DetectionCache(val stamp: Long, val length: Long, val match: Boolean)

  enum class JUnitReportXmlDetection {
    MATCH,
    NO_MATCH,
    /** Content not ready yet; caller may schedule a notification refresh. */
    DEFER_NOTIFICATION_UPDATE,
  }

  @JvmStatic
  fun looksLikeJUnitReportFile(file: VirtualFile): Boolean =
    detectJUnitReportXmlFile(file, allowDeferredRefresh = false, project = null) == JUnitReportXmlDetection.MATCH

  /**
   * Clears cached detection / defer counters so the next [detectJUnitReportXmlFile] run reads the file again.
   * Used when an editor is opened so the notification layer can observe freshly available content.
   */
  @JvmStatic
  fun invalidateEditorDetectionState(file: VirtualFile) {
    file.putUserData(DEFER_ATTEMPT_KEY, null)
    file.putUserData(DETECTION_CACHE_KEY, null)
  }

  /**
   * @param allowDeferredRefresh when true and [project] non-null, schedules [EditorNotifications.updateNotifications]
   * if the file prefix could not be read yet (e.g. content still loading).
   */
  @JvmStatic
  fun detectJUnitReportXmlFile(
    file: VirtualFile,
    allowDeferredRefresh: Boolean,
    project: Project?,
  ): JUnitReportXmlDetection {
    if (!file.isValid || file.isDirectory) return JUnitReportXmlDetection.NO_MATCH
    if (!file.name.endsWith(".xml", ignoreCase = true)) return JUnitReportXmlDetection.NO_MATCH

    val stamp = file.modificationStamp
    val length = file.length
    file.getUserData(DETECTION_CACHE_KEY)?.let { cached ->
      if (cached.stamp == stamp && cached.length == length) {
        return if (cached.match) JUnitReportXmlDetection.MATCH else JUnitReportXmlDetection.NO_MATCH
      }
    }

    val prefix = try {
      file.inputStream.use { raw ->
        LimitedInputStream(raw, MAX_PREFIX_BYTES).use { limited ->
          limited.readAllBytes()
        }
      }
    }
    catch (_: IOException) {
      return handleIncompleteOrIoFailure(file, stamp, length, allowDeferredRefresh, project)
    }

    if (prefix.isEmpty() && length > 0) {
      return handleIncompleteOrIoFailure(file, stamp, length, allowDeferredRefresh, project)
    }

    file.putUserData(DEFER_ATTEMPT_KEY, null)

    val match = looksLikeJUnitReportXml(prefix, prefix.size)
    file.putUserData(DETECTION_CACHE_KEY, DetectionCache(stamp, length, match))
    return if (match) JUnitReportXmlDetection.MATCH else JUnitReportXmlDetection.NO_MATCH
  }

  private fun handleIncompleteOrIoFailure(
    file: VirtualFile,
    stamp: Long,
    length: Long,
    allowDeferredRefresh: Boolean,
    project: Project?,
  ): JUnitReportXmlDetection {
    if (allowDeferredRefresh && project != null && !project.isDisposed) {
      val attempts = file.getUserData(DEFER_ATTEMPT_KEY) ?: AtomicInteger(0).also { file.putUserData(DEFER_ATTEMPT_KEY, it) }
      if (attempts.incrementAndGet() <= 12) {
        file.putUserData(DETECTION_CACHE_KEY, null)
        scheduleNotificationRefresh(project, file)
        return JUnitReportXmlDetection.DEFER_NOTIFICATION_UPDATE
      }
      file.putUserData(DEFER_ATTEMPT_KEY, null)
      file.putUserData(DETECTION_CACHE_KEY, DetectionCache(stamp, length, false))
      return JUnitReportXmlDetection.NO_MATCH
    }

    if (length > 0) {
      file.putUserData(DETECTION_CACHE_KEY, null)
    }
    else {
      file.putUserData(DETECTION_CACHE_KEY, DetectionCache(stamp, length, false))
    }
    return JUnitReportXmlDetection.NO_MATCH
  }

  private fun scheduleNotificationRefresh(project: Project, file: VirtualFile) {
    AppExecutorUtil.getAppScheduledExecutorService().schedule(
      {
        ApplicationManager.getApplication().invokeLater {
          if (!project.isDisposed && file.isValid) {
            EditorNotifications.getInstance(project).updateNotifications(file)
          }
        }
      },
      200L,
      TimeUnit.MILLISECONDS,
    )
  }

  /**
   * @param contentPrefix first bytes of the file (UTF-8 or ASCII); only indices `< length` are read
   */
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
        i < length && contentPrefix[i] == '<'.code.toByte() && i + 1 < length && contentPrefix[i + 1] == '/'.code.toByte() -> {
          val end = indexOfByte(contentPrefix, i + 2, length, '>'.code.toByte())
          i = if (end < 0) length else end + 1
        }
        i < length && contentPrefix[i] == '<'.code.toByte() -> {
          i++
          val nameStart = i
          while (i < length) {
            val b = contentPrefix[i].toInt() and 0xFF
            if (b <= ' '.code || b == '>'.code || b == '/'.code) break
            i++
          }
          if (nameStart >= i) return false
          return isJUnitRootSuiteElement(contentPrefix, nameStart, i)
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

  private val TESTSUITE = "testsuite".toByteArray(StandardCharsets.US_ASCII)
  private val TESTSUITES = "testsuites".toByteArray(StandardCharsets.US_ASCII)

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
}
