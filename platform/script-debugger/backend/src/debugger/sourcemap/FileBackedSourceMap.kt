// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger.sourcemap

import com.intellij.reference.SoftReference
import com.intellij.util.Url
import com.intellij.util.io.readText
import java.nio.file.Path

class FileBackedSourceMap private constructor(filePath: Path,
                                              initialData: SourceMapDataEx,
                                              sourceResolver: SourceResolver)
  : SourceMapBase(FileBackedSourceMapData(filePath, initialData), sourceResolver) {

  override val sourceIndexToMappings: Array<MappingList?>
    get() = (this.sourceMapData as FileBackedSourceMapData).sourceIndexToMappings

  override val generatedMappings: Mappings
    get() = (sourceMapData as FileBackedSourceMapData).generatedMappings

  companion object {
    fun newFileBackedSourceMap(filePath: Path,
                               trimFileScheme: Boolean,
                               baseUrl: Url?,
                               baseUrlIsFile: Boolean): FileBackedSourceMap? {
      val text = filePath.readText()
      val data = SourceMapDataCache.getOrCreate(text, filePath.toString()) ?: return null
      return FileBackedSourceMap(filePath, data, SourceResolver(data.sourceMapData.sources, trimFileScheme, baseUrl, baseUrlIsFile))
    }
  }
}

private class FileBackedSourceMapData(private val filePath: Path, initialData: SourceMapDataEx) : SourceMapData {

  private var cachedData: SoftReference<SourceMapDataEx> = SoftReference(initialData)

  override val file: String? = initialData.sourceMapData.file
  override val sources: List<String> = initialData.sourceMapData.sources
  override val sourcesContent: List<String?>?
    get() = getData().sourceMapData.sourcesContent
  override val hasNameMappings: Boolean = initialData.sourceMapData.hasNameMappings
  override val mappings: List<MappingEntry>
    get() = getData().sourceMapData.mappings

  val sourceIndexToMappings: Array<MappingList?>
    get() = getData().sourceIndexToMappings
  val generatedMappings: Mappings
    get() = getData().generatedMappings

  private fun calculateData(): SourceMapDataEx? {
    val text = filePath.readText()
    // TODO invalidate map. Need to drop SourceResolver's rawSources at least.
    return SourceMapDataCache.getOrCreate(text, filePath.toString())
  }

  private fun getData(): SourceMapDataEx {
    val cached = cachedData.get()
    if (cached != null) return cached

    val calculated = calculateData() ?: throw RuntimeException("Cannot decode $filePath")
    cachedData = SoftReference(calculated)
    return calculated
  }
}
