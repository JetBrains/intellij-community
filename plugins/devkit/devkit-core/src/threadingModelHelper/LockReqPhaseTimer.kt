// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Measures the time that the processing of one traversal node spends in each [Phase].
 *
 * The phases are exclusive.
 * If a measured block contains a nested measured block, the time of the nested block counts only for the nested phase.
 *
 * The class is not thread-safe. Only one thread at a time can use an instance.
 * The [DISABLED] instance does not measure anything, so all threads can use it.
 */
class LockReqPhaseTimer private constructor(@PublishedApi internal val isEnabled: Boolean) {

  constructor() : this(true)

  enum class Phase(val presentableName: String, val description: String) {
    READ_ACTION_WAIT("read action wait", "The wait for a smart read action. It includes dumb mode and the restarts after a write action."),
    SMART_POINTERS("smart pointers", "The creation and the restore of smart pointers."),
    ANNOTATION_CHECK("annotation check", "The check of the method annotations."),
    TREE_WALK("tree walk", "The walk along the method body to find the callees. It does not include the reference resolve."),
    REFERENCE_RESOLVE("reference resolve", "The resolve of the calls, the method references and the message bus topics."),
    CALLEE_CHECK("callee check", "The check of the callees for assertions and Swing calls."),
    INHERITOR_SEARCH("inheritor search", "The search for the overriding methods of the callees."),
    LISTENER_SEARCH("listener search", "The search for the message bus listeners."),
    SEARCH_CACHE_WAIT("search cache wait", "The wait for another thread that runs the same inheritor or listener search."),
    ENQUEUE("enqueue", "The creation of the queue entries for the callees."),
    OTHER("other", "The remaining time. It includes the progress report, the consumer calls and the scheduling."),
  }

  /**
   * The measured times of one traversal node.
   */
  class Snapshot internal constructor(
    private val totalNanos: LongArray,
    private val counts: IntArray,
    private val maxNanos: LongArray,
  ) {
    fun timeNanos(phase: Phase): Long = totalNanos[phase.ordinal]

    fun time(phase: Phase): Duration = timeNanos(phase).nanoseconds

    /**
     * The number of the measured blocks of [phase].
     */
    fun count(phase: Phase): Int = counts[phase.ordinal]

    /**
     * The longest measured block of [phase].
     */
    fun maxTime(phase: Phase): Duration = maxNanos[phase.ordinal].nanoseconds
  }

  private val totalNanos = LongArray(Phase.entries.size)
  private val counts = IntArray(Phase.entries.size)
  private val maxNanos = LongArray(Phase.entries.size)

  /**
   * The time of the finished nested blocks in the current block.
   */
  private var nestedNanos = 0L

  /**
   * The exclusive time of the finished blocks of [phase] up to now.
   * The [DISABLED] instance always returns `0`.
   */
  fun measuredNanos(phase: Phase): Long = totalNanos[phase.ordinal]

  inline fun <T> measure(phase: Phase, block: () -> T): T {
    if (!isEnabled) {
      return block()
    }
    val startNanos = System.nanoTime()
    val outerNestedNanos = enterPhase()
    try {
      return block()
    }
    finally {
      exitPhase(phase, startNanos, outerNestedNanos)
    }
  }

  @PublishedApi
  internal fun enterPhase(): Long {
    val outerNestedNanos = nestedNanos
    nestedNanos = 0
    return outerNestedNanos
  }

  @PublishedApi
  internal fun exitPhase(phase: Phase, startNanos: Long, outerNestedNanos: Long) {
    val elapsedNanos = System.nanoTime() - startNanos
    val exclusiveNanos = (elapsedNanos - nestedNanos).coerceAtLeast(0)
    totalNanos[phase.ordinal] += exclusiveNanos
    counts[phase.ordinal]++
    if (exclusiveNanos > maxNanos[phase.ordinal]) {
      maxNanos[phase.ordinal] = exclusiveNanos
    }
    nestedNanos = outerNestedNanos + elapsedNanos
  }

  /**
   * Takes the measured times.
   * The part of [wallClockNanos] that no block measured counts for [Phase.OTHER].
   */
  fun snapshot(wallClockNanos: Long): Snapshot {
    val total = totalNanos.copyOf()
    val unmeasuredNanos = wallClockNanos - total.sum()
    if (unmeasuredNanos > 0) {
      total[Phase.OTHER.ordinal] += unmeasuredNanos
    }
    return Snapshot(total, counts.copyOf(), maxNanos.copyOf())
  }

  companion object {
    val DISABLED: LockReqPhaseTimer = LockReqPhaseTimer(false)
  }
}
