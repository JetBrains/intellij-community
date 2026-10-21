// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.experimental

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.editor.ex.DocumentOp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.BitSet

/**
 * Replays editing histories whose final texts come from the reference implementation, and checks
 * that the port reaches the same texts.
 *
 * A history takes one path or two:
 * - the full replay of its event graph;
 * - a rebuild through branches: each transaction starts from the branch at its parents, which is a
 *   merge of the branches at those units, and applies its ops as local edits. Every merge of two
 *   branches that went apart is a partial replay, so this path checks the partial replay too. It
 *   needs a history that keeps the agent contract of [DocumentBranch].
 *
 * A history is a list of transactions in the export format of diamond types. A transaction names
 * its agent, its first seq, its parents as lvs, and a list of ops. Each op after the first has the
 * op before it as its only parent. The lvs match the lvs of the port, because the graph appends the
 * transactions in their order.
 *
 * The histories are random, with the final texts from the reference. In one set, every history
 * keeps the agent contract. In the other set, an agent edits concurrently with itself in every
 * history.
 */
class EgWalkerConformanceTest {

  @Test
  fun `every history reaches the text of the reference on both paths`() {
    val histories = readHistories(testDataFile("conformanceHistories.json"))
    assertEquals(HISTORIES, histories.size)
    for ((index, history) in histories.withIndex()) {
      val where = { "history $index" }
      checkFullReplay(history, where)
      checkBranches(history, where)
    }
  }

  /**
   * An agent that edits concurrently with itself breaks the agent contract of [DocumentBranch]. So
   * these histories take the full replay only.
   */
  @Test
  fun `every self-concurrent history reaches the text of the reference`() {
    val histories = readHistories(testDataFile("selfConcurrentHistories.json"))
    assertEquals(SELF_CONCURRENT_HISTORIES, histories.size)
    for ((index, history) in histories.withIndex()) {
      val where = { "self-concurrent history $index" }
      assertNotNull(firstContractBreak(history)) { "${where()} keeps the agent contract" }
      checkFullReplay(history, where)
    }
  }

  private fun checkFullReplay(history: History, where: () -> String) {
    assertEquals(history.endContent, graphOf(history).replay().string()) { "${where()}: the full replay" }
  }

  /**
   * Fails unless the rebuild through branches reaches the final text of [history]. Every branch
   * that a transaction starts from must also hold the text of the full replay at its parents, which
   * names the first merge that goes wrong.
   */
  private fun checkBranches(history: History, where: () -> String) {
    val text = textThroughBranches(history, graphOf(history), where)
    assertEquals(history.endContent, text) { "${where()}: the branches" }
  }

  private fun graphOf(history: History): EventGraph {
    var graph = EventGraph.createGraph()
    for (txn in history.txns) {
      assertEquals(txn.start, graph.size()) { "the first lv of $txn" }
      var seq = txn.seqStart.toLong()
      var parents = versionOf(txn.parents)
      for (op in txn.ops) {
        val event = if (op.deleted > 0) {
          Event.createDelete(txn.agent, seq, op.offset, op.deleted)
        } else {
          Event.createInsert(txn.agent, seq, op.offset, op.inserted)
        }
        graph = graph.append(event, parents)
        seq += event.length()
        parents = Version.of(graph.size() - 1)
      }
      assertEquals(txn.end, graph.size()) { "the lv after $txn" }
    }
    return graph
  }

  /**
   * The text that [history] reaches through branches. Each branch that a transaction starts from
   * must hold the text of [stepGraph] at the parents.
   *
   * A branch mints the next seq of its agent, so the rebuild gives the ids of the history only when
   * each transaction of an agent descends from the one before it. That is the agent contract of
   * [DocumentBranch], and [checkAgentContract] makes sure the data keeps it.
   *
   * The branch at a unit exists only after the op that holds it, so an op is cut at every unit that
   * a later transaction names as a parent.
   */
  private fun textThroughBranches(
    history: History,
    stepGraph: EventGraph,
    where: () -> String,
  ): String {
    checkAgentContract(history, where)
    val heads = headsOf(history)
    val needed = BitSet()
    for (txn in history.txns) {
      for (parent in txn.parents) {
        needed.set(parent)
      }
    }
    for (head in heads) {
      needed.set(head)
    }
    val branchAt = HashMap<Int, DocumentBranch>()
    val root = DocumentBranch.createBranch("", agent("root"))
    for (txn in history.txns) {
      var branch = branchAt(txn.parents, root.fork(txn.agent), branchAt)
      assertEquals(stepGraph.replay(versionOf(txn.parents)).string(), branch.string()) {
        "${where()}: the branch before $txn"
      }
      var lv = txn.start
      for (op in txn.ops) {
        val length = op.length()
        var done = 0
        while (done < length) {
          // The piece ends at the next unit that a branch must exist at, or at the op end.
          val cut = needed.nextSetBit(lv + done)
          val pieceEnd = if (cut in 0 until lv + length) cut + 1 else lv + length
          val count = pieceEnd - (lv + done)
          branch = if (op.deleted > 0) {
            branch.applyOp(DocumentOp.deleteOp(op.offset, count))
          } else {
            branch.applyOp(DocumentOp.insertOp(op.offset + done, op.inserted.substring(done, done + count)))
          }
          done += count
          if (needed.get(pieceEnd - 1)) {
            branchAt[pieceEnd - 1] = branch
          }
        }
        lv += length
      }
    }
    return branchAt(heads, root, branchAt).string()
  }

  /**
   * The branch at [lvs]: [start] when they are the root, and the merge of their branches otherwise.
   */
  private fun branchAt(
    lvs: IntArray,
    start: DocumentBranch,
    branchAt: Map<Int, DocumentBranch>,
  ): DocumentBranch {
    if (lvs.isEmpty()) {
      return start
    }
    var branch = branchAt.getValue(lvs[0]).fork(start.agent())
    for (index in 1 until lvs.size) {
      branch = branch.merge(branchAt.getValue(lvs[index]))
    }
    return branch
  }

  /**
   * Fails unless every transaction of an agent descends from the transaction of that agent before it.
   */
  private fun checkAgentContract(history: History, where: () -> String) {
    val breaking = firstContractBreak(history)
    assertNull(breaking) {
      "${where()}: $breaking does not descend from the transaction of its agent before it"
    }
  }

  /**
   * The first transaction that does not descend from the transaction of its agent before it, or
   * null when [history] keeps the agent contract.
   */
  private fun firstContractBreak(history: History): Txn? {
    val lastUnitOf = HashMap<Agent, Int>()
    for (txn in history.txns) {
      val last = lastUnitOf[txn.agent]
      if (last != null && !eventsOf(history, txn.parents).get(last)) {
        return txn
      }
      lastUnitOf[txn.agent] = txn.end - 1
    }
    return null
  }

  /**
   * The units at or before [lvs]. The units of a transaction before a unit are its ancestors.
   */
  private fun eventsOf(history: History, lvs: IntArray): BitSet {
    val seen = BitSet()
    val stack = ArrayDeque<Int>()
    lvs.forEach { stack.addLast(it) }
    while (stack.isNotEmpty()) {
      val lv = stack.removeLast()
      if (seen.get(lv)) {
        continue
      }
      val txn = history.txnAt(lv)
      seen.set(txn.start, lv + 1)
      txn.parents.forEach { stack.addLast(it) }
    }
    return seen
  }

  /**
   * The units that no transaction names as a parent and that end their transaction, ascending.
   */
  private fun headsOf(history: History): IntArray {
    val named = BitSet()
    for (txn in history.txns) {
      txn.parents.forEach { named.set(it) }
    }
    return history.txns.map { it.end - 1 }.filter { !named.get(it) }.toIntArray()
  }

  private fun versionOf(lvs: IntArray): Version {
    if (lvs.isEmpty()) {
      return Version.root()
    }
    return Version.of(lvs[0], *lvs.copyOfRange(1, lvs.size))
  }

  private fun readHistories(file: Path): List<History> {
    val histories = Files.newBufferedReader(file).use { JsonParser.parseReader(it).asJsonArray }
    return histories.map { historyOf(it.asJsonObject) }
  }

  private fun historyOf(json: JsonObject): History {
    val txns = json.arrayAt("txns").map { element ->
      val txn = element.asJsonObject
      val span = txn.arrayAt("span")
      val ops = txn.arrayAt("ops").map { opElement ->
        val op = opElement.asJsonArray
        Op(op[0].asInt, op[1].asInt, op[2].asString)
      }
      val parents = txn.arrayAt("parents").map { it.asInt }.sorted().toIntArray()
      Txn(
        agent = agent(txn.get("agent").asString),
        seqStart = txn.get("seqStart").asInt,
        start = span[0].asInt,
        end = span[1].asInt,
        parents = parents,
        ops = ops,
      )
    }
    return History(txns, json.get("endContent").asString)
  }

  /**
   * The array under [key], which the export format always writes.
   */
  private fun JsonObject.arrayAt(key: String): JsonArray {
    val array = getAsJsonArray(key)
    requireNotNull(array) {
      "The export has no \"$key\" array: $this"
    }
    return array
  }

  private fun testDataFile(name: String): Path {
    return Path.of(PathManager.getCommunityHomePath(), TEST_DATA, name)
  }

  /**
   * One op of a transaction: a delete of [deleted] units at [offset], or an insert of [inserted] there.
   */
  private class Op(
    val offset: Int,
    val deleted: Int,
    val inserted: String,
  ) {
    fun length(): Int {
      return if (deleted > 0) deleted else inserted.length
    }
  }

  /**
   * One transaction: the units `[start, end)` of [agent], from [seqStart], after [parents].
   */
  private class Txn(
    val agent: Agent,
    val seqStart: Int,
    val start: Int,
    val end: Int,
    val parents: IntArray,
    val ops: List<Op>,
  ) {
    override fun toString(): String {
      return "txn[$start..${end - 1}] of $agent after ${parents.contentToString()}"
    }
  }

  private class History(val txns: List<Txn>, val endContent: String) {
    private val starts = IntArray(txns.size) { txns[it].start }

    /**
     * The transaction that holds the unit [lv].
     */
    fun txnAt(lv: Int): Txn {
      val index = starts.binarySearch(lv)
      return txns[if (index >= 0) index else -index - 2]
    }
  }

  private companion object {
    /**
     * The sizes of the data sets, so a truncated file fails and does not pass on less data.
     */
    const val HISTORIES = 150
    const val SELF_CONCURRENT_HISTORIES = 100

    /**
     * The test data of the feature, under the community root.
     */
    const val TEST_DATA = "platform/platform-tests/testData/editor/docBranch"
  }
}
