// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import java.lang.ref.WeakReference

/**
 * Measures whether the renderer instances of an inspected icon stay the same across its repaints (stable) or get replaced
 * (the icon is recreated, e.g. a new `SwingIcon` per toolbar update).
 *
 * - [onPaint] per captured paint, with the paint's top-level renderer → [snapshot] ([RendererStability]).
 * - [nodeRecreations] per rebuilt inspector tree: counts, per node id, how often a different renderer instance appeared at
 *   that tree position.
 *
 * Renderers are only held weakly. Thread-safe.
 */
internal class RendererStabilityTracker(private val clock: () -> Long = System::currentTimeMillis) {
  private var startMs = clock()
  private var paints = 0
  private var recreations = 0
  private var distinct = 0
  private var lastRecreationMs: Long? = null
  private var previous: WeakReference<Any>? = null
  /** Distinct top-level instances seen (weakly; a collected one can never be seen again). */
  private val seen = ArrayList<WeakReference<Any>>()

  private val nodeRenderers = HashMap<String, WeakReference<Any>>()
  private val nodeCounts = HashMap<String, Int>()

  @Synchronized
  fun onPaint(topLevelRenderer: Any) {
    paints++
    val last = previous
    if (last != null && last.get() !== topLevelRenderer) {
      recreations++
      lastRecreationMs = clock()
    }
    if (last == null || last.get() !== topLevelRenderer) previous = WeakReference(topLevelRenderer)
    seen.removeIf { it.get() == null }
    if (seen.none { it.get() === topLevelRenderer }) {
      distinct++
      if (seen.size < MAX_SEEN) seen.add(WeakReference(topLevelRenderer))
    }
  }

  @Synchronized
  fun snapshot(): RendererStability {
    val now = clock()
    return RendererStability(
      paints = paints,
      recreations = recreations,
      distinctInstances = distinct,
      lastRecreationAgoMs = lastRecreationMs?.let { now - it },
      observedMs = if (paints == 0) 0 else now - startMs,
    )
  }

  /**
   * Records [renderers] (node id → renderer instance of a rebuilt tree) and returns the recreation count of each of those
   * nodes (ids without recreations are omitted).
   */
  @Synchronized
  fun nodeRecreations(renderers: Map<String, Any>): Map<String, Int> {
    for ((id, renderer) in renderers) {
      val last = nodeRenderers[id]
      if (last != null && last.get() !== renderer) {
        nodeCounts[id] = (nodeCounts[id] ?: 0) + 1
      }
      if (last == null || last.get() !== renderer) nodeRenderers[id] = WeakReference(renderer)
    }
    return currentNodeCounts(renderers.keys)
  }

  @Synchronized
  fun currentNodeCounts(ids: Collection<String>): Map<String, Int> =
    ids.mapNotNull { id -> nodeCounts[id]?.takeIf { it > 0 }?.let { id to it } }.toMap()

  /** Zeroes all counters and restarts the observation; the last seen instances stay known, so the next change counts. */
  @Synchronized
  fun reset() {
    startMs = clock()
    paints = 0
    recreations = 0
    lastRecreationMs = null
    nodeCounts.clear()
    seen.clear()
    distinct = 0
  }

  private companion object {
    const val MAX_SEEN = 1024
  }
}

/** [node] and its subtree with [InspectorNode.recreations] set from [counts] (node id → count; missing = 0). */
internal fun withRecreations(node: InspectorNode, counts: Map<String, Int>): InspectorNode {
  val recreations = counts[node.id] ?: 0
  val children = if (node.children.isEmpty()) node.children else node.children.map { withRecreations(it, counts) }
  return if (recreations == node.recreations && children == node.children) node
  else node.copy(recreations = recreations, children = children)
}
