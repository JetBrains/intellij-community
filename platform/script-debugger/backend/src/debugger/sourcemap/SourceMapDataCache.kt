// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger.sourcemap

import com.intellij.openapi.util.text.StringHash
import com.intellij.reference.SoftReference
import gnu.trove.TLongObjectHashMap

object SourceMapDataCache {

  private val map: TLongObjectHashMap<SoftReference<SourceMapDataEx>> = TLongObjectHashMap()

  fun getOrCreate(sourceMapData: String, mapDebugName: String? = null): SourceMapDataEx? {
    val hash = StringHash.calc(sourceMapData)
    val ref = map[hash]
    val cachedData = SoftReference.deref(ref)
    if (cachedData != null) {
      return cachedData
    }

    val data = parseMapSafely(sourceMapData, mapDebugName) ?: return null
    val sourceIndexToMappings = calculateReverseMappings(data)
    val generatedMappings = GeneratedMappingList(data.mappings)
    val result = SourceMapDataEx(data, sourceIndexToMappings, generatedMappings)

    map.put(hash, SoftReference(result))

    return result
  }
}