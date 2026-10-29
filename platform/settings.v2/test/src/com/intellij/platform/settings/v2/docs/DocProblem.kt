// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.docs

import java.nio.file.Path

/**
 * Holds one problem that a documentation check found.
 *
 * @property file the file or the folder that holds the problem.
 * @property line the 1-based line of the problem, or `0` when the problem belongs to the whole file or folder.
 * @property message the description of the problem.
 */
data class DocProblem(val file: Path, val line: Int, val message: String) {
  override fun toString(): String = if (line > 0) "$file:$line: $message" else "$file: $message"
}
