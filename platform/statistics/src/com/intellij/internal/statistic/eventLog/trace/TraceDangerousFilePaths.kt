// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.trace

import org.jetbrains.annotations.ApiStatus
import java.util.Locale

@ApiStatus.Internal
fun isDangerousFileForLogging(filePath: String?): Boolean {
  return DangerousFileLoggingMatcher.isDangerousPath(filePath)
}

/**
 * Conservative path-only filtering for analytics logging.
 * The matcher intentionally overblocks and does not touch VFS / filesystem because it can run on EDT.
 */
private object DangerousFileLoggingMatcher {
  private val exactDangerousNames: Array<String> = arrayOf(
    "application.properties",
    "config.ini",
    "secrets.yml",
    "settings.py",
  )

  private val exactDangerousDirectories: Array<String> = arrayOf(
    "_build",
  )

  private val dangerousSuffixes: Array<String> = arrayOf(
    ".7z",
    ".accdb",
    ".agentignore",
    ".aiignore",
    ".aiexclude",
    ".bak",
    ".backup",
    ".bson",
    ".claudeignore",
    ".codeiumignore",
    ".codexignore",
    ".crt",
    ".csv",
    ".cursorignore",
    ".db",
    ".dbf",
    ".dmp",
    ".dump",
    ".feather",
    ".geminiignore",
    ".gz",
    ".json",
    ".key",
    ".llmignore",
    ".mdb",
    ".mongoexport.json",
    ".ndjson",
    ".ods",
    ".parquet",
    ".pem",
    ".pfx",
    ".rar",
    ".sqlite",
    ".sqlite3",
    ".sql",
    ".tar",
    ".tmp",
    ".tsv",
    ".uignore",
    ".xls",
    ".xlsx",
    ".zip",
  )

  private val dangerousSubstrings: Array<String> = arrayOf(
    "auth",
    "billing",
    "certificate",
    "credential",
    "invoice",
    "iban",
    "password",
    "secret",
    "ssn",
    "testdata",
    "token",
  )

  private val dangerousTokens: Array<String> = arrayOf(
    "account",
    "backup",
    "bak",
    "card",
    "cert",
    "config",
    "copy",
    "credit",
    "crt",
    "dsa",
    "identity",
    "key",
    "login",
    "mock",
    "pem",
    "pfx",
    "rsa",
    "setting",
    "settings",
    "tmp",
  )

  fun isDangerousPath(filePath: String?): Boolean {
    if (filePath.isNullOrBlank()) {
      return false
    }

    val normalizedPath = filePath.lowercase(Locale.ROOT)
    var segmentStart = 0
    for (index in 0..normalizedPath.length) {
      val isEnd = index == normalizedPath.length
      val currentChar = if (isEnd) '/' else normalizedPath[index]
      if (currentChar == '/' || currentChar == '\\') {
        if (index > segmentStart && isDangerousSegment(normalizedPath, segmentStart, index)) {
          return true
        }
        segmentStart = index + 1
      }
    }
    return false
  }

  private fun isDangerousSegment(path: String, start: Int, end: Int): Boolean {
    if (isPathNavigationSegment(path, start, end)) {
      return false
    }

    if (path[start] == '.') {
      return true
    }

    if (matchesAnyExactSegment(path, start, end, exactDangerousNames) ||
        matchesAnyExactSegment(path, start, end, exactDangerousDirectories)) {
      return true
    }

    for (suffix in dangerousSuffixes) {
      if (endsWith(path, start, end, suffix)) {
        return true
      }
    }

    for (substring in dangerousSubstrings) {
      if (contains(path, start, end, substring)) {
        return true
      }
    }

    return containsDangerousToken(path, start, end)
  }

  private fun isPathNavigationSegment(path: String, start: Int, end: Int): Boolean {
    val segmentLength = end - start
    return (segmentLength == 1 && path[start] == '.') ||
           (segmentLength == 2 && path[start] == '.' && path[start + 1] == '.')
  }

  private fun matchesAnyExactSegment(path: String, start: Int, end: Int, segments: Array<String>): Boolean {
    for (segment in segments) {
      if (end - start == segment.length && path.regionMatches(start, segment, 0, segment.length)) {
        return true
      }
    }
    return false
  }

  private fun endsWith(path: String, start: Int, end: Int, suffix: String): Boolean {
    val suffixStart = end - suffix.length
    return suffixStart >= start && path.regionMatches(suffixStart, suffix, 0, suffix.length)
  }

  private fun contains(path: String, start: Int, end: Int, substring: String): Boolean {
    val matchStart = path.indexOf(substring, startIndex = start)
    return matchStart >= 0 && matchStart + substring.length <= end
  }

  private fun containsDangerousToken(path: String, start: Int, end: Int): Boolean {
    var tokenStart = -1
    for (index in start..end) {
      val isTokenChar = index < end && path[index].isLetterOrDigit()
      if (isTokenChar) {
        if (tokenStart < 0) {
          tokenStart = index
        }
        continue
      }

      if (tokenStart >= 0) {
        if (matchesAnyExactSegment(path, tokenStart, index, dangerousTokens)) {
          return true
        }
        tokenStart = -1
      }
    }
    return false
  }
}
