// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.docs

import java.nio.file.Path

/**
 * Checks the amendment and supersession graph of the decision records (ADRs) in one folder.
 *
 * The `Status` section of an ADR names its relations: `Amends`, `Supersedes`, `Amended by` and `Superseded by`, each
 * followed by links like `[ADR NNNN](...)`, up to the end of the sentence. The checker builds the graph from the
 * older ADR to the newer one and reports:
 * - a relation to an ADR that does not exist;
 * - a relation that the other ADR does not name back. An ADR in the `Proposed` status may amend or supersede an older
 *   ADR that does not name it yet, because the status of the older ADR changes only when the new one is accepted;
 * - a cycle;
 * - a chain of more than [maxChainLength] ADRs. Such a chain must be merged into fewer ADRs.
 *
 * @param adrDir the folder that holds the `NNNN-<slug>.md` files.
 * @param maxChainLength the largest number of ADRs that one chain may hold.
 */
class AdrChainChecker(private val adrDir: Path, private val maxChainLength: Int) {
  private val records: Map<String, AdrRecord> by lazy { readRecords() }

  /** Returns every problem in the graph. */
  fun findProblems(): List<DocProblem> = relationProblems() + chainProblems()

  /** Returns each chain of more than [maxChainLength] ADRs, as the ADR numbers from the oldest to the newest. */
  fun longChains(): List<List<String>> {
    val newer = newerByOlder()
    val longest = HashMap<String, List<String>>()
    fun longestFrom(number: String, path: Set<String>): List<String> {
      longest[number]?.let { return it }
      val next = newer[number].orEmpty().filter { it !in path }
      val result = listOf(number) + (next.map { longestFrom(it, path + number) }.maxByOrNull { it.size } ?: emptyList())
      longest[number] = result
      return result
    }
    val hasOlder = newer.values.flatten().toSet()
    return newer.keys.filter { it !in hasOlder }.sorted()
      .map { longestFrom(it, emptySet()) }
      .filter { it.size > maxChainLength }
  }

  private fun relationProblems(): List<DocProblem> {
    val problems = mutableListOf<DocProblem>()
    for (record in records.values.sortedBy { it.number }) {
      for (relation in record.relations) {
        val other = records[relation.number]
        if (other == null) {
          problems += DocProblem(record.file, record.statusLine,
                                 "${relation.kind.text} names ADR ${relation.number}, which does not exist")
          continue
        }
        val backKind = relation.kind.inverse
        val namedBack = other.relations.any { it.kind == backKind && it.number == record.number }
        val mayBeUnnamed = relation.kind.isFromNewer && record.statusWord == PROPOSED_STATUS
        if (!namedBack && !mayBeUnnamed) {
          problems += DocProblem(record.file, record.statusLine,
                                 "${relation.kind.text} ADR ${relation.number}, but ADR ${relation.number} has no " +
                                 "${backKind.text} ADR ${record.number}")
        }
      }
    }
    return problems
  }

  private fun chainProblems(): List<DocProblem> {
    val cycles = findCycles()
    val chains = longChains().map { chain ->
      val first = records.getValue(chain.first())
      DocProblem(first.file, first.statusLine,
                 "the chain ${chain.joinToString(" -> ")} holds ${chain.size} ADRs, more than $maxChainLength. " +
                 "Merge these ADRs into one decision")
    }
    return cycles + chains
  }

  private fun findCycles(): List<DocProblem> {
    val newer = newerByOlder()
    val done = HashSet<String>()
    val problems = mutableListOf<DocProblem>()
    fun visit(number: String, path: List<String>) {
      if (number in path) {
        val cycle = path.subList(path.indexOf(number), path.size) + number
        val record = records.getValue(number)
        problems += DocProblem(record.file, record.statusLine, "the ADRs form a cycle: ${cycle.joinToString(" -> ")}")
        return
      }
      if (!done.add(number)) return
      for (next in newer[number].orEmpty()) visit(next, path + number)
    }
    for (number in newer.keys.sorted()) visit(number, emptyList())
    return problems
  }

  /** Returns the newer ADRs of each ADR, from the relations that either side names. */
  private fun newerByOlder(): Map<String, Set<String>> {
    val newer = HashMap<String, MutableSet<String>>()
    for (record in records.values) {
      for (relation in record.relations) {
        if (relation.number !in records) continue
        val (older, newerNumber) = if (relation.kind.isFromNewer) relation.number to record.number else record.number to relation.number
        newer.getOrPut(older) { sortedSetOf() }.add(newerNumber)
        newer.getOrPut(newerNumber) { sortedSetOf() }
      }
    }
    return newer
  }

  private fun readRecords(): Map<String, AdrRecord> =
    readAdrFiles(adrDir).associate {
      it.number to AdrRecord(it.number, it.file, it.statusLine, it.statusWord, parseRelations(it.status))
    }

  private fun parseRelations(status: String): List<Relation> {
    val relations = mutableListOf<Relation>()
    var kind: RelationKind? = null
    for (token in RELATION_TOKEN.findAll(status)) {
      val keyword = token.groups[1]?.value
      val number = token.groups[2]?.value
      when {
        keyword != null -> kind = RelationKind.entries.first { it.text == keyword }
        number != null -> kind?.let { relations += Relation(it, number) }
        else -> kind = null
      }
    }
    return relations
  }

  private data class AdrRecord(
    val number: String,
    val file: Path,
    val statusLine: Int,
    val statusWord: String,
    val relations: List<Relation>,
  )

  private data class Relation(val kind: RelationKind, val number: String)

  private enum class RelationKind(val text: String, val isFromNewer: Boolean) {
    AMENDS("Amends", true),
    SUPERSEDES("Supersedes", true),
    AMENDED_BY("Amended by", false),
    SUPERSEDED_BY("Superseded by", false);

    val inverse: RelationKind
      get() = when (this) {
        AMENDS -> AMENDED_BY
        SUPERSEDES -> SUPERSEDED_BY
        AMENDED_BY -> AMENDS
        SUPERSEDED_BY -> SUPERSEDES
      }
  }

  private companion object {
    /** Matches a relation keyword, an ADR link text, or a sentence end, which closes the list of a keyword. */
    val RELATION_TOKEN = Regex("""(Amends|Supersedes|Amended by|Superseded by)|\[(?:ADR )?(\d{4})]|\.(?=\s|\z)""")
  }
}
