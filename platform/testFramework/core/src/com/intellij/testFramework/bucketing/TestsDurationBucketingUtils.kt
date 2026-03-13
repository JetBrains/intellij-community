// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.bucketing

import com.intellij.GroupBasedTestClassFilter
import com.intellij.TestCaseLoader
import com.intellij.TestCaseLoader.TEST_RUNNERS_COUNT
import com.intellij.TestCaseLoader.TEST_RUNNER_INDEX
import com.intellij.testFramework.TeamCityLogger
import org.jetbrains.annotations.ApiStatus
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.PriorityQueue
import java.util.TreeSet
import java.util.function.BiFunction
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.io.path.createParentDirectories
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.outputStream
import kotlin.io.path.useLines
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
internal object TestsDurationBucketingUtils {
  data class BucketClassFilter(val classes: Set<String>)

  /**
   * `null` as entry value in `packageClasses` means all classes in package
   */
  data class BucketFilter(val index: Int, val packageClasses: Map<String, BucketClassFilter?>)

  @JvmStatic
  fun calculateBucketFilters(filter: TestCaseLoader.TestClassesFilterArgs, classes: Set<String>): List<BucketFilter> {
    val classesDurations: MutableMap<String, Int> = HashMap(loadDurationData(filter))
    val loadedSize = classesDurations.size
    classesDurations.keys.retainAll(classes)
    val relevantSize = classesDurations.size
    System.out.printf("Loaded tests duration for %d classes. Filtered out of scope ones, left with %d relevant classes%n",
                      loadedSize, relevantSize)

    var missing: MutableSet<String> = HashSet(classes)
    missing.removeAll(classesDurations.keys)
    missing.remove("_FirstInSuiteTest")
    missing.remove("_LastInSuiteTest")
    missing = TreeSet(missing)
    if (missing.isNotEmpty()) {
      System.out.printf("%d classes are in scope, but without duration info:%n", missing.size)
      for (name in missing) {
        System.out.printf("  %s%n", name)
      }
    }
    if (TeamCityLogger.isUnderTC) {
      println(String.format("##teamcity[buildStatisticValue key='testDurationClasses.loaded' value='%d']", loadedSize))
      println(String.format("##teamcity[buildStatisticValue key='testDurationClasses.relevant' value='%d']", relevantSize))
      println(String.format("##teamcity[buildStatisticValue key='testDurationClasses.missing' value='%d']", missing.size))
    }

    if (classesDurations.isEmpty()) return emptyList()
    val filters = getBucketFilter(classesDurations, TEST_RUNNERS_COUNT, TEST_RUNNER_INDEX)
    dump(filter, classes, classesDurations, filters)
    return filters
  }

  @JvmStatic
  fun loadSeasonBucketFilters(
    filter: TestCaseLoader.TestClassesFilterArgs,
    seasonData: Map<String, Int>,
  ): List<BucketFilter> {
    val filters = getBucketFilter(seasonData, TEST_RUNNERS_COUNT, TEST_RUNNER_INDEX)
    dump(filter, null, seasonData, filters)
    return filters
  }

  @JvmStatic
  fun loadSeasonData(season: String?): Map<String, Int>? {
    if (season == null) return null

    val files = getDataDirectories().map { it.resolve("seasons/$season.csv") }.filter { Files.isRegularFile(it) }.distinct().toList()
    if (files.isEmpty()) {
      System.err.println("No CSV file for season '$season' found")
      return null
    }
    if (files.size > 1) {
      System.err.println("Multiple files for season '$season' found, will use the first one: ${files.joinToString { it.absolutePathString() }}")
    }
    val result = HashMap<String, Int>()
    loadCSV(files.first(), result)
    return result
  }

  private fun dump(
    filter: TestCaseLoader.TestClassesFilterArgs,
    classes: Set<String>?,
    durations: Map<String, Int>,
    filters: List<BucketFilter>,
  ) {
    try {
      val dumpData = mutableMapOf(
        "filter" to filter,
        "index" to TEST_RUNNER_INDEX,
        "count" to TEST_RUNNERS_COUNT,
        "durations" to durations.toSortedMap(),
        "buckets" to filters,
      )
      if (classes != null) {
        dumpData["classes"] = classes.sorted()
      }

      val objectMapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build()
      val outputFile = Path.of(System.getProperty("java.io.tmpdir"))
        .resolve("test-bucketing-dump-${System.currentTimeMillis()}.json")
        .createParentDirectories()
      outputFile.outputStream().buffered().use { objectMapper.writeValue(it, dumpData) }

      println("Dumped bucketing data to: $outputFile")
      if (TeamCityLogger.isUnderTC) {
        println("##teamcity[publishArtifacts '${outputFile.absolutePathString()}']")
      }
    }
    catch (e: Exception) {
      System.err.println("Failed to dump bucketing data: ${e.message}")
      e.printStackTrace()
    }
  }

  /**
   * @param statistics map from class name to test duration
   */
  @Suppress("SameParameterValue")
  private fun getBucketFilter(statistics: Map<String, Int>, bucketsCount: Int, currentBucketIndex: Int): List<BucketFilter> {
    require(bucketsCount > 0) {
      "Total buckets count '$bucketsCount' must be greater than zero"
    }

    val buckets = createBucketsFromTestsStatistics(statistics, bucketsCount)
    val groupsPerPackages = buckets.flatMap { it.items }.groupBy { it.packageName }

    val filters = buckets.mapIndexed { index, bucket ->
      val packageClassesForCurrentBucket: MutableMap<String, BucketClassFilter?> = HashMap(bucket.items.map { it.packageName }.distinct().count())
      bucket.items.forEach {
        if (groupsPerPackages.getValue(it.packageName).size == 1) {
          packageClassesForCurrentBucket[it.packageName] = null
          return@forEach
        }
        // We might have multiple BucketClassFilter for the same package, combine them
        val classNames = it.classes.mapTo(LinkedHashSet(it.classes.size)) { cls -> cls.key }
        packageClassesForCurrentBucket.compute(it.packageName, BiFunction { _, previous ->
          if (previous == null) return@BiFunction BucketClassFilter(classNames)
          BucketClassFilter(previous.classes + classNames)
        })
      }
      BucketFilter(index, packageClassesForCurrentBucket)
    }

    val averageTime = (buckets.sumOf { it.totalTime.inWholeMilliseconds } / bucketsCount).milliseconds
    println("*** Calculated bucket partitions, average bucket time is ${averageTime}")
    if (TeamCityLogger.isUnderTC) {
      println(String.format("##teamcity[buildStatisticValue key='buckets.averageMs' value='%d']", averageTime.inWholeMilliseconds))
      val bucket = buckets.getOrNull(currentBucketIndex)
      println(String.format("##teamcity[buildStatisticValue key='buckets.currentMs' value='%d']", bucket?.totalTime?.inWholeMilliseconds ?: 0))
    }
    filters.forEachIndexed { index, filter ->
      val bucket = buckets[index]
      val current = if (index == currentBucketIndex) " (current)" else ""
      println(
        "  Bucket ${index + 1}$current, total time: ${bucket.totalTime}, total packages: ${bucket.items.size}, total classes: ${bucket.items.sumOf { it.classes.size }}:")
      println(filter.packageClasses.entries.sortedBy { it.key }.joinToString(separator = "\n") {
        val pkg = it.key
        val value = it.value
        "    ${pkg.ifEmpty { "<root>" }} => " + when (value) {
          null -> "whole package"
          else -> value.classes.joinToString(prefix = "only classes: [", postfix = "]") { cls ->
            cls.removePrefix(pkg).removePrefix(".")
          }
        }
      })
    }
    return filters
  }

  private data class PackageClassesGroup(val packageName: String,
                                         val classes: List<Map.Entry<String, Int>>,
                                         val groupTime: Duration,
                                         val groupIndex: Int)

  private fun createBucketsFromTestsStatistics(statistics: Map<String, Int>,
                                               bucketsCount: Int): List<ItemsAndTotalTime<PackageClassesGroup>> {
    val partitionPerPackages = statistics.entries.groupBy { it.packageName() }.map {
      ItemsAndTotalTime(it.value, it.value.sumOf { it.value }.milliseconds)
    }
    val averageTime = (partitionPerPackages.sumOf { it.totalTime.inWholeMilliseconds } / bucketsCount).milliseconds
    val deltaTimeMax = averageTime * 0.10

    val partition = partitionPerPackages.sortedBy { it.items[0].packageName() }.flatMap {
      if (it.totalTime < averageTime + deltaTimeMax)
        return@flatMap listOf(PackageClassesGroup(it.items[0].packageName(), it.items, it.totalTime, 0))

      val result = mutableListOf<PackageClassesGroup>()
      val pendingClasses = it.items.sortedBy { it.key }.toMutableList()
      while (pendingClasses.isNotEmpty()) {
        val group = mutableListOf(pendingClasses.removeFirst())
        var groupTime = group[0].value.milliseconds

        pendingClasses.removeIf { pending ->
          if (groupTime < averageTime &&
              groupTime + pending.value.milliseconds < averageTime + deltaTimeMax) {
            group.add(pending)
            groupTime += pending.value.milliseconds
            true
          }
          else
            false
        }

        result.add(PackageClassesGroup(group[0].packageName(), group, groupTime, result.size))
      }

      result
    }

    return tossElementsIntoBuckets(partition.map { Pair(it, it.groupTime) }, bucketsCount)
  }

  private data class ItemsAndTotalTime<T>(val items: List<T>, val totalTime: Duration)

  private fun <T> tossElementsIntoBuckets(elements: List<Pair<T, Duration>>, binCount: Int): List<ItemsAndTotalTime<T>> {
    val queue = PriorityQueue<ItemsAndTotalTime<T>>(binCount, Comparator.comparing { it.totalTime })
    (0 until binCount).forEach { _ ->
        queue.add(ItemsAndTotalTime(emptyList(), ZERO))
    }

    for (element in elements.sortedByDescending { it.second }) {
      val smallestBin = queue.poll()
      queue.add(ItemsAndTotalTime(smallestBin.items + element.first, smallestBin.totalTime + element.second))
    }

    return queue.sortedBy { it.totalTime }
  }

  private fun loadDurationData(filter: TestCaseLoader.TestClassesFilterArgs): Map<String, Int> {
    val groupsToLoad = getGroupsToLoad(filter)
    val directories = getDataDirectories()
    for (directory in directories) {
      val result = HashMap<String, Int>()
      try {
        val files = directory.listDirectoryEntries("*.csv")
          .filter { groupsToLoad == null || it.nameWithoutExtension in groupsToLoad }
        for (path in files) {
          loadCSV(path, result)
        }
      }
      catch (e: IOException) {
        System.err.println("Failed to load test classes duration from files in '$directory': ${e.message}")
      }
      // load only from the first directory
      return result
    }
    return emptyMap()
  }

  private fun getDataDirectories(): Sequence<Path> {
    // Guess project directory, data located under ultimate repo and not available in the community version.
    val directories = setOfNotNull(
      System.getenv("JPS_PROJECT_HOME"),
      System.getenv("JPS_BOOTSTRAP_COMMUNITY_HOME"),
      System.getProperty("idea.home.path"),
      System.getProperty("user.dir"),
    )
      .asSequence()
      .map { Path.of(it).absolute() }
      .mapNotNull { path ->
        val res = when {
          Files.exists(path.resolve(".ultimate.root.marker")) -> path
          Files.exists(path.resolve("intellij.idea.community.main.iml")) -> path.parent
          else -> null
        }
        println("Probing '$path', result is '$res'")
        res
      }
      .map { it.resolve("tests/classes-duration/") }
      .filter(Files::isDirectory)
    return directories
  }

  private fun loadCSV(path: Path, result: MutableMap<String, Int>) {
    try {
      path.useLines(StandardCharsets.UTF_8) { lines ->
        lines.forEach { line ->
          val split = line.split(',', limit = 3)
          if (split.size == 2) {
            val name = split[0]
            var duration = split[1].toInt()
            val previous = result[name]
            if (previous != null) {
              if (previous != duration) {
                System.err.println("Conflicting test duration for '$name': $previous vs $duration")
                duration = maxOf(previous, duration)
              }
            }
            result[name] = duration
          }
        }
      }
    }
    catch (e: Exception) {
      System.err.println("Failed to load test classes duration from '$path': ${e.message}")
    }
  }

  private fun getGroupsToLoad(filter: TestCaseLoader.TestClassesFilterArgs): List<String>? {
    if (!filter.patterns.isNullOrEmpty()) return null
    val testGroupNames = filter.testGroupNames
    if (testGroupNames == null) return null
    if (testGroupNames.contains("ALL")) return null
    if (testGroupNames.contains(GroupBasedTestClassFilter.ALL_EXCLUDE_DEFINED)) return null
    return testGroupNames
  }

  private fun <V> Map.Entry<String, V>.packageName(): String {
    return key.substringBeforeLast('.', "")
  }
}
