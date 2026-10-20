// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

/**
 * Collects the statistics of one call graph traversal in [LockReqAnalyzerParallelBFS].
 *
 * Each processed queue entry is a node of the traversal tree.
 * The parent of a node is the node that put the entry into the queue.
 * The same method can be processed more than once, if the traversal finds a shorter path to it later.
 * In this case, each processing is a separate node.
 *
 * The class is thread-safe.
 */
internal class LockReqAnalysisStatistics {

  enum class Status { COMPLETED, CANCELLED, FAILED }

  class Node(val id: Int, val parentId: Int, val signature: MethodSignature, val depth: Int, val startNanos: Long) {
    /**
     * The wall clock time of the processing in nanoseconds, or `-1` if the processing did not finish.
     */
    @Volatile
    var durationNanos: Long = -1

    @Volatile
    var calleeCount: Int = 0
  }

  /**
   * The statistics of the traversal subtree that starts at [node].
   *
   * @property subtreeDepth the number of levels in the subtree. A leaf has the depth 1.
   * @property subtreeSize the number of processed nodes in the subtree, including [node].
   * @property subtreeProcessingTime the sum of the processing times of all finished nodes in the subtree.
   * The nodes run in parallel, so this value is not a wall clock time.
   */
  class SubtreeStatistics(
    val node: Node,
    val subtreeDepth: Int,
    val subtreeSize: Int,
    val subtreeProcessingTime: Duration,
    val children: List<SubtreeStatistics>,
  )

  private val nextNodeId = AtomicInteger()
  private val nodes = ConcurrentHashMap<Int, Node>()
  private val enqueuedEntries = AtomicInteger()
  private val skippedAsVisited = AtomicInteger()
  private val skippedByMaxDepth = AtomicInteger()

  @Volatile
  var rootSignature: MethodSignature? = null

  fun onEnqueued() {
    enqueuedEntries.incrementAndGet()
  }

  fun onSkippedAsVisited() {
    skippedAsVisited.incrementAndGet()
  }

  fun onSkippedByMaxDepth() {
    skippedByMaxDepth.incrementAndGet()
  }

  fun startNode(parentId: Int, signature: MethodSignature, depth: Int): Node {
    val node = Node(nextNodeId.getAndIncrement(), parentId, signature, depth, System.nanoTime())
    nodes[node.id] = node
    return node
  }

  fun finishNode(node: Node) {
    node.durationNanos = System.nanoTime() - node.startNanos
  }

  /**
   * Builds the traversal trees.
   * A node without a recorded parent is a root.
   * The children of a node are sorted by their signatures to keep the output stable.
   */
  fun computeSubtreeStatistics(): List<SubtreeStatistics> {
    val snapshot = nodes.values.toList()
    val ids = snapshot.mapTo(HashSet()) { it.id }
    val childrenByParent = snapshot.groupBy { it.parentId }
    val comparator = compareBy<Node>({ it.signature.toPresentableString() }, { it.id })

    // The recursion depth is bounded by AnalysisConfig.maxDepth, because each level increases the path length by one.
    fun compute(node: Node): SubtreeStatistics {
      val children = childrenByParent[node.id].orEmpty().sortedWith(comparator).map { compute(it) }
      val ownTime = if (node.durationNanos >= 0) node.durationNanos.nanoseconds else Duration.ZERO
      return SubtreeStatistics(
        node = node,
        subtreeDepth = 1 + (children.maxOfOrNull { it.subtreeDepth } ?: 0),
        subtreeSize = 1 + children.sumOf { it.subtreeSize },
        subtreeProcessingTime = children.fold(ownTime) { acc, child -> acc + child.subtreeProcessingTime },
        children = children,
      )
    }

    return snapshot.filter { it.parentId == NO_PARENT || it.parentId !in ids }.sortedWith(comparator).map(::compute)
  }

  fun buildReport(config: AnalysisConfig, status: Status, wallClockTime: Duration): String {
    val trees = computeSubtreeStatistics()
    val allNodes = nodes.values.toList()
    val uniqueMethods = allNodes.mapTo(HashSet()) { it.signature }.size
    return buildString {
      appendLine("Lock requirement analysis statistics")
      appendLine("Root method: ${rootSignature?.toPresentableString() ?: "<unknown>"}")
      appendLine("Status: $status")
      appendLine("Wall clock time: ${wallClockTime.format()}")
      appendLine("Constraint types: ${config.interestingConstraintTypes.joinToString()}")
      appendLine("Max depth: ${config.maxDepth}, max implementations: ${config.maxImplementations}, polymorphic: ${config.includePolymorphic}")
      appendLine("Enqueued entries: ${enqueuedEntries.get()}")
      appendLine("Processed nodes: ${allNodes.size} (unique methods: $uniqueMethods, reprocessed with a shorter path: ${allNodes.size - uniqueMethods})")
      appendLine("Unfinished nodes: ${allNodes.count { it.durationNanos < 0 }}")
      appendLine("Skipped entries (a shorter path is already processed): ${skippedAsVisited.get()}")
      appendLine("Skipped entries (max depth is reached): ${skippedByMaxDepth.get()}")
      appendLine("Max subtree depth: ${trees.maxOfOrNull { it.subtreeDepth } ?: 0}")
      appendLine()

      appendLine("Slowest calls (top $SLOWEST_CALLS_LIMIT):")
      allNodes
        .filter { it.durationNanos >= 0 }
        .sortedByDescending { it.durationNanos }
        .take(SLOWEST_CALLS_LIMIT)
        .forEach { node ->
          appendLine(String.format(Locale.ROOT, "%14s  depth=%-3d callees=%-5d %s",
                                   node.durationNanos.nanoseconds.format(), node.depth, node.calleeCount, node.signature.toPresentableString()))
        }
      appendLine()

      appendLine("Traversal tree (DFS order):")
      appendLine("Time is the wall clock time to process the call. It includes the search for the callees and the body checks of the callees.")
      appendLine("Subtree time is the sum of the processing times in the subtree. The nodes run in parallel, so it is not a wall clock time.")
      appendLine(String.format(Locale.ROOT, "%14s %8s %8s %13s %13s %16s  %s", "time", "depth", "callees", "subtreeDepth", "subtreeNodes", "subtreeTime", "method"))
      fun appendTree(tree: SubtreeStatistics, indent: Int) {
        val node = tree.node
        val time = if (node.durationNanos >= 0) node.durationNanos.nanoseconds.format() else "-"
        appendLine(String.format(Locale.ROOT, "%14s %8d %8d %13d %13d %16s  %s%s",
                                 time, node.depth, node.calleeCount, tree.subtreeDepth, tree.subtreeSize,
                                 tree.subtreeProcessingTime.format(), "  ".repeat(indent), node.signature.toPresentableString()))
        tree.children.forEach { appendTree(it, indent + 1) }
      }
      trees.forEach { appendTree(it, 0) }
    }
  }

  /**
   * Writes the report to a new file in [directory].
   *
   * @return the path of the new file
   */
  fun writeReport(directory: Path, config: AnalysisConfig, status: Status, wallClockTime: Duration): Path {
    Files.createDirectories(directory)
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ROOT))
    val rootName = rootSignature?.let { "${it.containingClassName.substringAfterLast('.')}.${it.methodName}" } ?: "unknown"
    val file = directory.resolve(FileUtil.sanitizeFileName("lock-req-stats-$timestamp-$rootName") + ".txt")
    Files.writeString(file, buildReport(config, status, wallClockTime))
    return file
  }

  companion object {
    const val NO_PARENT: Int = -1
    private const val SLOWEST_CALLS_LIMIT = 50

    private fun MethodSignature.toPresentableString(): String = "$containingClassName.$methodName(${parameterTypes.joinToString(", ")})"

    private fun Duration.format(): String = String.format(Locale.ROOT, "%.3fms", toDouble(DurationUnit.MILLISECONDS))
  }
}
