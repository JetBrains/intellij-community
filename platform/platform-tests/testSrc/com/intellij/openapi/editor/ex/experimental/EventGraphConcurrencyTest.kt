// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.experimental

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs graph values on several threads at once.
 *
 * A graph value is immutable, and successive values SHARE the nodes of their run trees and agent
 * indexes: an append copies only the path it changes. So a value needs no lock, and a value that
 * one thread reads must stay unchanged while another thread grows a newer value from it. These
 * tests run that claim. They are the regression net for any structure that a later change makes
 * mutable and shared.
 *
 * The newest run of a value is not in the trees. It lives in the value, and an append that
 * extends it copies no node at all. The trees receive a run only when an append that does not
 * continue it closes it. So a test that must reach the trees either types in bursts or inserts
 * at the front, where no op continues the one before it.
 *
 * Every assertion here holds whatever the scheduling, so a failure is a real defect and not
 * a flake. The one thing the tests cannot control is how much the threads overlap, so each
 * one also asserts that the work actually happened.
 *
 * What these tests do NOT claim: that a document is thread safe. Two threads that apply an
 * op to ONE value under ONE agent both mint the same (agent, seq) and diverge. That breaks
 * the agent contract of [DocumentBranch], and no lock would fix it. Each writer below therefore owns
 * its own agent.
 */
class EventGraphConcurrencyTest {

  /**
   * A writer appends while readers replay the values it publishes. A value is
   * self-describing: its text must equal a from-scratch replay of its own graph, so a reader
   * needs no expectation from the writer. A value that shared a node the writer changed would
   * break that.
   *
   * The writer types in bursts. Inside a burst, a value differs from the one before only in
   * its newest run. The first keystroke of each burst closes the run before it into the trees.
   */
  @Test
  fun `a reader replays a published value while a writer appends`() {
    val published = ConcurrentLinkedQueue<DocumentBranch>()
    val writing = AtomicBoolean(true)
    withPool(READERS + 1) { pool ->
      // The barrier makes the reads overlap the writes. Without it a fixed pool can run the
      // writer to completion before it creates a reader thread, and the readers would then
      // drain a queue that nobody is still filling.
      val ready = CyclicBarrier(READERS + 1)
      val writer = pool.submit<DocumentBranch> {
        var branch = DocumentBranch.createBranch("", agent("writer"))
        try {
          ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
          var caret = 0
          repeat(APPENDS) { op ->
            caret = if (op % BURST == 0) 0 else caret + 1
            branch = branch.applyOp(insertOp(caret, "x"))
            // The queue hands the value over, and a value is complete once it is built.
            published.add(branch)
          }
        } finally {
          // A writer that fails must still release the readers.
          writing.set(false)
        }
        branch
      }
      val readers = List(READERS) {
        pool.submit<Int> {
          ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
          var verified = 0
          fun moreToRead() = writing.get() || published.isNotEmpty()
          while (moreToRead() && !Thread.currentThread().isInterrupted) {
            val branch = published.poll()
            if (branch == null) {
              Thread.yield()
              continue
            }
            assertEquals(branch.string(), branch.graph().replay().string()) { "at ${branch.length()} chars" }
            verified++
          }
          verified
        }
      }
      awaitAll(readers + writer)
      val written = writer.await()
      // Every published value was polled by exactly one reader, so the counts must add up.
      // This is what keeps the test from passing without doing anything.
      assertEquals(APPENDS, readers.sumOf { it.await() })
      // Each burst made one run: the writer coalesced, and the trees grew.
      assertEquals(APPENDS / BURST, written.graph().runCount())
    }
  }

  /**
   * A reader holds an OLD value and replays it out of the newer values that the writer grows
   * from it. This is the harder read: the graph that answers for the old version is a newer
   * value, with a larger size and paths that the writer copied.
   *
   * The writer's first burst continues the run that the old value ends in. So the old version
   * names a unit inside a run that keeps growing, and that run later closes into the trees.
   */
  @Test
  fun `a past version replays while the graph grows`() {
    var start = DocumentBranch.createBranch("", agent("writer"))
    repeat(40) {
      start = start.applyOp(insertOp(start.length(), "o"))
    }
    val past = start
    val pastVersion = past.graph().version()
    val pastText = past.string()

    val current = AtomicReference(past)
    val writing = AtomicBoolean(true)
    withPool(READERS + 1) { pool ->
      // A fixed pool creates its threads lazily, so without help the writer finishes its
      // whole run before a reader thread exists. The barrier gets everyone live, and the two
      // latches FENCE the append phase: the writer waits until every reader has read the
      // small graph, and waits again until every reader has read the grown one. So each
      // reader is guaranteed to read both sides of the growth, and the reads in between race
      // with it.
      val ready = CyclicBarrier(READERS + 1)
      val smallReads = CountDownLatch(READERS)
      val grownReads = CountDownLatch(READERS)
      val grownSize = past.graph().size() + APPENDS
      val writer = pool.submit<DocumentBranch> {
        var branch = past
        try {
          ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
          awaitLatch(smallReads, "every reader read the small graph")
          var caret = past.length() - 1
          repeat(APPENDS) { op ->
            caret = if (op > 0 && op % BURST == 0) 0 else caret + 1
            branch = branch.applyOp(insertOp(caret, "x"))
            current.set(branch)
          }
          awaitLatch(grownReads, "every reader read the grown graph")
        } finally {
          // A writer that fails must still release the readers.
          writing.set(false)
        }
        branch
      }
      val readers = List(READERS) {
        pool.submit<Int> {
          ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
          var sizesSeen = 0
          var lastSize = -1
          // A latch counts down-EVENTS, not distinct readers, so each reader must count at
          // most once. Otherwise one fast reader satisfies a latch alone, the writer runs on,
          // and a slow reader never sees both sides of the growth.
          var countedSmall = false
          var countedGrown = false
          while (writing.get() && !Thread.currentThread().isInterrupted) {
            val graph = current.get().graph()
            assertEquals(pastText, graph.replay(pastVersion).string()) { "out of ${graph.size()} units" }
            if (graph.size() != lastSize) {
              sizesSeen++
              lastSize = graph.size()
            }
            if (!countedSmall) {
              smallReads.countDown()
              countedSmall = true
            }
            if (!countedGrown && graph.size() == grownSize) {
              grownReads.countDown()
              countedGrown = true
            }
          }
          sizesSeen
        }
      }
      awaitAll(readers + writer)
      val grown = writer.await()
      val sizesSeen = readers.sumOf { it.await() }
      // The writer finished its whole run, and the fences make every reader see at least the
      // small size and the grown one.
      assertEquals(past.length() + APPENDS, grown.length())
      // The first burst joined the run of the old value, and every later burst made one run.
      assertEquals(APPENDS / BURST, grown.graph().runCount())
      assertTrue(sizesSeen >= 2 * READERS) { "the readers saw only $sizesSeen graph sizes" }
      // Deterministic close: the past version still replays out of the final, larger graph.
      assertEquals(pastText, grown.graph().replay(pastVersion).string())
    }
  }

  /**
   * Several writers that share one value all append at once. Each writer grows its own value,
   * and every value shares the nodes of the base. An append copies only the path it changes, so
   * a writer that changed a shared node in place would corrupt the others. This test aims at that.
   *
   * Each writer owns an agent, so the agent contract holds and the histories are concurrent
   * rather than clashing. Every result must be self-consistent, and they must all converge.
   */
  @Test
  fun `writers that share one value keep valid histories and converge`() {
    withPool(WRITERS) { pool ->
      repeat(ROUNDS) { round ->
        // The base size sweeps a range, so on many rounds the writers fill the first leaf of the
        // run tree inside the race window. That is the append that copies a node the base
        // shares with every other writer.
        val baseOps = MIN_BASE_OPS + round % BASE_OPS_SWEEP
        val shared = baseOfRuns(baseOps)
        // The barrier lines the writers up, so their appends overlap.
        val barrier = CyclicBarrier(WRITERS)
        val results = List(WRITERS) { writer ->
          pool.submit<DocumentBranch> {
            var branch = shared.fork(agent("w$writer"))
            barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            repeat(OPS_PER_WRITER) { op ->
              // Two characters per op, and every writer inserts at the front.
              branch = branch.applyOp(insertOp(0, "$writer$op"))
            }
            assertEquals(branch.string(), branch.graph().replay().string()) { "round $round, writer $writer" }
            branch
          }
        }.map { it.await() }

        // Every writer sees its own ops plus the shared base, and nothing else.
        for ((writer, branch) in results.withIndex()) {
          assertEquals(baseOps + 2 * OPS_PER_WRITER, branch.length()) { "round $round, writer $writer" }
        }
        // The concurrent histories converge, in either merge order.
        val forward = results.reduce { left, right -> left.merge(right) }
        val backward = results.reversed().reduce { left, right -> left.merge(right) }
        assertEquals(forward.string(), backward.string()) { "round $round" }
        assertEquals(forward.graph().replay().string(), forward.string()) { "round $round" }
        assertEquals(baseOps + WRITERS * 2 * OPS_PER_WRITER, forward.length()) { "round $round" }
      }
    }
  }

  /**
   * Several threads merge ONE value at the same time, each from a different concurrent edit
   * of it.
   *
   * This is the test for the queries that only a merge asks: `summarize`, `newRunStarts` and
   * `lvOfSeq` of the agent index. The replay path never reaches them. Here every thread asks
   * them of the same shared value, while the other threads grow values that share its nodes.
   *
   * A merge is legitimate here even on one value, because it mints no id of its own: it only
   * imports the other side's.
   */
  @Test
  fun `concurrent merges of one value all produce a consistent result`() {
    withPool(WRITERS) { pool ->
      repeat(MERGE_ROUNDS) { round ->
        val baseOps = MIN_BASE_OPS + round % BASE_OPS_SWEEP
        val shared = baseOfRuns(baseOps)
        // One concurrent edit per thread. Each source shares every node of `shared`.
        val sources = List(WRITERS) { i ->
          shared.fork(agent("s$i")).applyOp(insertOp(0, "$i"))
        }
        val barrier = CyclicBarrier(WRITERS)
        // Every thread pulls EVERY source, each starting at its own index. So every thread merges
        // several times instead of once, and the threads reach the same union by different merge
        // orders. Convergence and the races get tested together.
        val merged = List(WRITERS) { i ->
          pool.submit<DocumentBranch> {
            barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            var result = shared
            repeat(WRITERS) { step ->
              result = result.merge(sources[(i + step) % WRITERS])
              assertEquals(result.string(), result.graph().replay().string()) {
                "round $round, thread $i, step $step"
              }
            }
            result
          }
        }.map { it.await() }

        // Every merge order reached the same union, and lost nothing on the way.
        val expected = merged[0].string()
        for ((i, result) in merged.withIndex()) {
          assertEquals(baseOps + WRITERS, result.length()) { "round $round, thread $i" }
          assertEquals(expected, result.string()) { "round $round, thread $i" }
        }
      }
    }
  }

  /**
   * A gossip protocol on real threads: every replica lives on its own thread, edits, and
   * pulls its neighbour's published snapshot. `DocBranchGossipFuzzTest` drives the same shape
   * on one thread; this one adds the races.
   *
   * Each thread owns one replica and one agent, which is what the agent contract asks for.
   * A thread reads a neighbour through an [AtomicReference], while the neighbour keeps growing
   * values that share nodes with the one it read.
   *
   * Three invariants. A replica's text always equals a replay of its own graph, and a full sync
   * brings every replica to one text: the single-threaded fuzz uses the same two. The version of a
   * neighbour, which another thread made, also replays in the merge of that neighbour.
   */
  @Test
  fun `replicas that gossip on their own threads converge`() {
    withPool(REPLICAS) { pool ->
      repeat(GOSSIP_ROUNDS) { round ->
        val base = DocumentBranch.createBranch("abc", agent("base"))
        val published = List(REPLICAS) { i -> AtomicReference(base.fork(agent("r$i"))) }
        val ready = CyclicBarrier(REPLICAS)
        val workers = List(REPLICAS) { i ->
          pool.submit {
            var mine = published[i].get()
            ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            repeat(GOSSIP_STEPS) { step ->
              mine = if (step % 2 == 0) {
                mine.applyOp(insertOp(0, "$i"))
              } else {
                // The neighbour is editing its own replica as this reads the snapshot.
                val neighbour = published[(i + 1) % REPLICAS].get()
                val merged = mine.merge(neighbour)
                val neighbourText = merged.graph().replay(neighbour.version()).string()
                assertEquals(neighbour.string(), neighbourText) {
                  "round $round, replica $i, step $step, the version of the neighbour"
                }
                merged
              }
              assertEquals(mine.string(), mine.graph().replay().string()) {
                "round $round, replica $i, step $step"
              }
              published[i].set(mine)
            }
          }
        }
        workers.forEach { it.await() }

        // A full sync, back on one thread: every replica pulls every other one.
        var synced = published.map { it.get() }
        synced = synced.map { mine ->
          synced.fold(mine) { pulled, other ->
            pulled.merge(other)
          }
        }
        val expected = synced[0].string()
        for ((i, replica) in synced.withIndex()) {
          assertEquals(expected, replica.string()) { "round $round, replica $i after the sync" }
          assertEquals(expected, replica.graph().replay().string()) { "round $round, replica $i replay" }
        }
        // Every replica's own ops survived, so the sync joined rather than dropped. Each
        // replica edits on an even step and inserts one character.
        assertEquals("abc".length + REPLICAS * (GOSSIP_STEPS / 2), expected.length) { "round $round" }
      }
    }
  }

  /**
   * Writers that share one value type in bursts, each under its own agent. A keystroke inside
   * a burst extends the writer's own newest run and copies no node. The first keystroke of the
   * next burst closes that run into the writer's own trees, which share their older nodes with
   * the other writers.
   *
   * Every past value of a writer must still replay to its text out of the writer's final
   * graph. That covers a version inside a run, both while the run grows and after it closes.
   */
  @Test
  fun `writers that type in bursts keep valid histories and converge`() {
    withPool(WRITERS) { pool ->
      repeat(BURST_ROUNDS) { round ->
        val baseOps = MIN_BASE_OPS + round % BASE_OPS_SWEEP
        val shared = baseOfRuns(baseOps)
        val barrier = CyclicBarrier(WRITERS)
        val results = List(WRITERS) { writer ->
          pool.submit<DocumentBranch> {
            var branch = shared.fork(agent("w$writer"))
            val past = ArrayList<Pair<Version, String>>()
            var caret = 0
            barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            repeat(BURST_OPS_PER_WRITER) { op ->
              caret = if (op % WRITER_BURST == 0) 0 else caret + 1
              branch = branch.applyOp(insertOp(caret, "$writer"))
              past.add(branch.graph().version() to branch.string())
            }
            val graph = branch.graph()
            for ((version, text) in past) {
              assertEquals(text, graph.replay(version).string()) { "round $round, writer $writer at $version" }
            }
            branch
          }
        }.map { it.await() }

        // Each burst made one run on top of the base, so every writer coalesced.
        for ((writer, branch) in results.withIndex()) {
          val expectedRuns = baseOps + BURST_OPS_PER_WRITER / WRITER_BURST
          assertEquals(expectedRuns, branch.graph().runCount()) { "round $round, writer $writer" }
        }
        val forward = results.reduce { left, right -> left.merge(right) }
        val backward = results.reversed().reduce { left, right -> left.merge(right) }
        assertEquals(forward.string(), backward.string()) { "round $round" }
        assertEquals(forward.graph().replay().string(), forward.string()) { "round $round" }
        assertEquals(baseOps + WRITERS * BURST_OPS_PER_WRITER, forward.length()) { "round $round" }
      }
    }
  }

  /**
   * A base of [ops] runs. Every op inserts at the front, so no op extends the run before it,
   * and the run count, not only the length, sweeps with [ops].
   */
  private fun baseOfRuns(ops: Int): DocumentBranch {
    var base = DocumentBranch.createBranch("", agent("base"))
    repeat(ops) {
      base = base.applyOp(insertOp(0, "o"))
    }
    return base
  }

  private fun <T> Future<T>.await(): T {
    return get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
  }

  /**
   * Waits until every one of [futures] is done, and fails at the first one that fails. A reader
   * that fails early would otherwise hide behind a writer that waits for it until the timeout.
   */
  private fun awaitAll(futures: List<Future<*>>) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
    while (!futures.all { it.isDone }) {
      // A future that is done and failed throws its failure here.
      futures.filter { it.isDone }.forEach { it.get() }
      assertTrue(System.nanoTime() < deadline) { "The workers did not finish in $TIMEOUT_SECONDS seconds" }
      Thread.sleep(POLL_MILLIS)
    }
    futures.forEach { it.get() }
  }

  /**
   * Waits for [latch], and fails when it does not open in time.
   */
  private fun awaitLatch(latch: CountDownLatch, what: String) {
    assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Not in time: $what" }
  }

  private fun withPool(threads: Int, body: (ExecutorService) -> Unit) {
    val pool = Executors.newFixedThreadPool(threads)
    try {
      body(pool)
    } finally {
      pool.shutdownNow()
    }
  }

  private companion object {
    const val READERS = 4
    const val WRITERS = 4
    const val REPLICAS = 6

    /**
     * The writer's op count. It bounds the replay work, which grows with the history.
     */
    const val APPENDS = 600

    /**
     * The keystrokes of one burst in the reader tests. A burst makes one run, and the next one
     * closes it. So the writer closes 300 runs into its trees, and fills several leaves of the
     * run tree while the readers read.
     */
    const val BURST = 2

    const val ROUNDS = 300

    /**
     * The base run count, and how far it sweeps, to cross the first full leaf of the run tree.
     */
    const val MIN_BASE_OPS = 24
    const val BASE_OPS_SWEEP = 24

    /**
     * Enough ops that every writer closes several runs into its own trees.
     */
    const val OPS_PER_WRITER = 8

    const val MERGE_ROUNDS = 60

    /**
     * The rounds of the burst test, the keystrokes of one burst, and those of one writer.
     */
    const val BURST_ROUNDS = 100
    const val WRITER_BURST = 10
    const val BURST_OPS_PER_WRITER = 4 * WRITER_BURST

    /**
     * The gossip rounds, and the edit-or-pull steps each replica takes in one round.
     */
    const val GOSSIP_ROUNDS = 25
    const val GOSSIP_STEPS = 20

    /**
     * A deadlock has to fail the test instead of hanging the build.
     */
    const val TIMEOUT_SECONDS = 60L

    /**
     * How often [awaitAll] looks at the workers.
     */
    const val POLL_MILLIS = 10L
  }
}
