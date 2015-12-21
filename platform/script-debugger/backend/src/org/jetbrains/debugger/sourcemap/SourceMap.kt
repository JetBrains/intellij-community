/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.util.NullableLazyValue
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Url

// sources - is not originally specified, but canonicalized/normalized
class SourceMap(val outFile: String?, val mappings: MappingList, internal val sourceIndexToMappings: Array<MappingList?>, val sourceResolver: SourceResolver, val hasNameMappings: Boolean) {
  val sources: Array<Url>
    get() = sourceResolver.canonicalizedUrls

  fun getSourceLineByRawLocation(rawLine: Int, rawColumn: Int) = mappings.get(rawLine, rawColumn)?.sourceLine ?: -1

  fun findMappingList(sourceUrls: List<Url>, sourceFile: VirtualFile?, resolver: NullableLazyValue<SourceResolver.Resolver>?): MappingList? {
    var mappings = sourceResolver.findMappings(sourceUrls, this, sourceFile)
    if (mappings == null && resolver != null) {
      mappings = resolver.value?.let { sourceResolver.findMappings(sourceFile, this, it) }
    }
    return mappings
  }

  fun processMappingsInLine(sourceUrls: List<Url>,
                            sourceLine: Int,
                            mappingProcessor: MappingList.MappingsProcessorInLine,
                            sourceFile: VirtualFile?,
                            resolver: NullableLazyValue<SourceResolver.Resolver>?): Boolean {
    val mappings = findMappingList(sourceUrls, sourceFile, resolver)
    return mappings != null && mappings.processMappingsInLine(sourceLine, mappingProcessor)
  }

  fun getMappingsOrderedBySource(source: Int) = sourceIndexToMappings[source]!!
}