// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.docs

import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/** Holds the status word of a `Proposed` ADR. */
internal const val PROPOSED_STATUS: String = "Proposed"

/** Holds the time from which a six-character ADR id counts the seconds. */
internal val ADR_ID_BASE: Instant = Instant.parse("2026-11-18T05:20:25Z")

/** Holds the number of characters in a time-based ADR id. */
private const val ADR_ID_LENGTH = 6

/** Holds the radix of a time-based ADR id. The digits are `0-9`, then `A-Z`. */
private const val ADR_ID_RADIX = 36

/** Holds the number of digits in the number of an ADR from before the time-based ids. */
internal const val ADR_NUMBER_LENGTH: Int = 4

/** Matches the number `NNNN` of an ADR from before the time-based ids. */
internal const val ADR_NUMBER_PATTERN: String = """\d{$ADR_NUMBER_LENGTH}"""

/** Matches an ADR id: a time-based id of six characters `0-9A-Z`, or a number `NNNN`. */
internal const val ADR_ID_PATTERN: String = """[0-9A-Z]{$ADR_ID_LENGTH}|$ADR_NUMBER_PATTERN"""

/**
 * Matches the text of a link to an ADR: `ADR <ID>`, or a bare number `NNNN`. A bare six-character text is not an ADR
 * link text, because it can be a word, such as `[AGENTS]`.
 */
internal const val ADR_LINK_TEXT_PATTERN: String = """ADR (?:$ADR_ID_PATTERN)|$ADR_NUMBER_PATTERN"""

/**
 * Orders ADR ids by time: a number `NNNN` counts as the id `00NNNN`. Two ids of one value, such as `0042` and
 * `000042`, stay distinct, so a sorted set keeps both.
 */
internal val ADR_ID_ORDER: Comparator<String> = compareBy({ canonicalAdrId(it) }, { it })

/**
 * Holds one decision record (ADR) file of an ADR folder.
 *
 * @property id the id from the file name, as written: six characters, or the number `NNNN` of an older ADR.
 * @property file the file.
 * @property text the text of the file, read with [readDocText].
 * @property statusLine the 1-based line of the `## Status` heading, or `0` when the file has no such section.
 * @property status the text of the `Status` section, without its heading.
 */
internal class AdrFile(val id: String, val file: Path, val text: String, val statusLine: Int, val status: String) {
  /** Holds the first word of [status], such as [PROPOSED_STATUS]. */
  val statusWord: String
    get() = STATUS_WORD.find(status)?.value.orEmpty()

  /** Returns the fold entries `### <ID>, ...` of the `Earlier decisions` section of a merged ADR. */
  fun foldEntries(): List<FoldEntry> {
    val prose = withoutCode(text)
    val section = EARLIER_DECISIONS_SECTION.find(prose) ?: return emptyList()
    val sectionOffset = section.groups[1]!!.range.first
    return FOLD_ENTRY.findAll(section.groupValues[1]).map { entry ->
      FoldEntry(entry.groupValues[1], lineOf(text, sectionOffset + entry.range.first))
    }.toList()
  }
}

/**
 * Holds one fold entry of a merged ADR.
 *
 * @property id the id of the folded ADR, as written.
 * @property line the 1-based line of the entry heading.
 */
internal class FoldEntry(val id: String, val line: Int)

/** Returns every Markdown file in [adrDir], sorted by name, or an empty list when [adrDir] does not exist. */
internal fun adrFolderFiles(adrDir: Path): List<Path> =
  if (adrDir.isDirectory()) adrDir.listDirectoryEntries("*.md").sorted() else emptyList()

/** Reads every ADR file in [adrDir] in the order of [ADR_ID_ORDER]. Skips a file whose name has no ADR id. */
internal fun readAdrFiles(adrDir: Path): List<AdrFile> =
  adrFolderFiles(adrDir).mapNotNull { file ->
    val id = adrIdOf(file) ?: return@mapNotNull null
    val text = readDocText(file)
    val section = STATUS_SECTION.find(text)
    val statusLine = section?.let { lineOf(text, it.range.first) } ?: 0
    AdrFile(id, file, text, statusLine, section?.groupValues?.get(1).orEmpty())
  }.sortedWith(compareBy(ADR_ID_ORDER, AdrFile::id))

/** Returns the id of the ADR file [file], or `null` when its name is not `<ID>-<slug>.md` or `NNNN-<slug>.md`. */
internal fun adrIdOf(file: Path): String? = ADR_FILE.matchEntire(file.name)?.groupValues?.get(1)

/** Returns the ADR id of [linkText], a text that matches [ADR_LINK_TEXT_PATTERN]. */
internal fun adrIdOfLinkText(linkText: String): String = linkText.removePrefix("ADR ")

/** Returns `true` when [id] is the number `NNNN` of an ADR from before the time-based ids. */
internal fun isAdrNumber(id: String): Boolean = id.length == ADR_NUMBER_LENGTH

/** Returns [id] in its six-character form, so the number `NNNN` gives `00NNNN`. */
internal fun canonicalAdrId(id: String): String = id.padStart(ADR_ID_LENGTH, '0')

/**
 * Returns the id of an ADR created at [time]: the seconds from [ADR_ID_BASE], in base 36, padded to six characters.
 *
 * @return the id, or `null` when [time] is before [ADR_ID_BASE] or the id needs more than six characters.
 */
internal fun adrIdAt(time: Instant): String? {
  val seconds = Duration.between(ADR_ID_BASE, time).seconds
  if (seconds < 0) return null
  val id = seconds.toString(ADR_ID_RADIX).uppercase()
  return if (id.length <= ADR_ID_LENGTH) canonicalAdrId(id) else null
}

private fun sectionPattern(heading: String): Regex =
  Regex("""^## $heading\n(.*?)(?=^## |\z)""", setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))

private val STATUS_SECTION = sectionPattern("Status")
private val EARLIER_DECISIONS_SECTION = sectionPattern("Earlier decisions")
private val STATUS_WORD = Regex("""\w+""")
private val FOLD_ENTRY = Regex("""^### ($ADR_ID_PATTERN),""", RegexOption.MULTILINE)
private val ADR_FILE = Regex("""($ADR_ID_PATTERN)-.*\.md""")
