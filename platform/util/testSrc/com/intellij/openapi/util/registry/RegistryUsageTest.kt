// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.registry

import java.time.LocalDate
import java.util.Properties

private const val statisticsFile = "registryUsageStatistics.csv"
private const val annotationFile = "registry.properties.annotation"
private const val USAGE_COUNT_THRESHOLD = 1
private const val LIFE_TIME_YEARS_THRESHOLD = 3

/**
 * Detects unused or rarely used keys in `registry.properties`
 *
 * Usage details
 * * Export usage statistics for several last releases and maximum available last period
 * into `community/platform/util/testResources/com/intellij/openapi/util/registry/registryUsageStatistics.csv`
 * (e.g., https://data.jetbrains.do/statistics/query/26eec9ab39df461196258d82257f7c6f?st=group-with-segments)
 * * Go to the repo root and update annotations in `registry.properties.annotation` from console:
 * ```shell
 * git annotate community/platform/util/resources/misc/registry.properties > community/platform/util/testResources/com/intellij/openapi/util/registry/registry.properties.annotation
 * ```
 * * Run the main method
 * * Check the output
 */
fun main() {
  val usageStatistics = loadStatisticsFile()
  val allPlatformRegistryKeys = loadPlatformRegistryKeys()
  val annotations = loadAnnotations()

  require(allPlatformRegistryKeys.toSet() == annotations.keys) { "Inconsistent annotations and registry keys" }

  dumpOutdatedKeys("Not used keys, older than $LIFE_TIME_YEARS_THRESHOLD years", allPlatformRegistryKeys - usageStatistics.keys, annotations)
  println()
  dumpOutdatedKeys("Used less than $USAGE_COUNT_THRESHOLD time(s), older than $LIFE_TIME_YEARS_THRESHOLD years",
                   usageStatistics.filter { it.value <= USAGE_COUNT_THRESHOLD && allPlatformRegistryKeys.contains(it.key) }.keys,
                   annotations)
}

private fun dumpOutdatedKeys(description: String, keys: Collection<String>, annotations: Map<String, LocalDate>) {
  val outdatedKeys = keys
    .filter { isKeyOutdated(it, annotations) }
    .sorted()
  println(outdatedKeys.joinToString("\n", "$description (${keys.size} count):\n"))
}

private fun isKeyOutdated(key: String, annotations: Map<String, LocalDate>): Boolean {
  val annotationDate = annotations[key]
  requireNotNull(annotationDate) { "No annotation for key $key" }

  return annotationDate.isBefore(LocalDate.now().minusYears(LIFE_TIME_YEARS_THRESHOLD.toLong()))
}

private fun loadStatisticsFile(): Map<String, Int> {
  val resource = object {}.javaClass.getResourceAsStream(statisticsFile)
  requireNotNull(resource) { "$statisticsFile not found" }

  val regex = Regex("""^registry,([\w.]+),[\d.]+,[\d.]+,[\d.]+,(\d+)$""")
  val result = mutableMapOf<String, Int>()

  resource.bufferedReader().useLines { lines ->
    lines.forEach { line ->
      val match = regex.find(line)
      if (match != null) {
        val key = match.groupValues[1]
        val usageCount = match.groupValues[2].toIntOrNull()

        require(key.isNotEmpty()) { "Invalid key: $key" }
        requireNotNull(usageCount) { "Invalid usage count: $usageCount" }
        require(result.put(key, usageCount) == null) { "Duplicated key $key" }
      }
    }
  }

  require(result.isNotEmpty()) { "$statisticsFile is empty" }

  return result
}

private fun loadPlatformRegistryKeys(): List<String> {
  val resource = Registry::class.java.classLoader.getResourceAsStream("misc/registry.properties")
  requireNotNull(resource) { "Registry file not found" }

  val properties = Properties()
  properties.load(resource)

  return properties.keys().toList()
    .filterIsInstance<String>()
    .filter { it.isPropertyKey() }
}

private fun loadAnnotations(): Map<String, LocalDate> {
  val resource = object {}.javaClass.getResourceAsStream(annotationFile)
  requireNotNull(resource) { "$annotationFile not found" }

  val regex = Regex("""^.+\t\(.+\t([0-9-]+) .+\)(\S+)=.*$""")
  val result = mutableMapOf<String, LocalDate>()

  resource.bufferedReader().useLines { lines ->
    lines.forEach { line ->
      val match = regex.find(line)
      if (match != null) {
        val dateString = match.groupValues[1]
        val date = LocalDate.parse(dateString)
        val key = match.groupValues[2]

        if (key.isPropertyKey()) {
          require(result.put(key, date) == null) { "Duplicated key $key" }
        }
      }
    }
  }

  require(result.isNotEmpty()) { "$annotationFile is empty" }

  return result
}

private fun String.isPropertyKey(): Boolean {
  return !endsWith(".description") && !endsWith(".restartRequired")
}