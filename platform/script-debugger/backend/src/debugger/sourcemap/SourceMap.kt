// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger.sourcemap

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Url

// sources - is not originally specified, but canonicalized/normalized
// lines and columns are zero-based according to specification
interface SourceMap {
  val outFile: String?

  /**
   * note: Nested map returns only parent sources
   */
  val sources: Array<Url>

  val generatedMappings: Mappings
  val hasNameMappings: Boolean
  val sourceResolver: SourceResolver

  fun findSourceMappings(sourceIndex: Int): Mappings

  fun findSourceIndex(sourceUrl: Url, sourceFile: VirtualFile?, resolver: Lazy<SourceFileResolver?>?, localFileUrlOnly: Boolean): Int

  fun findSourceMappings(sourceUrl: Url, sourceFile: VirtualFile?, resolver: Lazy<SourceFileResolver?>?, localFileUrlOnly: Boolean): Mappings? {
    val sourceIndex = findSourceIndex(sourceUrl, sourceFile, resolver, localFileUrlOnly)
    return if (sourceIndex >= 0) findSourceMappings(sourceIndex) else null
  }

  fun getSourceLineByRawLocation(rawLine: Int, rawColumn: Int): Int = generatedMappings.get(rawLine, rawColumn)?.sourceLine ?: -1

  fun findSourceIndex(sourceFile: VirtualFile, localFileUrlOnly: Boolean): Int

  fun getSourceMappingsInLine(sourceIndex: Int, sourceLine: Int): Iterable<MappingEntry>

  fun processSourceMappingsInLine(sourceIndex: Int, sourceLine: Int, mappingProcessor: MappingsProcessorInLine): Boolean {
    return mappingProcessor.processIterable(getSourceMappingsInLine(sourceIndex, sourceLine))
  }

  fun getSourceMappingsInLine(sourceUrl: Url,
                              sourceLine: Int,
                              sourceFile: VirtualFile?,
                              resolver: Lazy<SourceFileResolver?>?,
                              localFileUrlOnly: Boolean): Iterable<MappingEntry> {
    val sourceIndex = findSourceIndex(sourceUrl, sourceFile, resolver, localFileUrlOnly)
    return if (sourceIndex >= 0) getSourceMappingsInLine(sourceIndex, sourceLine) else emptyList()
  }
}


class OneLevelSourceMap(override val outFile: String?,
                        override val generatedMappings: Mappings,
                        private val sourceIndexToMappings: Array<MappingList?>,
                        override val sourceResolver: SourceResolver,
                        override val hasNameMappings: Boolean) : SourceMap {
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
}