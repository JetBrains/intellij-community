// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger.sourcemap

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Url
import org.jetbrains.annotations.ApiStatus

// sources - is not originally specified, but canonicalized/normalized
// lines and columns are zero-based according to specification
interface SourceMap {
  @get:ApiStatus.Internal
  val outFile: String?

  /**
   * note: Nested map returns only parent sources
   */
  @get:ApiStatus.Internal
  val sources: Array<Url>

  @get:ApiStatus.Internal
  val generatedMappings: Mappings

  @get:ApiStatus.Internal
  val hasNameMappings: Boolean

  @get:ApiStatus.Internal
  val sourceResolver: SourceResolver

  @ApiStatus.Internal
  fun findSourceMappings(sourceIndex: Int): Mappings

  @ApiStatus.Internal
  fun findSourceIndex(sourceUrl: Url, sourceFile: VirtualFile?, resolver: Lazy<SourceFileResolver?>?, localFileUrlOnly: Boolean): Int

  @ApiStatus.Internal
  fun findSourceMappings(sourceUrl: Url, sourceFile: VirtualFile?, resolver: Lazy<SourceFileResolver?>?, localFileUrlOnly: Boolean): Mappings? {
    val sourceIndex = findSourceIndex(sourceUrl, sourceFile, resolver, localFileUrlOnly)
    return if (sourceIndex >= 0) findSourceMappings(sourceIndex) else null
  }

  @ApiStatus.Internal
  fun getSourceLineByRawLocation(rawLine: Int, rawColumn: Int): Int = generatedMappings.get(rawLine, rawColumn)?.sourceLine ?: -1

  @ApiStatus.Internal
  fun findSourceIndex(sourceFile: VirtualFile, localFileUrlOnly: Boolean): Int

  fun getSourceMappingsInLine(sourceIndex: Int, sourceLine: Int): Iterable<MappingEntry>

  @ApiStatus.Internal
  fun processSourceMappingsInLine(sourceIndex: Int, sourceLine: Int, mappingProcessor: MappingsProcessorInLine): Boolean {
    return mappingProcessor.processIterable(getSourceMappingsInLine(sourceIndex, sourceLine))
  }

  @ApiStatus.Internal
  fun getSourceMappingsInLine(sourceUrl: Url,
                              sourceLine: Int,
                              sourceFile: VirtualFile?,
                              resolver: Lazy<SourceFileResolver?>?,
                              localFileUrlOnly: Boolean): Iterable<MappingEntry> {
    val sourceIndex = findSourceIndex(sourceUrl, sourceFile, resolver, localFileUrlOnly)
    return if (sourceIndex >= 0) getSourceMappingsInLine(sourceIndex, sourceLine) else emptyList()
  }

  @ApiStatus.Internal
  fun getRawSource(entry: MappingEntry): String?

  @ApiStatus.Internal
  fun getSourceContent(entry: MappingEntry): String?

  @ApiStatus.Internal
  fun getSourceContent(sourceIndex: Int): String?
}

internal abstract class SourceMapBase(
  protected val sourceMapData: SourceMapData,
  override val sourceResolver: SourceResolver,
) : SourceMap {

  override val outFile: String?
    get() = sourceMapData.file

  override val hasNameMappings: Boolean
    get() = sourceMapData.hasNameMappings

  protected abstract val sourceIndexToMappings: Array<MappingList?>

  override val sources: Array<Url>
    get() = sourceResolver.canonicalizedUrls

  override fun findSourceIndex(sourceUrl: Url, sourceFile: VirtualFile?, resolver: Lazy<SourceFileResolver?>?, localFileUrlOnly: Boolean): Int {
    val index = sourceResolver.findSourceIndex(sourceUrl, sourceFile, localFileUrlOnly)
    if (index == -1 && resolver != null) {
      return resolver.value?.let { sourceResolver.findSourceIndex(it) } ?: -1
    }
    return index
  }

  // returns SourceMappingList
  override fun findSourceMappings(sourceIndex: Int): MappingList = sourceIndexToMappings[sourceIndex]!!

  override fun findSourceIndex(sourceFile: VirtualFile, localFileUrlOnly: Boolean): Int = sourceResolver.findSourceIndexByFile(sourceFile, localFileUrlOnly)

  override fun getSourceMappingsInLine(sourceIndex: Int, sourceLine: Int): Iterable<MappingEntry> {
    return findSourceMappings(sourceIndex).getMappingsInLine(sourceLine)
  }

  override fun processSourceMappingsInLine(sourceIndex: Int, sourceLine: Int, mappingProcessor: MappingsProcessorInLine): Boolean {
    return findSourceMappings(sourceIndex).processMappingsInLine(sourceLine, mappingProcessor)
  }

  override fun getRawSource(entry: MappingEntry): String? {
    val index = entry.source
    return if (index < 0) null else sourceMapData.sources[index]
  }

  override fun getSourceContent(entry: MappingEntry): String? {
    val sourcesContent = sourceMapData.sourcesContent
    if (sourcesContent.isNullOrEmpty()) {
      return null
    }

    val index = entry.source
    return if (index < 0 || index >= sourcesContent.size) null else sourcesContent[index]
  }

  override fun getSourceContent(sourceIndex: Int): String? {
    val sourcesContent = sourceMapData.sourcesContent
    if (sourcesContent.isNullOrEmpty()) {
      return null
    }
    return if (sourceIndex < 0 || sourceIndex >= sourcesContent.size) null else sourcesContent[sourceIndex]
  }
}

internal class OneLevelSourceMap(
  sourceMapDataEx: SourceMapDataEx,
  sourceResolver: SourceResolver,
)
  : SourceMapBase(sourceMapDataEx.sourceMapData, sourceResolver) {

  override val sourceIndexToMappings: Array<MappingList?> = sourceMapDataEx.sourceIndexToMappings

  override val generatedMappings: Mappings = sourceMapDataEx.generatedMappings
}