// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger.sourcemap

import com.intellij.reference.SoftReference
import com.intellij.util.io.readChars
import java.nio.file.Path

class FileBackedSourceMap(filePath: Path, initialData: SourceMapDataImpl, sourceResolver: SourceResolver)
  : SourceMapBase(FileBackedSourceMapData(filePath, initialData), sourceResolver) {

  override val sourceIndexToMappings: Array<MappingList?>
    get() = (this.sourceMapData as FileBackedSourceMapData).sourceIndexToMappings

  override val generatedMappings: Mappings
    get() = (sourceMapData as FileBackedSourceMapData).generatedMappings
}

private class FileBackedSourceMapData(private val filePath: Path, initialData: SourceMapDataImpl) : SourceMapData {

  private var cachedData: SoftReference<SourceMapDataExImpl> = SoftReference(calculateData(initialData))

  override val file: String? = initialData.file
  override val sources: List<String> = initialData.sources
  override val sourcesContent: List<String?>?
    get() = getData().sourceMapData.sourcesContent
  override val hasNameMappings: Boolean = initialData.hasNameMappings
  override val mappings: List<MappingEntry>
    get() = getData().sourceMapData.mappings

  val sourceIndexToMappings: Array<MappingList?>
    get() = getData().sourceIndexToMappings
  val generatedMappings: Mappings
    get() = getData().generatedMappings

  private fun calculateData(precalculatedData: SourceMapDataImpl?): SourceMapDataExImpl {
    val text = filePath.readChars()
    // TODO invalidate map. Need to drop SourceResolver's rawSources at least.
    val data = precalculatedData ?: parseMap(text) ?: throw RuntimeException("Cannot parse map $filePath")
    val sourceIndexToMappings = calculateReverseMappings(data)
    val generatedMappings = GeneratedMappingList(data.mappings)
    return SourceMapDataExImpl(data, sourceIndexToMappings, generatedMappings)
  }

  private fun getData(): SourceMapDataExImpl {
    val cached = cachedData.get()
    if (cached != null) return cached

    val calculated = calculateData(null)
    cachedData = SoftReference(calculated)
    return calculated
  }
}

private class SourceMapDataExImpl(
  val sourceMapData: SourceMapDataImpl,
  val sourceIndexToMappings: Array<MappingList?>,
  val generatedMappings: Mappings
)