// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger.sourcemap

import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SourceMapDataCache {
  private val cache: MutableMap<SourceMapDataImpl, SourceMapDataEx> =
    ContainerUtil.createConcurrentSoftValueMap()

  fun getOrCreate(sourceMapData: CharSequence, mapDebugName: String? = null): SourceMapDataEx? {
    val data = parseMapSafely(sourceMapData, mapDebugName) ?: return null
    val value = cache[data]
    if (value != null) return value

    val sourceIndexToMappings = calculateReverseMappings(data)
    val generatedMappings = GeneratedMappingList(data.mappings)
    val result = SourceMapDataEx(data, sourceIndexToMappings, generatedMappings)

    cache[data] = result

    return result
  }

  private fun calculateReverseMappings(data: SourceMapData): Array<MappingList?> {
    val reverseMappingsBySourceUrl = arrayOfNulls<MutableList<MappingEntry>?>(data.sources.size)
    for (entry in data.mappings) {
      val sourceIndex = entry.source
      if (sourceIndex >= 0) {
        val reverseMappings = getOrCreateMapping(reverseMappingsBySourceUrl, sourceIndex)
        reverseMappings.add(entry)
      }
    }
    return Array(reverseMappingsBySourceUrl.size) {
      val entries = reverseMappingsBySourceUrl[it]
      if (entries == null) {
        null
      }
      else {
        SourceMappingList(entries)
      }
    }
  }

  private fun getOrCreateMapping(reverseMappingsBySourceUrl: Array<MutableList<MappingEntry>?>, sourceIndex: Int): MutableList<MappingEntry> {
    var reverseMappings = reverseMappingsBySourceUrl[sourceIndex]
    if (reverseMappings == null) {
      reverseMappings = ArrayList()
      reverseMappingsBySourceUrl[sourceIndex] = reverseMappings
    }
    return reverseMappings
  }
}