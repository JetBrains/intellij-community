// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.stats

import com.intellij.util.io.PersistentBTreeEnumerator
import com.intellij.util.io.PersistentMapImpl
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolute

@Internal
object StorageStatsRegistrar {
  private val maps = ConcurrentHashMap<Path, PersistentMapImpl<*, *>>()
  private val enumerators = ConcurrentHashMap<Path, PersistentBTreeEnumerator<*>>()

  fun registerMap(path: Path, map: PersistentMapImpl<*, *>) {
    maps.put(path.absolute(), map)
  }

  fun unregisterMap(path: Path) {
    maps.remove(path.absolute())
  }

  fun registerEnumerator(path: Path, enumerator: PersistentBTreeEnumerator<*>) {
    enumerators.put(path.absolute(), enumerator)
  }

  fun unregisterEnumerator(path: Path) {
    enumerators.remove(path.absolute())
  }

  fun dumpStatsForOpenMaps(): Map<Path, PersistentHashMapStatistics> = maps.mapValues { it.value.statistics }
  fun dumpStatsForOpenEnumerators(): Map<Path, PersistentEnumeratorStatistics> = enumerators.mapValues { it.value.statistics }
}

@Internal
data class BTreeStatistics(val pages: Int,
                           val elements: Int,
                           val height: Int,
                           val moves: Int,
                           val leafPages: Int,
                           val maxSearchStepsInRequest: Int,
                           val searchRequests: Int,
                           val searchSteps: Int,
                           val pageCapacity: Int,
                           val sizeInBytes: Long)

@Internal
data class PersistentEnumeratorStatistics(val bTreeStatistics: BTreeStatistics,
                                          val collisions: Int,
                                          val values: Int,
                                          val dataFileSizeInBytes: Long,
                                          val storageSizeInBytes: Long)

@Internal
data class PersistentHashMapStatistics(val persistentEnumeratorStatistics: PersistentEnumeratorStatistics,
                                       val valueStorageSizeInBytes: Long)

@Internal
data class CachedChannelsStatistics(val hit: Int,
                                    val miss: Int,
                                    val load: Int,
                                    val capacity: Int)

@Internal
data class FilePageCacheStatistics(val cachedChannelsStatistics: CachedChannelsStatistics,
                                   val uncachedFileAccess: Int,
                                   val maxRegisteredFiles: Int,
                                   val maxCacheSizeInBytes: Long,
                                   val totalCachedSizeInBytes: Long,
                                   val pageHit: Int,
                                   val pageFastCacheHit: Int,
                                   val pageMiss: Int,
                                   val pageLoad: Int,
                                   val disposedBuffers: Int,
                                   val capacityInBytes: Long) {
  fun dumpInfoImportantForBuildProcess() : String {
    return "pageHits=$pageHit, " +
           "pageFastCacheHits=$pageFastCacheHit, " +
           "pageMisses=$pageMiss, " +
           "pageLoad=$pageLoad, " +
           "capacityInBytes=$capacityInBytes, " +
           "disposedBuffers=$disposedBuffers " +
           "maxRegisteredFiles=$maxRegisteredFiles " +
           "maxCacheSizeInBytes=$maxCacheSizeInBytes" +
           "totalSizeCachedBytes=$totalCachedSizeInBytes"
  }
}