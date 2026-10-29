// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.docs

import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

/** Holds the status word of a `Proposed` ADR. */
internal const val PROPOSED_STATUS: String = "Proposed"

/**
 * Holds one decision record (ADR) file of an ADR folder.
 *
 * @property number the `NNNN` number from the file name.
 * @property file the file.
 * @property text the text of the file, read with [readDocText].
 * @property statusLine the 1-based line of the `## Status` heading, or `0` when the file has no such section.
 * @property status the text of the `Status` section, without its heading.
 */
internal class AdrFile(val number: String, val file: Path, val text: String, val statusLine: Int, val status: String) {
  /** Holds the first word of [status], such as [PROPOSED_STATUS]. */
  val statusWord: String
    get() = STATUS_WORD.find(status)?.value.orEmpty()
}

/** Reads every `NNNN-<slug>.md` file in [adrDir], sorted by name. Returns an empty list when [adrDir] does not exist. */
internal fun readAdrFiles(adrDir: Path): List<AdrFile> {
  if (!adrDir.isDirectory()) return emptyList()
  return adrDir.listDirectoryEntries("*.md").sorted().mapNotNull { file ->
    val number = DocLinkChecker.adrNumber(file) ?: return@mapNotNull null
    val text = readDocText(file)
    val section = STATUS_SECTION.find(text)
    val statusLine = section?.let { lineOf(text, it.range.first) } ?: 0
    AdrFile(number, file, text, statusLine, section?.groupValues?.get(1).orEmpty())
  }
}

private val STATUS_SECTION = Regex("""^## Status\n(.*?)(?=^## |\z)""", setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))
private val STATUS_WORD = Regex("""\w+""")
