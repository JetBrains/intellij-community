// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import java.util.*
import java.util.regex.Pattern

object GradleTaskClassifier {

  private const val OTHER: String = "other"
  private val PATTERN: Pattern = Pattern.compile("(?=\\p{Upper})")
  private val KNOWN_TASK_WORDS: Set<String> = setOf(
    "assemble",
    "build",
    "check",
    "checkstyle",
    "classes",
    "clean",
    "compile",
    "groovy",
    "init",
    "lint",
    "pmd",
    "jar",
    "java",
    "javadoc",
    "jmh",
    "kotlin",
    "process",
    "resources",
    "scala",
    "test",
    "war",
    "wrapper"
  )

  @JvmStatic
  fun classifyTaskName(name: String?): String {
    if (name == null) {
      return OTHER
    }
    val particles = name.split(PATTERN)
      .map { it.lowercase() }
      .filter { KNOWN_TASK_WORDS.contains(it) }
    if (particles.isEmpty()) {
      return OTHER
    }
    return particles.joinToConventionalNaming()
  }

  @JvmStatic
  fun isClassified(name: String): Boolean = OTHER.equals(name, ignoreCase = true)
                                            || name.split(PATTERN).all { KNOWN_TASK_WORDS.contains(it.lowercase()) }

  private fun List<String>.joinToConventionalNaming(): String {
    if (size == 1) {
      return first()
    }
    val result = StringBuilder(first())
    for (index in 1 until size) {
      val word = get(index).replaceFirstChar { it.titlecase(Locale.getDefault()) }
      result.append(word)
    }
    return result.toString()
  }
}
