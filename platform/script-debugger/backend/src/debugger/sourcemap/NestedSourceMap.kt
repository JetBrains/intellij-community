// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger.sourcemap

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Url
import com.intellij.util.containers.CollectionFactory

class NestedSourceMap(private val childMap: SourceMap, private val parentMap: SourceMap) : SourceMap {
  override val sourceResolver: SourceResolver
    get() = parentMap.sourceResolver

  override val sources: Array<Url>
    get() = parentMap.sources

  private val sourceIndexToSourceMappings = arrayOfNulls<Mappings>(parentMap.sources.size)

  private val childMappingToTransformed = CollectionFactory.createMap<MappingEntry, MappingEntry>()

  override val outFile: String?
    get() = childMap.outFile

  override val hasNameMappings: Boolean
    get() = childMap.hasNameMappings || parentMap.hasNameMappings

  override val generatedMappings: Mappings by lazy {
    NestedMappings(childMap.generatedMappings, parentMap.generatedMappings, false)
  }

  override fun findSourceMappings(sourceIndex: Int): Mappings {
    var result = sourceIndexToSourceMappings.get(sourceIndex)
    if (result == null) {
      result = NestedMappings(childMap.findSourceMappings(sourceIndex), parentMap.findSourceMappings(sourceIndex), true)
      sourceIndexToSourceMappings.set(sourceIndex, result)
    }
    return result
  }

  override fun findSourceIndex(sourceFile: VirtualFile, localFileUrlOnly: Boolean): Int = parentMap.findSourceIndex(sourceFile, localFileUrlOnly)

  override fun findSourceIndex(sourceUrl: Url,
                               sourceFile: VirtualFile?,
                               resolver: Lazy<SourceFileResolver?>?,
                               localFileUrlOnly: Boolean): Int = parentMap.findSourceIndex(sourceUrl, sourceFile, resolver, localFileUrlOnly)

  override fun getSourceMappingsInLine(sourceIndex: Int, sourceLine: Int): Iterable<MappingEntry> {
    val childSourceMappings = childMap.findSourceMappings(sourceIndex)
    return (parentMap.findSourceMappings(sourceIndex) as MappingList).getMappingsInLine(sourceLine).flatMap { entry ->
      val childIndex = childSourceMappings.indexOf(entry.generatedLine, entry.generatedColumn)
      if (childIndex == -1) {
        return emptyList()
      }

      val childEntry = childSourceMappings.getByIndex(childIndex)
      return listOf(childMappingToTransformed.getOrPut(childEntry) { NestedMappingEntry(childEntry, entry) })
      // todo not clear - should we resolve next child entry by current child index or by provided parent nextEntry?
    }
  }

  override fun getRawSource(entry: MappingEntry): String? = parentMap.getRawSource(entry)

  override fun getSourceContent(entry: MappingEntry): String? = parentMap.getSourceContent(entry)

  override fun getSourceContent(sourceIndex: Int): String? = parentMap.getSourceContent(sourceIndex)
}

private class NestedMappings(private val child: Mappings, private val parent: Mappings, private val isSourceMappings: Boolean) : Mappings {
  override fun getNextOnTheSameLine(index: Int, skipIfColumnEquals: Boolean) = parent.getNextOnTheSameLine(index, skipIfColumnEquals)

  override fun getNext(mapping: MappingEntry) = parent.getNext(mapping)

  override fun indexOf(line: Int, column: Int) = parent.indexOf(line, column)

  override fun getByIndex(index: Int) = parent.getByIndex(index)

  override fun getLine(mapping: MappingEntry) = parent.getLine(mapping)

  override fun getColumn(mapping: MappingEntry) = parent.getColumn(mapping)

  override fun get(line: Int, column: Int): MappingEntry? {
    return if (isSourceMappings) {
      parent.get(line, column)?.let { child.get(it.generatedLine, it.generatedColumn) }
    }
    else {
      child.get(line, column)?.let { parent.get(it.sourceLine, it.sourceColumn) }
    }
  }
}

private data class NestedMappingEntry(private val child: MappingEntry, private val parent: MappingEntry) : MappingEntry {
  override val generatedLine: Int
    get() = child.generatedLine

  override val generatedColumn: Int
    get() = child.generatedColumn

  override val sourceLine: Int
    get() = parent.sourceLine

  override val sourceColumn: Int
    get() = parent.sourceColumn

  override val name: String?
    get() = parent.name

  override val nextGenerated: MappingEntry?
    get() = child.nextGenerated
}