// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import com.intellij.util.ExceptionUtil
import com.intellij.util.io.sanitizeFileName
import java.io.IOException
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Handler
import java.util.logging.LogRecord
import kotlin.io.path.name

/**
 * Handler for logging attachments of [ExceptionWithAttachments] to log folder.
 */
internal class AttachmentHandler(private val logPath: Path ) : Handler() {
  override fun publish(record: LogRecord) {
    if (!isLoggable(record)) return

    val t = record.thrown ?: return
    val ewas = ExceptionUtil.findCauseAndSuppressed(t, ExceptionWithAttachments::class.java).ifEmpty { return }

    val dirWithLoggedAttachments = if (ewas.singleOrNull() == t) {
      writeSingleEwa(ewas.single(), t)
    }
    else {
      writeEwas(ewas, t)
    }

    if (dirWithLoggedAttachments != null) {
      log.info("Saving attachments of [${record.loggerName}] ${t.javaClass.name} to $dirWithLoggedAttachments")
    }
  }

  private fun writeEwas(ewas: MutableList<ExceptionWithAttachments>, t: Throwable): Path? {
    val attachmentsDir = prepareDir(logPath, t) ?: return null

    // store all EWAs directly in the main folder, prefixing files with the EWA index
    var index = 1
    for (ewa in ewas) {
      val attachments = ewa.attachments.ifEmpty { continue }
      writeEwa(attachmentsDir, ewa, "$index-", attachments)
      index++
    }

    if (index == 1) {
      // no attachments saved => delete the empty directory
      try {
        Files.deleteIfExists(attachmentsDir)
      }
      catch (_: IOException) {}
      return null
    }

    // Keep the overall throwable stacktrace for context
    writeStacktrace(attachmentsDir.resolve("stacktrace.txt"), t)

    return attachmentsDir
  }

  private fun writeSingleEwa(ewa: ExceptionWithAttachments, t: Throwable): Path? {
    val attachments = ewa.attachments.ifEmpty { return null }
    val attachmentsDir = prepareDir(logPath, t) ?: return null
    writeEwa(attachmentsDir, ewa, "", attachments)
    return attachmentsDir
  }

  private fun writeEwa(
    attachmentsDir: Path,
    ewa: ExceptionWithAttachments,
    prefix: String,
    attachments: Array<Attachment>,
  ) {
    writeIndexedEwaStacktrace(attachmentsDir, ewa, prefix)
    writeAttachments(attachmentsDir, attachments, prefix)
  }

  private fun writeIndexedEwaStacktrace(dir: Path, ewa: ExceptionWithAttachments, prefix: String) {
    if (ewa is Throwable) {
      val stacktraceFile = dir.resolve("${prefix}stacktrace.txt")
      writeStacktrace(stacktraceFile, ewa)
    }
    else {
      try {
        Files.write(dir.resolve("${prefix}ewa.txt"), ewa.toString().toByteArray())
      }
      catch (_: IOException) {
      }
    }
  }

  private fun prepareDir(logPath: Path, t: Throwable): Path? {
    val baseDir = logPath.parent.resolve("attachments")
    val dirName = prepareDirName(t)
    val attachmentsDir = baseDir.resolve(dirName)

    try {
      // Ensure base directory exists
      Files.createDirectories(baseDir)

      // Prune oldest groups to keep room for a new one
      pruneOldAttachmentGroups(baseDir, MAX_ATTACHMENT_GROUPS)

      // Create new group directory
      Files.createDirectories(attachmentsDir)
    }
    catch (_: IOException) {
      return null
    }

    return attachmentsDir
  }

  private fun prepareDirName(t: Throwable): String {
    val now = ZonedDateTime.now()
    val errorAbbr = inferErrorAbbreviation(t)
    return "attachments-" + dateFormat.format(now) + "-" + errorAbbr
  }

  private fun writeStacktrace(stacktraceFile: Path, t: Throwable) {
    try {
      PrintWriter(Files.newBufferedWriter(stacktraceFile)).use {
        t.printStackTrace(it)
      }
    }
    catch (_: IOException) {
      // ignore errors for individual files
    }
  }

  override fun flush() {
    // no-op
  }

  override fun close() {
    // no-op
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

  private fun writeAttachments(dir: Path, attachments: Array<Attachment>, prefix: String) {
    val usedNames = HashSet<String>()
    for (attachment in attachments) {
      val base = sanitizeFileName(attachment.name.ifEmpty { "attachment" })
      val fileName = uniqueName(prefix + base, usedNames)
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
   * Keep at most [maxGroups] attachment groups under [baseDir]. If the number of existing groups
   * is >= [maxGroups], delete the oldest ones to make room for a new group.
   */
  @Suppress("SameParameterValue")
  private fun pruneOldAttachmentGroups(baseDir: Path, maxGroups: Int) {
    val entries = try {
      val directoryStream = Files.newDirectoryStream(baseDir) { path ->
        Files.isDirectory(path) && path.name.startsWith("attachments-")
      }

      directoryStream.use { ds ->
        ds.toMutableList()
      }
    }
    catch (_: IOException) {
      return
    }

    val toDeleteCount = entries.size - maxGroups + 1 // make room for the incoming group
    if (toDeleteCount <= 0) return

    // Sort by directory name which begins with timestamp in yy-MM-dd-HH-mm-ss format -> lexicographical order matches time order
    entries.sortBy { it.fileName.toString() }

    for (i in 0 until toDeleteCount) {
      deleteRecursively(entries[i])
    }
  }

  private fun deleteRecursively(path: Path) {
    try {
      // Delete files first, then directories
      Files.walk(path)
        .sorted(Comparator.reverseOrder())
        .forEach { p ->
          try {
            Files.deleteIfExists(p)
          }
          catch (_: IOException) {
            // ignore deletion errors
          }
        }
    }
    catch (_: IOException) {
      // ignore traversal errors
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

private const val MAX_ATTACHMENT_GROUPS: Int = 100
private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yy-MM-dd-HH-mm-ss")
private val uppercaseMatcher = Regex("(?=[A-Z])")

private val log = logger<AttachmentHandler>()