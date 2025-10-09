// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.registry

import java.util.*

private const val statFile = "registryUsageStatistics.csv"
private const val USAGE_THRESHOLD = 1

/**
 * Detects unused or rarely used keys in `registry.properties`
 *
 * Usage details
 * * Export usage statistics for several last releases and maximum available last period
 * into `community/platform/util/testResources/com/intellij/openapi/util/registry/registryUsageStatistics.csv`
 * (see https://data.jetbrains.do/statistics/query/26eec9ab39df461196258d82257f7c6f?st=group-with-segments)
 * * Run the main method
 */
fun main() {
  val usageStat = loadStatFile()
  val allPlatformRegistryKeys = loadPlatformRegistryKeys()

  //dumpKeys("Non platform keys found", usageStat.keys - allPlatformRegistryKeys)
  println()
  dumpKeys("Not used keys", allPlatformRegistryKeys - usageStat.keys)
  println()
  dumpKeys("Used less than $USAGE_THRESHOLD time(s)",
           usageStat.filter { it.value <= USAGE_THRESHOLD && allPlatformRegistryKeys.contains(it.key) }.keys)
}

private fun dumpKeys(description: String, keys: Collection<String>) {
  println(keys.sorted().joinToString("\n", "$description (${keys.size} count):\n"))
}

private fun loadStatFile(): Map<String, Int> {
  val resource = object {}.javaClass.getResourceAsStream(statFile)
  requireNotNull(resource) { "$statFile not found" }

  val regex = Regex("""^registry,([\w.]+),[\d.]+,[\d.]+,[\d.]+,(\d+)$""")
  val result = mutableMapOf<String, Int>()

  resource.bufferedReader().useLines { lines ->
    lines.forEach { line ->
      val match = regex.find(line)
      if (match != null) {
        val key = match.groupValues[1]
        val usageCount = match.groupValues[2].toIntOrNull()

        if (key.isNotEmpty() && usageCount != null) {
          require(result.put(key, usageCount) == null) { "Duplicated key $key" }
        }
      }
    }
  }

  require(result.isNotEmpty()) { "$statFile is empty" }

  return result
}

private fun loadPlatformRegistryKeys(): List<String> {
  val resource = Registry::class.java.classLoader.getResourceAsStream("misc/registry.properties")
  requireNotNull(resource) { "Registry file not found" }

  val properties = Properties()
  properties.load(resource)

  return properties.keys().toList()
    .filterIsInstance<String>()
    .filter { !it.endsWith(".description") && !it.endsWith(".restartRequired") }
}
