// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests


import org.junit.platform.engine.FilterResult
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.PostDiscoveryFilter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.IdentityHashMap
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

private val included = FilterResult.included(null)
private val excluded = FilterResult.excluded(null)

/**
 * A filter that implements Bazel sharding support.
 *
 * Without a shard plan, each test goes to the shard of the hash of its unique id.
 *
 * With a shard plan ([SHARD_PLAN_ENV]), the unit is a top-level class (a child of the engine root), so only one shard
 * pays the class setup. The plan gives the target its number of shards and the recorded seconds of each class. The
 * filter puts each class into the least loaded shard, the heaviest class first. A class that is more than a quarter
 * heavier than the fair share of a shard is split by test over as many of the lightest shards as its weight needs.
 * A shard at or above the planned number runs no test ([isIdle]).
 *
 * Each shard is a separate JVM, and each computes the same partition: the discovered tree and the plan are the same in
 * each shard, and the partition depends only on them. A shard can still read another version of the plan than the other
 * shards of its target, so each shard reports the [planDigest] of the plan it read.
 */
class ShardFilter private constructor(
  totalShards: Int,
  private val shardIndex: Int,
  private val plan: ShardPlan?,
  /**
   * The digest of the lines of the shard plan that this target reads, or [NO_PLAN_DIGEST] when the shard could not read
   * the plan. `null` when [SHARD_PLAN_ENV] is not set. The runner writes it into the test report, and `bt` checks that
   * all shards of a target read the same plan.
   */
  val planDigest: String?,
) : PostDiscoveryFilter {
  private val shardCount = if (plan == null) totalShards else min(plan.shardCount, totalShards)

  // the state of the class partition; the loads carry over from one engine root to the next
  private val loads = DoubleArray(shardCount)
  private val partitionedRoots = HashSet<TestDescriptor>()
  private val shardOfDescriptor = IdentityHashMap<TestDescriptor, Int>()

  private var excludedTests = false

  /**
   * Whether the shard plan gives the target fewer shards than Bazel runs, so that this shard runs no test.
   */
  val isIdle: Boolean
    get() = shardIndex >= shardCount

  /**
   * Whether the filter excluded a test. Then a shard without tests is a valid shard of a target that has tests.
   */
  fun hasExcludedTests(): Boolean = excludedTests

  override fun apply(testDescriptor: TestDescriptor): FilterResult {
    // classes, nested classes, engine root, etc.
    if (!testDescriptor.isShardItem()) {
      return included
    }
    val shard = if (plan == null) testDescriptor.hashShard() else testDescriptor.classShard(plan)
    if (shard == shardIndex) {
      return included
    }
    excludedTests = true
    return excluded
  }

  private fun TestDescriptor.hashShard() = abs(uniqueId.toString().hashCode() % shardCount)

  private fun TestDescriptor.classShard(plan: ShardPlan): Int {
    val unit = unit()
    val root = unit.parent.orElse(unit)
    if (partitionedRoots.add(root)) {
      partition(root, plan)
    }
    return shardOfDescriptor.get(this) ?: shardOfDescriptor.get(unit) ?: hashShard()
  }

  private fun partition(root: TestDescriptor, plan: ShardPlan) {
    val units = root.children.map { descriptor ->
      val items = ArrayList<TestDescriptor>()
      descriptor.collectShardItems(items)
      ShardUnit(descriptor, items, descriptor.recordedSeconds(plan))
    }

    // a class without a record weighs as much per test as the recorded classes do on average
    val recorded = units.filter { it.recordedSeconds != null }
    val recordedItems = recorded.sumOf { it.items.size }
    val secondsPerItem = if (recordedItems == 0) 1.0 else recorded.sumOf { it.recordedSeconds!! } / recordedItems
    for (unit in units) {
      // a small weight per test keeps the classes recorded at zero seconds from piling up in one shard
      unit.weight = (unit.recordedSeconds ?: (secondsPerItem * unit.items.size)) + unit.items.size * 0.001
    }

    // a split repeats the class setup in each part, so a class only a little over the fair share stays whole
    val splitShare = (loads.sum() + units.sumOf { it.weight }) / shardCount * 1.25
    val allShards = loads.indices.toList()
    for (unit in units.sortedWith(compareByDescending<ShardUnit> { it.weight }.thenBy { it.descriptor.uniqueId.toString() })) {
      val parts = min(shardCount, ceil(unit.weight / splitShare).toInt())
      if (parts > 1 && unit.items.size > 1) {
        val shards = allShards.sortedWith(compareBy<Int> { loads[it] }.thenBy { it }).take(parts)
        val itemWeight = unit.weight / unit.items.size
        for (item in unit.items.sortedBy { it.uniqueId.toString() }) {
          shardOfDescriptor.put(item, addToLightestShard(shards, itemWeight))
        }
      }
      else {
        shardOfDescriptor.put(unit.descriptor, addToLightestShard(allShards, unit.weight))
      }
    }
  }

  private fun addToLightestShard(shards: List<Int>, weight: Double): Int {
    var lightest = shards.first()
    for (index in shards) {
      if (loads[index] < loads[lightest]) {
        lightest = index
      }
    }
    loads[lightest] += weight
    return lightest
  }

  companion object {
    /**
     * The path of the shard plan file. `bt` writes the file and passes the path to a multi-target run. A relative path is
     * relative to the checkout.
     *
     * The file has tab-separated lines, and the filter ignores other lines:
     * - `default <shards>`: the number of shards of a target without a `target` line;
     * - `target <label> <shards>`: the number of shards of the target with the label of `TEST_TARGET`;
     * - `class <label> <class> <seconds>`: the recorded seconds of a class, as the suite of the class in the test report.
     */
    const val SHARD_PLAN_ENV: String = "JB_TEST_SHARD_PLAN"

    /**
     * The [planDigest] of a shard that could not read the plan, so that it differs from the digest of a shard that could.
     */
    const val NO_PLAN_DIGEST: String = "none"

    @JvmStatic
    fun create(): ShardFilter? {
      val totalShards = System.getenv("TEST_TOTAL_SHARDS") ?: return null
      val shardIndex = System.getenv("TEST_SHARD_INDEX") ?: return null
      val planValue = System.getenv(SHARD_PLAN_ENV)?.takeIf { it.isNotBlank() }
      val plan = planValue?.let { readPlan(it, System.getenv("TEST_TARGET").orEmpty()) }
      try {
        println("Configuring shard $shardIndex of $totalShards" + (plan?.let { ", ${it.shardCount} planned, by class" } ?: ""))
        return ShardFilter(totalShards.toInt(), shardIndex.toInt(), plan, planValue?.let { plan?.digest() ?: NO_PLAN_DIGEST })
      }
      catch (e: NumberFormatException) {
        throw RuntimeException("Failed to parse sharding environment variables", e)
      }
    }

    /**
     * A filter for one of [totalShards] shards, with a shard plan in the format of [SHARD_PLAN_ENV] or without one.
     */
    @JvmStatic
    fun create(totalShards: Int, shardIndex: Int, planText: String?, target: String): ShardFilter {
      val plan = planText?.let { parsePlan(it, target) }
      return ShardFilter(totalShards, shardIndex, plan, planText?.let { plan?.digest() ?: NO_PLAN_DIGEST })
    }

    @JvmStatic
    fun writeShardStatus() {
      val file = System.getenv("TEST_SHARD_STATUS_FILE") ?: return
      try {
        Path.of(file).writeText("")
      }
      catch (e: IOException) {
        throw RuntimeException("Failed to write Shard Status File '$file'", e)
      }
    }

    // A plan that cannot be read leaves the hash of each test. Every shard reads the same file, so all of them agree.
    private fun readPlan(value: String, target: String): ShardPlan? {
      val file = try {
        Path.of(value).let { if (it.isAbsolute) it else findCheckout().resolve(it) }
      }
      catch (e: Exception) {
        System.err.println("Cannot find the checkout of the shard plan $value, sharding by test: $e")
        return null
      }
      val text = try {
        file.readText()
      }
      catch (_: NoSuchFileException) {
        System.err.println("No shard plan at $file, sharding by test")
        return null
      }
      catch (e: IOException) {
        System.err.println("Cannot read the shard plan $file, sharding by test: $e")
        return null
      }
      return parsePlan(text, target) ?: run {
        System.err.println("The shard plan $file is malformed, sharding by test")
        null
      }
    }

    private fun parsePlan(text: String, target: String): ShardPlan? {
      var defaultShards: Int? = null
      var targetShards: Int? = null
      val classSeconds = HashMap<String, Double>()
      for (line in text.lineSequence()) {
        val fields = line.split('\t')
        when (fields.first()) {
          "default" -> defaultShards = fields.getOrNull(1)?.toIntOrNull() ?: return null
          "target" -> if (fields.getOrNull(1) == target) {
            targetShards = fields.getOrNull(2)?.toIntOrNull() ?: return null
          }
          "class" -> if (fields.getOrNull(1) == target) {
            classSeconds.put(fields.getOrNull(2) ?: return null, fields.getOrNull(3)?.toDoubleOrNull() ?: return null)
          }
        }
      }
      val shardCount = targetShards ?: defaultShards ?: return null
      return if (shardCount < 1) null else ShardPlan(shardCount, classSeconds)
    }
  }
}

/**
 * The checkout of this test. A sandboxed test has no source file of the checkout in its runfiles, but its runfiles are
 * in the execroot of the output base, and each top-level entry of that execroot links to the checkout.
 */
private fun findCheckout(): Path {
  val runfiles = Path.of(System.getenv("TEST_SRCDIR") ?: error("TEST_SRCDIR is not set")).toAbsolutePath().toString().replace('\\', '/')
  val execRootStart = runfiles.indexOf("/execroot/").takeIf { it >= 0 } ?: error("The runfiles $runfiles are not in an execroot")
  // a sandbox has an execroot of its own at <output base>/sandbox/<strategy>/<number>/execroot
  val outputBase = runfiles.substring(0, execRootStart).let { Regex("(.*)/sandbox/[^/]+/\\d+").matchEntire(it)?.groupValues?.get(1) ?: it }
  val workspace = runfiles.substring(execRootStart + "/execroot/".length).substringBefore('/')
  val execRoot = Path.of(outputBase, "execroot", workspace)
  for (entry in listOf("MODULE.bazel", "build")) {
    val link = execRoot.resolve(entry)
    if (Files.exists(link)) {
      return link.toRealPath().parent
    }
  }
  error("No entry of $execRoot links to the checkout")
}

private class ShardPlan(@JvmField val shardCount: Int, @JvmField val classSeconds: Map<String, Double>) {
  fun digest(): String {
    val text = buildString {
      append("shards\t").append(shardCount).append('\n')
      for ((name, seconds) in classSeconds.toSortedMap()) {
        append(name).append('\t').append(seconds).append('\n')
      }
    }
    val hash = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
    return HexFormat.of().formatHex(hash, 0, 8)
  }
}

private class ShardUnit(
  @JvmField val descriptor: TestDescriptor,
  @JvmField val items: List<TestDescriptor>,
  @JvmField val recordedSeconds: Double?,
) {
  @JvmField
  var weight: Double = 0.0
}

/**
 * A test, or a method-backed container (e.g., @ParameterizedTest, @TestFactory, @RepeatedTest). Such a container goes
 * into one shard because the invocations are produced dynamically at execution time.
 */
private fun TestDescriptor.isShardItem(): Boolean = isTest || (isContainer && source.orElse(null) is MethodSource)

private fun TestDescriptor.collectShardItems(items: MutableList<TestDescriptor>) {
  if (isShardItem()) {
    items.add(this)
    return
  }
  children.forEach { it.collectShardItems(items) }
}

/**
 * The recorded seconds of a class. A test report has a suite per class with direct tests, a nested class included. The
 * suite of a class lasts until its nested classes end, so the record of a class already holds the records below it.
 */
private fun TestDescriptor.recordedSeconds(plan: ShardPlan): Double? {
  (source.orElse(null) as? ClassSource)?.let { plan.classSeconds.get(it.className) }?.let { return it }
  val nested = children.filter { !it.isShardItem() }.mapNotNull { it.recordedSeconds(plan) }
  return if (nested.isEmpty()) null else nested.sum()
}

/**
 * The child of the engine root that holds this descriptor: a top-level class for Jupiter, a runner for Vintage.
 */
private fun TestDescriptor.unit(): TestDescriptor {
  var unit = this
  while (true) {
    val parent = unit.parent.orElse(null) ?: return unit
    if (parent.parent.isEmpty) {
      return unit
    }
    unit = parent
  }
}
