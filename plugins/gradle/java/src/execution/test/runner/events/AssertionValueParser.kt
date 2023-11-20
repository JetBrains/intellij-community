// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events

import com.intellij.openapi.util.text.StringUtil

object AssertionValueParser {

  private val OPENTEST4J_FILE_INFO_EXTRACTOR =
    "FileInfo\\[path='(?<path>.+)', contents containing \\d+ bytes]"
      .toRegex()

  @JvmStatic
  fun parse(assertionText: String): String? {
    val value = StringUtil.convertLineSeparators(assertionText)
    val matchesResult = OPENTEST4J_FILE_INFO_EXTRACTOR.matchEntire(value) ?: return null
    val path = matchesResult.groups["path"] ?: return null
    return path.value
  }
}