// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.leakDetection

import com.intellij.util.io.DigestUtil
import java.nio.charset.StandardCharsets

// The name part of `ProjectImpl.toString()`: `Project(name=<name>, containerState=...`
private val PROJECT_TO_STRING_NAME_REGEX = Regex("""Project\(name=(.*?), containerState=""")

/** Returns a stable replacement of a project name: `project-` and the first 8 hex digits of the SHA-256 of the name. */
internal fun projectNameHash(name: String): String {
  return "project-" + DigestUtil.sha256Hex(name.toByteArray(StandardCharsets.UTF_8)).take(8)
}

/**
 * Replaces each project name in [text] with [projectNameHash].
 * The names are [knownNames] and the names that `ProjectImpl.toString()` wrote into [text].
 * A name is replaced only as a whole token, so `idea` in `com.intellij.idea` stays.
 */
internal fun maskProjectNames(text: String, knownNames: Collection<String>): String {
  val names = LinkedHashSet<String>()
  knownNames.filterTo(names) { it.isNotBlank() }
  PROJECT_TO_STRING_NAME_REGEX.findAll(text).mapTo(names) { it.groupValues[1] }
  names.removeIf { it.isBlank() }
  if (names.isEmpty()) return text

  // Longer names go first, so a name that contains a shorter name is replaced as a whole.
  val alternatives = names.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
  val nameRegex = Regex("""(?<![\w.$])(?:$alternatives)(?![\w$])""")
  return nameRegex.replace(text) { projectNameHash(it.value) }
}
