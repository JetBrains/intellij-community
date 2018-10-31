// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.javascript.debugger

import com.google.common.base.CharMatcher
import com.intellij.openapi.editor.Document
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
private val OPERATOR_TRIMMER = CharMatcher.invisible().or(CharMatcher.anyOf(S1))

val NAME_TRIMMER: CharMatcher = CharMatcher.invisible().or(CharMatcher.anyOf("$S1.&:"))

// generateVirtualFile only for debug purposes
open class NameMapper(private val document: Document, private val transpiledDocument: Document, private val sourceMappings: Mappings, protected val sourceMap: SourceMap, private val transpiledFile: VirtualFile? = null) {
  var rawNameToSource: MutableMap<String, String>? = null
    private set

  // PsiNamedElement, JSVariable for example
  // returns generated name
  open fun map(identifierOrNamedElement: PsiElement): String? {
    return doMap(identifierOrNamedElement, false)
  }

  protected fun doMap(identifierOrNamedElement: PsiElement, mapBySourceCode: Boolean): String? {
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
    if (sourceName == null || mapBySourceCode) {
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
    fun trimName(rawGeneratedName: CharSequence, isLastToken: Boolean): String? = (if (isLastToken) NAME_TRIMMER else OPERATOR_TRIMMER).trimFrom(rawGeneratedName)
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