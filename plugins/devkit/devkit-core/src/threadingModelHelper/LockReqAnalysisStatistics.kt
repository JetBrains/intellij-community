// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqPhaseTimer.Phase
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

    /**
     * Measures the phases of the processing. Only the coroutine that processes this node can use it.
     */
    val timer: LockReqPhaseTimer = LockReqPhaseTimer()

    /**
     * The phase breakdown of [durationNanos], or `null` if the processing did not finish.
     */
    @Volatile
    var phaseTimes: LockReqPhaseTimer.Snapshot? = null
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

  /**
   * The statistics of the searches for one searched element.
   *
   * @property searches the number of the searches
   * @property totalNanos the summed time of the searches. It does not include the enqueue of the results.
   * @property maxNanos the time of the longest search
   * @property results the summed number of the results that the searches gave to the handler
   * @property cacheHits the number of the requests that got the results from [LockReqSearchCache] and did not run a search
   */
  data class SearchStatistics(val searches: Int, val totalNanos: Long, val maxNanos: Long, val results: Int, val cacheHits: Int = 0) {
    operator fun plus(other: SearchStatistics): SearchStatistics = SearchStatistics(
      searches = searches + other.searches,
      totalNanos = totalNanos + other.totalNanos,
      maxNanos = maxOf(maxNanos, other.maxNanos),
      results = results + other.results,
      cacheHits = cacheHits + other.cacheHits,
    )
  }

  private val nextNodeId = AtomicInteger()
  private val nodes = ConcurrentHashMap<Int, Node>()
  private val enqueuedEntries = AtomicInteger()
  private val skippedAsVisited = AtomicInteger()
  private val skippedByMaxDepth = AtomicInteger()
  private val inheritorSearches = ConcurrentHashMap<MethodSignature, SearchStatistics>()
  private val listenerSearches = ConcurrentHashMap<String, SearchStatistics>()

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

  /**
   * Records one search for the overriding methods of [method].
   */
  fun onInheritorSearch(method: MethodSignature, nanos: Long, results: Int) {
    inheritorSearches.merge(method, SearchStatistics(1, nanos, nanos, results), SearchStatistics::plus)
  }

  /**
   * Records one request for the overriding methods of [method] that got the results from the cache.
   */
  fun onInheritorCacheHit(method: MethodSignature) {
    inheritorSearches.merge(method, CACHE_HIT, SearchStatistics::plus)
  }

  /**
   * Records one search for the listeners of the message bus topic [topicClassName].
   */
  fun onListenerSearch(topicClassName: String, nanos: Long, results: Int) {
    listenerSearches.merge(topicClassName, SearchStatistics(1, nanos, nanos, results), SearchStatistics::plus)
  }

  /**
   * Records one request for the listeners of the message bus topic [topicClassName] that got the results from the cache.
   */
  fun onListenerCacheHit(topicClassName: String) {
    listenerSearches.merge(topicClassName, CACHE_HIT, SearchStatistics::plus)
  }

  /**
   * The inheritor search statistics, grouped by the searched method.
   */
  fun inheritorSearchStatistics(): Map<MethodSignature, SearchStatistics> = HashMap(inheritorSearches)

  /**
   * The listener search statistics, grouped by the topic class name.
   */
  fun listenerSearchStatistics(): Map<String, SearchStatistics> = HashMap(listenerSearches)

  fun startNode(parentId: Int, signature: MethodSignature, depth: Int): Node {
    val node = Node(nextNodeId.getAndIncrement(), parentId, signature, depth, System.nanoTime())
    nodes[node.id] = node
    return node
  }

  fun finishNode(node: Node) {
    val durationNanos = System.nanoTime() - node.startNanos
    node.phaseTimes = node.timer.snapshot(durationNanos)
    node.durationNanos = durationNanos
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

      val finishedNodes = allNodes.filter { it.durationNanos >= 0 }
      appendBreakdown(finishedNodes)
      appendLine()

      appendInheritorSearches()
      appendLine()

      appendListenerSearches()
      appendLine()

      appendLine("Slowest calls (top $SLOWEST_CALLS_LIMIT):")
      appendLine("The line below each call shows the phases of the call: the time, the share, the number of blocks and the longest block.")
      finishedNodes
        .sortedByDescending { it.durationNanos }
        .take(SLOWEST_CALLS_LIMIT)
        .forEach { node ->
          appendLine(String.format(Locale.ROOT, "%14s  depth=%-3d callees=%-5d %s",
                                   node.durationNanos.nanoseconds.format(), node.depth, node.calleeCount, node.signature.toPresentableString()))
          node.phaseTimes?.let { appendLine("${" ".repeat(16)}${it.formatBreakdown(node.durationNanos)}") }
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
   * Appends the phase breakdown of the summed processing time of [finishedNodes].
   */
  private fun StringBuilder.appendBreakdown(finishedNodes: List<Node>) {
    val snapshots = finishedNodes.mapNotNull { it.phaseTimes }
    val totalNanos = finishedNodes.sumOf { it.durationNanos }
    appendLine("Time breakdown by phase (the sum over all finished nodes, the nodes run in parallel):")
    appendLine(String.format(Locale.ROOT, "%-20s %14s %7s %10s %14s", "phase", "time", "share", "blocks", "longest block"))
    for (phase in Phase.entries) {
      val phaseNanos = snapshots.sumOf { it.timeNanos(phase) }
      val count = snapshots.sumOf { it.count(phase) }
      val longest = snapshots.maxOfOrNull { it.maxTime(phase) } ?: Duration.ZERO
      appendLine(String.format(Locale.ROOT, "%-20s %14s %7s %10s %14s", phase.presentableName, phaseNanos.nanoseconds.format(),
                               share(phaseNanos, totalNanos), if (phase == Phase.OTHER) "-" else count.toString(),
                               if (phase == Phase.OTHER) "-" else longest.format()))
    }
    appendLine("Phases:")
    for (phase in Phase.entries) {
      appendLine("  ${phase.presentableName}: ${phase.description}")
    }
    appendLine("A restarted read action repeats its phases, so the time of the restarted attempts counts for these phases too.")
  }

  /**
   * Appends the classes with the slowest inheritor searches.
   * The time of a class is the summed time of the searches for the overriding methods of its methods.
   */
  private fun StringBuilder.appendInheritorSearches() {
    val byMethod = inheritorSearchStatistics()
    appendSearchSummary("Inheritor searches", "unique methods", byMethod.values)
    appendLine("Slowest classes for the inheritor search (top $SLOWEST_CLASSES_LIMIT):")
    appendLine("Time is the summed time of the searches for the overriding methods of the class methods. It does not include the enqueue of the results.")
    appendLine("Results is the number of the methods that the searches found, including the searched method.")
    appendLine("Cache hits is the number of the requests that got the results from the cache and did not run a search.")
    appendLine("The lines below each class show its slowest methods (top $SLOWEST_METHODS_PER_CLASS_LIMIT).")
    appendLine(String.format(Locale.ROOT, "%14s %9s %11s %8s %15s %8s  %s", "time", "searches", "cache hits", "methods", "longest search", "results", "class"))
    byMethod.entries
      .groupBy { it.key.containingClassName }
      .map { (className, methods) -> Triple(className, methods, methods.map { it.value }.reduce(SearchStatistics::plus)) }
      .sortedWith(compareByDescending<Triple<String, *, SearchStatistics>> { it.third.totalNanos }.thenBy { it.first })
      .take(SLOWEST_CLASSES_LIMIT)
      .forEach { (className, methods, total) ->
        appendLine(String.format(Locale.ROOT, "%14s %9d %11d %8d %15s %8d  %s", total.totalNanos.nanoseconds.format(), total.searches, total.cacheHits,
                                 methods.size, total.maxNanos.nanoseconds.format(), total.results, className))
        methods
          .sortedWith(compareByDescending<Map.Entry<MethodSignature, SearchStatistics>> { it.value.totalNanos }.thenBy { it.key.toPresentableString() })
          .take(SLOWEST_METHODS_PER_CLASS_LIMIT)
          .forEach { (signature, stats) ->
            appendLine(String.format(Locale.ROOT, "%14s %9d %11d %8s %15s %8d    %s", stats.totalNanos.nanoseconds.format(), stats.searches,
                                     stats.cacheHits, "", stats.maxNanos.nanoseconds.format(), stats.results,
                                     "${signature.methodName}(${signature.parameterTypes.joinToString(", ")})"))
          }
      }
  }

  /**
   * Appends the message bus topic classes with the slowest listener searches.
   */
  private fun StringBuilder.appendListenerSearches() {
    val byTopic = listenerSearchStatistics()
    appendSearchSummary("Listener searches", "unique topic classes", byTopic.values)
    appendLine("Slowest topic classes for the listener search (top $SLOWEST_CLASSES_LIMIT):")
    appendLine("Time is the summed time of the searches for the listener classes. It does not include the enqueue of the results.")
    appendLine("Results is the number of the listener classes that the searches found.")
    appendLine("Cache hits is the number of the requests that got the results from the cache and did not run a search.")
    appendLine(String.format(Locale.ROOT, "%14s %9s %11s %15s %8s  %s", "time", "searches", "cache hits", "longest search", "results", "topic class"))
    byTopic.entries
      .sortedWith(compareByDescending<Map.Entry<String, SearchStatistics>> { it.value.totalNanos }.thenBy { it.key })
      .take(SLOWEST_CLASSES_LIMIT)
      .forEach { (topicClassName, stats) ->
        appendLine(String.format(Locale.ROOT, "%14s %9d %11d %15s %8d  %s", stats.totalNanos.nanoseconds.format(), stats.searches, stats.cacheHits,
                                 stats.maxNanos.nanoseconds.format(), stats.results, topicClassName))
      }
  }

  /**
   * Appends the number of the searches and the cache hits.
   * A repeated search is a search for an element that was searched before.
   * It occurs when the cache does not keep the element, or when the cached results become invalid.
   */
  private fun StringBuilder.appendSearchSummary(title: String, uniqueTitle: String, statistics: Collection<SearchStatistics>) {
    val searched = statistics.count { it.searches > 0 }
    val searches = statistics.sumOf { it.searches }
    val cacheHits = statistics.sumOf { it.cacheHits }
    appendLine("$title: $searches ($uniqueTitle: $searched, repeated searches: ${searches - searched}, cache hits: $cacheHits)")
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
    private const val SLOWEST_CLASSES_LIMIT = 50
    private const val SLOWEST_METHODS_PER_CLASS_LIMIT = 3
    private val CACHE_HIT = SearchStatistics(searches = 0, totalNanos = 0, maxNanos = 0, results = 0, cacheHits = 1)

    private fun MethodSignature.toPresentableString(): String = "$containingClassName.$methodName(${parameterTypes.joinToString(", ")})"

    private fun Duration.format(): String = String.format(Locale.ROOT, "%.3fms", toDouble(DurationUnit.MILLISECONDS))

    private fun share(partNanos: Long, totalNanos: Long): String {
      return if (totalNanos <= 0) "-" else String.format(Locale.ROOT, "%.1f%%", partNanos * 100.0 / totalNanos)
    }

    /**
     * Lists the phases with a non-zero time, the longest phase first.
     */
    private fun LockReqPhaseTimer.Snapshot.formatBreakdown(totalNanos: Long): String {
      return Phase.entries
        .filter { timeNanos(it) > 0 }
        .sortedByDescending { timeNanos(it) }
        .joinToString("; ") { phase ->
          val details = if (phase == Phase.OTHER) share(timeNanos(phase), totalNanos)
          else "${share(timeNanos(phase), totalNanos)}, ${count(phase)}x, longest ${maxTime(phase).format()}"
          "${phase.presentableName} ${time(phase).format()} ($details)"
        }
    }
  }
}
