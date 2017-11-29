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

  fun findSourceIndex(sourceUrls: List<Url>, sourceFile: VirtualFile?, resolver: Lazy<SourceFileResolver?>?, localFileUrlOnly: Boolean): Int

  fun findSourceMappings(sourceUrls: List<Url>, sourceFile: VirtualFile?, resolver: Lazy<SourceFileResolver?>?, localFileUrlOnly: Boolean): Mappings? {
    val sourceIndex = findSourceIndex(sourceUrls, sourceFile, resolver, localFileUrlOnly)
    return if (sourceIndex >= 0) findSourceMappings(sourceIndex) else null
  }

  fun getSourceLineByRawLocation(rawLine: Int, rawColumn: Int) = generatedMappings.get(rawLine, rawColumn)?.sourceLine ?: -1

  fun findSourceIndex(sourceFile: VirtualFile, localFileUrlOnly: Boolean): Int

  fun processSourceMappingsInLine(sourceIndex: Int, sourceLine: Int, mappingProcessor: MappingsProcessorInLine): Boolean

  fun processSourceMappingsInLine(sourceUrls: List<Url>, sourceLine: Int, mappingProcessor: MappingsProcessorInLine, sourceFile: VirtualFile?, resolver: Lazy<SourceFileResolver?>?, localFileUrlOnly: Boolean): Boolean {
    val sourceIndex = findSourceIndex(sourceUrls, sourceFile, resolver, localFileUrlOnly)
    return sourceIndex >= 0 && processSourceMappingsInLine(sourceIndex, sourceLine, mappingProcessor)
  }
}


class OneLevelSourceMap(override val outFile: String?,
                        override val generatedMappings: Mappings,
                        private val sourceIndexToMappings: Array<MappingList?>,
                        override val sourceResolver: SourceResolver,
                        override val hasNameMappings: Boolean) : SourceMap {
  override val sources: Array<Url>
    get() = sourceResolver.canonicalizedUrls

  override fun findSourceIndex(sourceUrls: List<Url>, sourceFile: VirtualFile?, resolver: Lazy<SourceFileResolver?>?, localFileUrlOnly: Boolean): Int {
    val index = sourceResolver.findSourceIndex(sourceUrls, sourceFile, localFileUrlOnly)
    if (index == -1 && resolver != null) {
      return resolver.value?.let { sourceResolver.findSourceIndex(sourceFile, it) } ?: -1
    }
    return index
  }

  // returns SourceMappingList
  override fun findSourceMappings(sourceIndex: Int) = sourceIndexToMappings.get(sourceIndex)!!

  override fun findSourceIndex(sourceFile: VirtualFile, localFileUrlOnly: Boolean) = sourceResolver.findSourceIndexByFile(sourceFile, localFileUrlOnly)

  override fun processSourceMappingsInLine(sourceIndex: Int, sourceLine: Int, mappingProcessor: MappingsProcessorInLine): Boolean {
    return findSourceMappings(sourceIndex).processMappingsInLine(sourceLine, mappingProcessor)
  }
}