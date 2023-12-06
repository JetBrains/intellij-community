// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.metadata.diff

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.workspace.storage.metadata.StorageMetadata
import com.intellij.platform.workspace.storage.metadata.extensions.metadataType

private val LOG = logger<TypesMetadataComparator>()

internal class ComparisonsBuilder(private val log: MetadataComparatorLog) {
  internal var result: ComparisonResult = Equal

  fun <T> compare(metadataName: String, cache: T, current: T, comparator: (T, T) -> Boolean = { a, b -> a == b }) = compareAndUpdateResult(metadataName, cache, current) {
    comparator.invoke(cache, current)
  }

  fun <T> compare(metadataName: String, cache: T, current: T, checker: MetadataComparator<T>) = updateResult(metadataName, cache, current) {
    checker.areEquals(cache, current)
  }

  fun <T> compareAll(metadataName: String, cache: Iterable<T>, current: Iterable<T>, comparator: (T, T) -> Boolean = { a, b -> a == b }) {
    updateResult(metadataName, cache, current) {
      if (current.count() != cache.count()) {
        return@updateResult createNotEqualResult(
          "Sizes of cache $metadataName (${cache.count()}) and current $metadataName (${current.count()}) are different"
        )
      }

      val currentIterator = current.iterator()
      cache.allEquals {
        compare(metadataName, it, currentIterator.next(), comparator)
        result
      }
    }
  }

  fun <T> compareAll(metadataName: String, cache: Iterable<T>, current: Iterable<T>, checker: MetadataComparator<T>) =
    updateResult(metadataName, cache, current) {
      if (current.count() != cache.count()) {
        return@updateResult createNotEqualResult(
          "Sizes of cache $metadataName (${cache.count()}) and current $metadataName (${current.count()}) are different"
        )
      }

      val currentIterator = current.iterator()
      cache.allEquals { checker.areEquals(it, currentIterator.next()) }
    }

  private fun skipComparison(metadataName: String) {
    log.ignoreComparing(metadataName)
  }


  private fun updateResult(metadataName: String, cache: Any?, current: Any?, comparison: () -> ComparisonResult) {
    if (result.areEquals) {
      result = comparison.invoke()
      log.logResult(metadataName, cache, current, result)
    } else {
      skipComparison(metadataName)
    }
  }

  private fun compareAndUpdateResult(metadataName: String, cache: Any?, current: Any?, comparison: () -> Boolean) {
    if (result.areEquals) {
      val areEquals = comparison.invoke()
      result = if (areEquals) Equal else createNotEqualResult(metadataName, cache, current)

      log.logResult(metadataName, cache, current, result)
    } else {
      skipComparison(metadataName)
    }
  }


  private fun createNotEqualResult(metadataName: String, cache: Any?, current: Any?): NotEqualWithLog {
    val notEqual = NotEqualWithLog()
    notEqual.log.logResult(metadataName, cache, current, notEqual)
    return notEqual
  }

  private fun createNotEqualResult(cause: String): NotEqualWithLog {
    val notEqual = NotEqualWithLog()
    notEqual.log.comparisonResult(cause, notEqual)
    return notEqual
  }


  private fun MetadataComparatorLog.logResult(metadataName: String, cache: Any?, current: Any?, result: ComparisonResult) {
    if (!longOutput(cache, current)) {
      comparisonResult("Cache: $metadataName = $cache, Current: $metadataName = $current", result)
    } else {
      comparisonResult(metadataName, result)
    }
  }

  private fun longOutput(cache: Any?, current: Any?): Boolean =
    current is Iterable<*> || cache is Iterable<*> || current is StorageMetadata || cache is StorageMetadata

  private inline fun <T> Iterable<T>.allEquals(comparison: (T) -> ComparisonResult): ComparisonResult {
    forEach {
      val comparisonResult = comparison(it)
      if (!comparisonResult.areEquals) {
        return comparisonResult
      }
    }
    return Equal
  }
}

public object ComparisonUtil {
  internal fun compareMetadata(cacheMetadata: StorageMetadata, cacheMetadataName: String,
                               currentMetadata: StorageMetadata, currentMetadataName: String,
                               comparisons: ComparisonsBuilder.() -> Unit): ComparisonResult {
    return compareMetadata(
      areComparingText(
        "${cacheMetadata.metadataType} \"$cacheMetadataName\"",
        "${currentMetadata.metadataType} \"$currentMetadataName\""
      ),
      currentMetadata, cacheMetadata, comparisons
    )
  }

  internal fun compareMetadata(cacheMetadata: StorageMetadata,
                               currentMetadata: StorageMetadata,
                               comparisons: ComparisonsBuilder.() -> Unit): ComparisonResult {
    return compareMetadata(
      areComparingText(cacheMetadata.metadataType, currentMetadata.metadataType),
      currentMetadata, cacheMetadata, comparisons
    )
  }

  private fun compareMetadata(areComparing: String, cacheMetadata: StorageMetadata, currentMetadata: StorageMetadata,
                              comparisons: ComparisonsBuilder.() -> Unit): ComparisonResult {
    val log = EntitiesComparatorLog.INSTANCE
    val comparisonsBuilder = ComparisonsBuilder(log)

    log.startComparing(areComparing)
    comparisonsBuilder.compare("type", cacheMetadata.metadataType, currentMetadata.metadataType)
    if (comparisonsBuilder.result.areEquals) {
      comparisonsBuilder.comparisons()
    }
    log.endComparing(areComparing, comparisonsBuilder.result)

    logIfNotEqual(areComparing, comparisonsBuilder.result)

    return comparisonsBuilder.result
  }


  private fun logIfNotEqual(areComparing: String, result: ComparisonResult) {
    if (result.areEquals) {
      return
    }

    result as NotEqualWithLog

    result.log.startComparing(areComparing)
    result.log.endComparing(areComparing, result)
  }


  private fun areComparingText(cacheText: String, currentText: String): String =
    "cache: $cacheText     with current: $currentText"

}
