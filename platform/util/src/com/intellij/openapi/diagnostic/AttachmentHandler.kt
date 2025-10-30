// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import com.intellij.util.io.sanitizeFileName
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.IdentityHashMap
import java.util.logging.Filter
import java.util.logging.Handler
import java.util.logging.LogRecord
import kotlin.collections.ArrayDeque

internal class AttachmentHandler(
  private val logPath: Path,
  logFilter: Filter?,
) : Handler() {
  init {
    if (logFilter != null) {
      filter = logFilter
    }
  }

  override fun publish(record: LogRecord) {
    if (!isLoggable(record)) return

    val t = record.thrown ?: return
    val ewas = findWithAttachments(t).ifEmpty { return }

    val hasAnyAttachments = ewas.any { it.attachments.isNotEmpty() }
    if (!hasAnyAttachments) return

    val attachmentsDir = prepareDir(logPath, record) ?: return

    writeStacktrace(attachmentsDir, t)

    if (ewas.singleOrNull() == t) {
      // Single EWA: write files directly into the main folder (no nested folder)
      writeAttachments(attachmentsDir, ewas.single().attachments)
    }
    else {
      // Multiple EWAs: create a separate subfolder for each
      for ((index, ewa) in ewas.withIndex()) {
        if (ewa.attachments.isEmpty()) continue
        val subDir = prepareEwaDir(ewa, attachmentsDir, index) ?: continue
        writeEwaStacktrace(subDir, ewa)
        writeAttachments(subDir, ewa.attachments)
      }
    }
  }

  private fun prepareEwaDir(ewa: ExceptionWithAttachments, attachmentsDir: Path, index: Int): Path? {
    val subDir = attachmentsDir.resolve("ewa-" + (index + 1) + "-" + inferErrorAbbreviation(ewa))
    try {
      Files.createDirectories(subDir)
      return subDir
    }
    catch (_: IOException) {
      return null
    }
  }

  private fun writeEwaStacktrace(subDir: Path, ewa: ExceptionWithAttachments) {
    if (ewa is Throwable) {
      writeStacktrace(subDir, ewa)
    }
    else {
      try {
        Files.write(subDir.resolve("ewa.txt"), ewa.toString().toByteArray())
      }
      catch (_: IOException) {
      }
    }
  }

  private fun prepareDir(logPath: Path, record: LogRecord): Path? {
    val logDir = logPath.parent
    val now = ZonedDateTime.now()
    val errorAbbr = inferErrorAbbreviation(record.thrown)
    val dirName = "attachments-" + dateFormat.format(now) + "-" + errorAbbr
    val attachmentsDir = logDir.resolve(dirName)

    try {
      Files.createDirectories(attachmentsDir)
    }
    catch (_: IOException) {
      return null
    }

    return attachmentsDir
  }

  private fun writeStacktrace(dir: Path, t: Throwable) {
    try {
      val stacktraceFile = dir.resolve("stacktrace.txt")
      val stackTraceBytes = t.stackTraceToString().toByteArray()
      Files.write(stacktraceFile, stackTraceBytes)
    }
    catch (_: IOException) {
      // ignore errors for individual files
    }
  }

  override fun flush() {
    // no-op
  }

  @Throws(SecurityException::class)
  override fun close() {
    // no-op
  }

  private fun findWithAttachments(t: Throwable): List<ExceptionWithAttachments> {
    val result = ArrayList<ExceptionWithAttachments>()
    val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    val deque = ArrayDeque<Throwable>()
    deque.add(t)
    while (deque.isNotEmpty()) {
      val cur = deque.removeFirst()
      if (!visited.add(cur)) continue

      if (cur is ExceptionWithAttachments) {
        result.add(cur)
      }

      // Explore suppressed first to catch siblings as well
      for (s in cur.suppressed) {
        deque.addLast(s)
      }
      cur.cause?.let {
        deque.addLast(it)
      }
    }
    return result
  }

  private fun uniqueName(base: String, used: MutableSet<String>): String {
    val name = base.ifBlank { "attachment" }
    if (used.add(name)) return name
    var i = 1
    while (true) {
      val candidate = "$name.$i"
      if (used.add(candidate)) return candidate
      i++
    }
  }

  private fun writeAttachments(dir: Path, attachments: Array<Attachment>) {
    val usedNames = HashSet<String>()
    for (attachment in attachments) {
      val base = sanitizeFileName(attachment.name.ifEmpty { "attachment" })
      val fileName = uniqueName(base, usedNames)
      val file = dir.resolve(fileName)
      try {
        Files.write(file, attachment.bytes)
      }
      catch (_: IOException) {
        // ignore errors for individual files
      }
    }
  }

  /**
   * Produce a short, filesystem-safe abbreviation for an error/exception class.
   * Examples:
   *  - NullPointerException -> NPE
   *  - IllegalArgumentException -> IAE
   *  - IndexOutOfBoundsException -> IOBE
   *  - ArrayIndexOutOfBoundsException -> AIOOBE
   *  - IOException -> IOE
   *  - OutOfMemoryError -> OOME
   */
  private fun inferErrorAbbreviation(t: Any): String {
    val simple = t.javaClass.simpleName.orEmpty().ifBlank { "error" }

    // Early exit for very short names
    if (simple.length <= 3) return sanitizeFileName(simple).take(48)

    val endsWithException = simple.endsWith("Exception")
    val endsWithError = simple.endsWith("Error")

    val core = when {
      endsWithException -> simple.removeSuffix("Exception")
      endsWithError -> simple.removeSuffix("Error")
      else -> simple
    }

    // Split CamelCase into parts, e.g., ArrayIndexOutOfBounds -> [Array, Index, Out, Of, Bounds]
    val parts = core
      .split(uppercaseMatcher)
      .filter { it.isNotEmpty() }

    val acronym = if (parts.isNotEmpty()) parts.joinToString(separator = "") { it[0].uppercase() } else simple

    val withSuffix = when {
      endsWithException -> acronym + 'E'
      endsWithError -> acronym + 'E' // keep the common convention like OOME
      else -> acronym
    }

    return sanitizeFileName(withSuffix.ifBlank { simple }).take(48)
  }
}

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yy-MM-dd-HH-mm-ss")
private val uppercaseMatcher = Regex("(?=[A-Z])")
