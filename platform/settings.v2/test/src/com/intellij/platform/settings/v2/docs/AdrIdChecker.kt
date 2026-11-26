// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.docs

import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Checks the ids of the decision records (ADRs) in one folder.
 *
 * A new ADR file is `<ID>-<slug>.md`. The id is six characters `0-9A-Z`: the seconds from [ADR_ID_BASE] to the
 * creation of the ADR, in base 36. The title is `# <ID>. <Title>`, and the `Date:` line holds the creation time.
 * An ADR from before the ids keeps its file `NNNN-<slug>.md`, its title and its `Date:` line. It holds the id `00NNNN`.
 * A fold entry `### <ID>, ...` in the `Earlier decisions` section of a merged ADR keeps the id of a folded ADR.
 *
 * Each check is its own function, so a test can name the failed check.
 *
 * @param adrDir the folder that holds the ADR files.
 * @param lastAdrNumber the highest number at the move to the time-based ids.
 */
class AdrIdChecker(private val adrDir: Path, private val lastAdrNumber: Int) {
  private val adrFiles: List<AdrFile> by lazy { readAdrFiles(adrDir) }

  /** Returns a problem for each Markdown file whose name does not start with an id and `-`. */
  fun shapeProblems(): List<DocProblem> =
    adrFolderFiles(adrDir).filter { adrIdOf(it) == null }.map {
      DocProblem(it, 0, "the file name ${it.fileName} does not start with a four-digit number or a six-character id")
    }

  /** Returns a problem for each ADR file and fold entry with a number above [lastAdrNumber]. */
  fun newNumberProblems(): List<DocProblem> {
    val lastNumber = lastAdrNumber.toString().padStart(ADR_NUMBER_LENGTH, '0')
    return idUses().filter { isAdrNumber(it.id) && it.id.toInt() > lastAdrNumber }.map {
      DocProblem(it.file, it.line, "${it.description} has the number ${it.id}, above the last number $lastNumber. " +
                                   "A new ADR takes a time-based id")
    }
  }

  /**
   * Returns a problem for each id that two or more ADR files or fold entries hold. Two branches created an ADR in the
   * same second, or a new ADR reused a folded id.
   */
  fun duplicateProblems(): List<DocProblem> =
    idUses().groupBy { canonicalAdrId(it.id) }.toSortedMap().filterValues { it.size > 1 }.map { (id, places) ->
      val secondPlace = places[1]
      DocProblem(secondPlace.file, secondPlace.line, "the id $id is used ${places.size} times: " +
                                                     places.joinToString { it.description })
    }

  /**
   * Returns a problem for each six-character ADR whose title does not start with `# <ID>. `, or whose `Date:` line is
   * not a UTC time in seconds that gives the id.
   */
  fun recordProblems(): List<DocProblem> =
    adrFiles.filterNot { isAdrNumber(it.id) }.flatMap { titleProblems(it) + dateProblems(it) }

  private fun titleProblems(adr: AdrFile): List<DocProblem> {
    val title = TITLE.find(adr.text)
    if (title != null && title.value.startsWith("# ${adr.id}. ")) return emptyList()
    val line = title?.let { lineOf(adr.text, it.range.first) } ?: 0
    return listOf(DocProblem(adr.file, line, "the title does not start with `# ${adr.id}. `, the id from the file name"))
  }

  private fun dateProblems(adr: AdrFile): List<DocProblem> {
    val dateLine = DATE_LINE.find(adr.text)
    val line = dateLine?.let { lineOf(adr.text, it.range.first) } ?: 0
    val date = dateLine?.groupValues?.get(1)?.removeSuffix(".")
    val time = date?.let { parseUtcSeconds(it) }
                 ?: return listOf(DocProblem(adr.file, line, "the Date line is not a UTC time in seconds, such as " +
                                                             "`Date: 2026-11-18T05:21:13Z`"))
    val dateId = adrIdAt(time)
    if (dateId == adr.id) return emptyList()
    val message = if (dateId == null) "the date $date gives no id" else "the date $date gives the id $dateId, not ${adr.id}"
    return listOf(DocProblem(adr.file, line, message))
  }

  private fun parseUtcSeconds(text: String): Instant? {
    if (!UTC_SECONDS.matches(text)) return null
    return try {
      Instant.parse(text)
    }
    catch (e: DateTimeParseException) {
      null
    }
  }

  private fun idUses(): List<IdUse> =
    adrFiles.flatMap { adr ->
      listOf(IdUse(adr.id, adr.file, 0, "the file ${adr.file.fileName}")) +
      adr.foldEntries().map { entry ->
        IdUse(entry.id, adr.file, entry.line, "the fold entry in ${adr.file.fileName}:${entry.line}")
      }
    }

  private class IdUse(val id: String, val file: Path, val line: Int, val description: String)

  private companion object {
    val TITLE = Regex("""^# .*""", RegexOption.MULTILINE)
    val DATE_LINE = Regex("""^Date: (\S*)""", RegexOption.MULTILINE)
    val UTC_SECONDS = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z""")
  }
}
