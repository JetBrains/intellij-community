// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tool

import com.intellij.TestCaseLoader
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*

/**
 * Process with cache might be spawn / killed multiple times during aggregator test run.
 * Disk cache is used to store the data, that already has been requested.
 */
object NastradamusCache : Cache(
  cacheDir = Paths.get("CacheDir").apply {
    createDirectories()
    if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED)
      println("Creating cache dir ${this.toRealPath()}")
  }.toRealPath()
)

abstract class Cache(val cacheDir: Path) {
  private val dataCache: ConcurrentHashMap<String, String> = ConcurrentHashMap()

  init {
    /**
     * Try to initialize cache from cache directory
     */
    if (dataCache.isEmpty()) {
      val cacheEntries = cacheDir.listDirectoryEntries()

      if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED) {
        println("Reading from cache dir ${cacheDir.toRealPath()} ${cacheEntries.size} entries")
      }

      cacheEntries.forEach { filePath ->
        dataCache[filePath.nameWithoutExtension] = filePath.readText()
      }
    }
  }

  fun eraseCache() {
    dataCache.clear()
    cacheDir.listDirectoryEntries().forEach { it.toFile().deleteRecursively() }
  }

  private fun <T> getHashedKey(key: T) = key.toString().hashCode().toString()

  fun <T> put(key: T, value: String): Unit {
    val hashedKey = getHashedKey(key)

    dataCache[hashedKey] = value
    cacheDir.resolve(hashedKey).apply {
      createFile()
      writeText(value)
    }
  }

  fun <T> get(key: T): String? {
    val hashedKey = getHashedKey(key)

    val value = dataCache[hashedKey]

    if (TestCaseLoader.IS_VERBOSE_LOG_ENABLED && value != null) {
      println("Returning cached result for $key / hashed as: $hashedKey")
    }

    return value
  }

  /**
   * If data is missing then calculated value will be put to cache and returned
   */
  fun <T> get(key: T, calculatedValue: () -> String): String {
    var value: String? = get(key)

    if (value != null) return value

    value = calculatedValue()
    put(key, value)
    return value
  }
}