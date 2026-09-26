// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests


import org.junit.platform.engine.FilterResult
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.PostDiscoveryFilter
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.math.abs

private val included = FilterResult.included(null)
private val excluded = FilterResult.excluded(null)

/**
 * A filter that implements Bazel sharding support.
 */
class ShardFilter private constructor(
  private val totalShards: Int,
  private val shardIndex: Int
) : PostDiscoveryFilter {
  override fun apply(testDescriptor: TestDescriptor): FilterResult {
    return when {
      // a simple test
      testDescriptor.isTest -> testDescriptor.shardDecision()
      // parametrized test
      testDescriptor.isDynamicTestMethod() -> testDescriptor.shardDecision()
      // classes, nested classes, engine root, etc.
      else -> included
    }
  }

  private fun TestDescriptor.shardDecision(): FilterResult =
    if (isInShard()) included else excluded

  private fun TestDescriptor.isInShard() =
    abs(uniqueId.toString().hashCode() % totalShards) == shardIndex

  // method-backed container (e.g., @ParameterizedTest, @TestFactory, @RepeatedTest)
  // goes into 1 shard because the invocations are produced dynamically at execution time
  private fun TestDescriptor.isDynamicTestMethod(): Boolean =
    isContainer && !isTest && source.orElse(null) is MethodSource

  companion object {
    @JvmStatic
    fun create(): ShardFilter? {
      val totalShards = System.getenv("TEST_TOTAL_SHARDS") ?: return null
      val shardIndex = System.getenv("TEST_SHARD_INDEX") ?: return null
      try {
        println("Configuring shard $shardIndex of $totalShards")
        return ShardFilter(totalShards.toInt(), shardIndex.toInt())
      } catch (e: NumberFormatException) {
        throw RuntimeException("Failed to parse sharding environment variables", e)
      }
    }

    @JvmStatic
    fun writeShardStatus() {
      val file = System.getenv("TEST_SHARD_STATUS_FILE") ?: return
      try {
        Path.of(file).writeText("")
      } catch (e: IOException) {
        throw RuntimeException("Failed to write Shard Status File '$file'", e)
      }
    }
  }
}
