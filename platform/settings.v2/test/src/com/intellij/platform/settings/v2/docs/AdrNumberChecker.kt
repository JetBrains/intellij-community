// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.docs

import java.nio.file.Path

/**
 * Checks that each number of the decision records (ADRs) in one folder belongs to exactly one decision.
 *
 * A number is used by an ADR file, `NNNN-<slug>.md`, or by a fold entry `### NNNN, ...` in the `Earlier decisions`
 * section of a merged ADR. A fold entry keeps the number of an ADR that a merge folded. The checker reports:
 * - a number that two or more places use. Two branches took the same number, or a new ADR reused a folded number;
 * - a number from `0001` up to the highest used number that no place uses. An ADR was deleted with no fold entry.
 *
 * @param adrDir the folder that holds the `NNNN-<slug>.md` files.
 */
class AdrNumberChecker(private val adrDir: Path) {
  /** Returns every problem with the numbers in the folder. */
  fun findProblems(): List<DocProblem> {
    val uses = numberUses().groupBy { it.number }
    val duplicates = uses.toSortedMap().values.filter { it.size > 1 }.map { places ->
      val secondPlace = places[1]
      DocProblem(secondPlace.file, secondPlace.line, "the number ${secondPlace.number} is used ${places.size} times: " +
                                                     places.joinToString { it.description })
    }
    val highest = uses.keys.maxOfOrNull { it.toInt() } ?: 0
    val gaps = (1..highest).map { it.toString().padStart(NUMBER_LENGTH, '0') }.filter { it !in uses }.map { number ->
      DocProblem(adrDir, 0, "no ADR file and no fold entry uses the number $number")
    }
    return duplicates + gaps
  }

  private fun numberUses(): List<NumberUse> =
    readAdrFiles(adrDir).flatMap { adr ->
      listOf(NumberUse(adr.number, adr.file, 0, "the file ${adr.file.fileName}")) +
      adr.foldEntries().map { entry ->
        NumberUse(entry.number, adr.file, entry.line, "the fold entry in ${adr.file.fileName}:${entry.line}")
      }
    }

  private class NumberUse(val number: String, val file: Path, val line: Int, val description: String)

  private companion object {
    /** Holds the number of digits in an ADR number. */
    const val NUMBER_LENGTH = 4
  }
}
