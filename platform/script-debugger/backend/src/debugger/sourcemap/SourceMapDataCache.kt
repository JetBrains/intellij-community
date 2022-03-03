// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger.sourcemap

import com.intellij.util.containers.ContainerUtil
import java.util.*

object SourceMapDataCache {

  private val cache: MutableMap<SourceMapDataImpl, SourceMapDataEx> =
    ContainerUtil.createConcurrentSoftValueMap()

  fun getOrCreate(sourceMapData: String, mapDebugName: String? = null): SourceMapDataEx? {
    val data = parseMapSafely(sourceMapData, mapDebugName) ?: return null
    val value = cache[data]
    if (value != null) return value

    val sourceIndexToMappings = calculateReverseMappings(data)
    val generatedMappings = GeneratedMappingList(data.mappings)
    val result = SourceMapDataEx(data, sourceIndexToMappings, generatedMappings)

    cache[data] = result

    return result
  }
}