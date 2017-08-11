/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.debugger.sourcemap

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Url
import gnu.trove.THashMap

class NestedSourceMap(private val childMap: SourceMap, private val parentMap: SourceMap) : SourceMap {
  override val sourceResolver: SourceResolver
    get() = parentMap.sourceResolver

  override val sources: Array<Url>
    get() = parentMap.sources

  private val sourceIndexToSourceMappings = arrayOfNulls<Mappings>(parentMap.sources.size)

  private val childMappingToTransformed = THashMap<MappingEntry, MappingEntry>()

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

  override fun findSourceIndex(sourceFile: VirtualFile, localFileUrlOnly: Boolean) = parentMap.findSourceIndex(sourceFile, localFileUrlOnly)

  override fun findSourceIndex(sourceUrls: List<Url>,
                               sourceFile: VirtualFile?,
                               resolver: Lazy<SourceFileResolver?>?,
                               localFileUrlOnly: Boolean) = parentMap.findSourceIndex(sourceUrls, sourceFile, resolver, localFileUrlOnly)

  override fun processSourceMappingsInLine(sourceIndex: Int, sourceLine: Int, mappingProcessor: MappingsProcessorInLine): Boolean {
    val childSourceMappings = childMap.findSourceMappings(sourceIndex)
    return (parentMap.findSourceMappings(sourceIndex) as MappingList).processMappingsInLine(sourceLine, object: MappingsProcessorInLine {
      override fun process(entry: MappingEntry, nextEntry: MappingEntry?): Boolean {
        val childIndex = childSourceMappings.indexOf(entry.generatedLine, entry.generatedColumn)
        if (childIndex == -1) {
          return true
        }

        val childEntry = childSourceMappings.getByIndex(childIndex)
        // todo not clear - should we resolve next child entry by current child index or by provided parent nextEntry?
        val nextChildEntry = if (nextEntry == null) null else childSourceMappings.getNextOnTheSameLine(childIndex)
        return mappingProcessor.process(childMappingToTransformed.getOrPut(childEntry) { NestedMappingEntry(childEntry, entry) },
                                        nextChildEntry?.let { childMappingToTransformed.getOrPut(it) { NestedMappingEntry(it, entry) } })
      }
    })
  }
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

  override val nextGenerated: MappingEntry
    get() = child.nextGenerated
}