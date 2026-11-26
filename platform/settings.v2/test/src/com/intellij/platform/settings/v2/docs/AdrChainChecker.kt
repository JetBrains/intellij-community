// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.docs

import java.nio.file.Path

/**
 * Checks the amendment and supersession graph of the decision records (ADRs) in one folder.
 *
 * The `Status` section of an ADR names its relations: `Amends`, `Supersedes`, `Amended by` and `Superseded by`, each
 * followed by links like `[ADR <ID>](...)`, up to the end of the sentence. The checker builds the graph from the
 * older ADR to the newer one and reports:
 * - a relation to an ADR that does not exist;
 * - a relation that the other ADR does not name back. An ADR in the `Proposed` status may amend or supersede an older
 *   ADR that does not name it yet, because the status of the older ADR changes only when the new one is accepted;
 * - a cycle;
 * - a chain of more than [maxChainLength] ADRs. Such a chain must be merged into fewer ADRs.
 *
 * @param adrDir the folder that holds the ADR files.
 * @param maxChainLength the largest number of ADRs that one chain may hold.
 */
class AdrChainChecker(private val adrDir: Path, private val maxChainLength: Int) {
  private val records: Map<String, AdrRecord> by lazy { readRecords() }

  /** Returns every problem in the graph. */
  fun findProblems(): List<DocProblem> = relationProblems() + chainProblems()

  /** Returns each chain of more than [maxChainLength] ADRs, as the ADR ids from the oldest to the newest. */
  fun longChains(): List<List<String>> {
    val newer = newerByOlder()
    val longest = HashMap<String, List<String>>()
    fun longestFrom(id: String, path: Set<String>): List<String> {
      longest[id]?.let { return it }
      val next = newer[id].orEmpty().filter { it !in path }
      val result = listOf(id) + (next.map { longestFrom(it, path + id) }.maxByOrNull { it.size } ?: emptyList())
      longest[id] = result
      return result
    }
    val hasOlder = newer.values.flatten().toSet()
    return newer.keys.filter { it !in hasOlder }.sortedWith(ADR_ID_ORDER)
      .map { longestFrom(it, emptySet()) }
      .filter { it.size > maxChainLength }
  }

  private fun relationProblems(): List<DocProblem> {
    val problems = mutableListOf<DocProblem>()
    for (record in records.values.sortedWith(compareBy(ADR_ID_ORDER, AdrRecord::id))) {
      for (relation in record.relations) {
        val other = records[relation.id]
        if (other == null) {
          problems += DocProblem(record.file, record.statusLine,
                                 "${relation.kind.text} names ADR ${relation.id}, which does not exist")
          continue
        }
        val backKind = relation.kind.inverse
        val namedBack = other.relations.any { it.kind == backKind && it.id == record.id }
        val mayBeUnnamed = relation.kind.isFromNewer && record.statusWord == PROPOSED_STATUS
        if (!namedBack && !mayBeUnnamed) {
          problems += DocProblem(record.file, record.statusLine,
                                 "${relation.kind.text} ADR ${relation.id}, but ADR ${relation.id} has no " +
                                 "${backKind.text} ADR ${record.id}")
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
    fun visit(id: String, path: List<String>) {
      if (id in path) {
        val cycle = path.subList(path.indexOf(id), path.size) + id
        val record = records.getValue(id)
        problems += DocProblem(record.file, record.statusLine, "the ADRs form a cycle: ${cycle.joinToString(" -> ")}")
        return
      }
      if (!done.add(id)) return
      for (next in newer[id].orEmpty()) visit(next, path + id)
    }
    for (id in newer.keys.sortedWith(ADR_ID_ORDER)) visit(id, emptyList())
    return problems
  }

  /** Returns the newer ADRs of each ADR, from the relations that either side names. */
  private fun newerByOlder(): Map<String, Set<String>> {
    val newer = HashMap<String, MutableSet<String>>()
    for (record in records.values) {
      for (relation in record.relations) {
        if (relation.id !in records) continue
        val (older, newerId) = if (relation.kind.isFromNewer) relation.id to record.id else record.id to relation.id
        newer.getOrPut(older) { sortedSetOf(ADR_ID_ORDER) }.add(newerId)
        newer.getOrPut(newerId) { sortedSetOf(ADR_ID_ORDER) }
      }
    }
    return newer
  }

  private fun readRecords(): Map<String, AdrRecord> =
    readAdrFiles(adrDir).associate {
      it.id to AdrRecord(it.id, it.file, it.statusLine, it.statusWord, parseRelations(it.status))
    }

  private fun parseRelations(status: String): List<Relation> {
    val relations = mutableListOf<Relation>()
    var kind: RelationKind? = null
    for (token in RELATION_TOKEN.findAll(status)) {
      val keyword = token.groups[1]?.value
      val id = token.groups[2]?.value?.let(::adrIdOfLinkText)
      when {
        keyword != null -> kind = RelationKind.entries.first { it.text == keyword }
        id != null -> kind?.let { relations += Relation(it, id) }
        else -> kind = null
      }
    }
    return relations
  }

  private data class AdrRecord(
    val id: String,
    val file: Path,
    val statusLine: Int,
    val statusWord: String,
    val relations: List<Relation>,
  )

  private data class Relation(val kind: RelationKind, val id: String)

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
    val RELATION_TOKEN = Regex("""(Amends|Supersedes|Amended by|Superseded by)|\[($ADR_LINK_TEXT_PATTERN)]|\.(?=\s|\z)""")
  }
}
