/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.javascript.debugger

import com.google.common.base.CharMatcher
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import gnu.trove.THashMap
import org.jetbrains.debugger.sourcemap.MappingEntry
import org.jetbrains.debugger.sourcemap.Mappings
import org.jetbrains.debugger.sourcemap.SourceMap
import org.jetbrains.rpc.LOG

private val S1 = ",()[]{}="
// don't trim trailing .&: - could be part of expression
private val OPERATOR_TRIMMER = CharMatcher.INVISIBLE.or(CharMatcher.anyOf(S1))

val NAME_TRIMMER = CharMatcher.INVISIBLE.or(CharMatcher.anyOf(S1 + ".&:"))

// generateVirtualFile only for debug purposes
open class NameMapper(private val document: Document, private val transpiledDocument: Document, private val sourceMappings: Mappings, protected val sourceMap: SourceMap, private val transpiledFile: VirtualFile? = null) {
  var rawNameToSource: MutableMap<String, String>? = null
    private set

  // PsiNamedElement, JSVariable for example
  // returns generated name
  @JvmOverloads
  open fun map(identifierOrNamedElement: PsiElement, forceMapBySourceCode: Boolean = false): String? {
    val offset = identifierOrNamedElement.textOffset
    val line = document.getLineNumber(offset)

    val sourceEntryIndex = sourceMappings.indexOf(line, offset - document.getLineStartOffset(line))
    if (sourceEntryIndex == -1) {
      return null
    }

    val sourceEntry = sourceMappings.getByIndex(sourceEntryIndex)
    val next = sourceMappings.getNextOnTheSameLine(sourceEntryIndex, false)
    if (next != null && sourceMappings.getColumn(next) == sourceMappings.getColumn(sourceEntry)) {
      warnSeveralMapping(identifierOrNamedElement)
      return null
    }

    val generatedName: String?
    try {
      generatedName = extractName(getGeneratedName(transpiledDocument, sourceMap, sourceEntry))
    }
    catch (e: IndexOutOfBoundsException) {
      LOG.warn("Cannot get generated name: source entry (${sourceEntry.generatedLine},  ${sourceEntry.generatedColumn}). Transpiled File: " + transpiledFile?.path)
      return null
    }
    if (generatedName == null || generatedName.isEmpty()) {
      return null
    }

    var sourceName = sourceEntry.name
    if (sourceName == null || forceMapBySourceCode || Registry.`is`("js.debugger.name.mappings.by.source.code", false)) {
      sourceName = (identifierOrNamedElement as? PsiNamedElement)?.name ?: identifierOrNamedElement.text ?: sourceName ?: return null
    }

    addMapping(generatedName, sourceName)
    return generatedName
  }

  fun addMapping(generatedName: String, sourceName: String) {
    if (rawNameToSource == null) {
      rawNameToSource = THashMap<String, String>()
    }
    rawNameToSource!!.put(generatedName, sourceName)
  }

  protected open fun extractName(rawGeneratedName: CharSequence):String? = NAME_TRIMMER.trimFrom(rawGeneratedName)

  companion object {
    fun trimName(rawGeneratedName: CharSequence, isLastToken: Boolean) = (if (isLastToken) NAME_TRIMMER else OPERATOR_TRIMMER).trimFrom(rawGeneratedName)
  }
}

fun warnSeveralMapping(element: PsiElement) {
  // see https://dl.dropboxusercontent.com/u/43511007/s/Screen%20Shot%202015-01-21%20at%2020.33.44.png
  // var1 mapped to the whole "var c, notes, templates, ..." expression text + unrelated text "   ;"
  LOG.warn("incorrect sourcemap, several mappings for named element ${element.text}")
}

private fun getGeneratedName(document: Document, sourceMap: SourceMap, sourceEntry: MappingEntry): CharSequence {
  val lineStartOffset = document.getLineStartOffset(sourceEntry.generatedLine)
  val nextGeneratedMapping = sourceMap.generatedMappings.getNextOnTheSameLine(sourceEntry)
  val endOffset: Int
  if (nextGeneratedMapping == null) {
    endOffset = document.getLineEndOffset(sourceEntry.generatedLine)
  }
  else {
    endOffset = lineStartOffset + nextGeneratedMapping.generatedColumn
  }
  return document.immutableCharSequence.subSequence(lineStartOffset + sourceEntry.generatedColumn, endOffset)
}