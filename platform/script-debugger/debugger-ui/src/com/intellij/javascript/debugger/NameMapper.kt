// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.javascript.debugger

import com.google.common.base.CharMatcher
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.SyntaxTraverser
import org.jetbrains.debugger.sourcemap.MappingEntry
import org.jetbrains.debugger.sourcemap.Mappings
import org.jetbrains.debugger.sourcemap.MappingsProcessorInLine
import org.jetbrains.debugger.sourcemap.SourceMap
import org.jetbrains.rpc.LOG
import java.util.concurrent.atomic.AtomicInteger

private const val S1 = ",()[]{}="
// don't trim trailing .&: - could be part of expression
private val OPERATOR_TRIMMER = CharMatcher.invisible().or(CharMatcher.anyOf(S1))

val NAME_TRIMMER: CharMatcher = CharMatcher.invisible().or(CharMatcher.anyOf("$S1.&:"))

// generateVirtualFile only for debug purposes
open class NameMapper(private val document: Document, private val transpiledDocument: Document, private val sourceMappings: Mappings, protected val sourceMap: SourceMap, private val transpiledFile: VirtualFile? = null) {
  var rawNameToSource: MutableMap<String, String>? = null
    private set

  // PsiNamedElement, JSVariable for example
  // returns generated name
  @JvmOverloads
  open fun map(identifierOrNamedElement: PsiElement, saveMapping: Boolean = true): String? {
    return doMap(identifierOrNamedElement, false, saveMapping)
  }

  protected fun doMap(identifierOrNamedElement: PsiElement, mapBySourceCode: Boolean, saveMapping: Boolean = true): String? {
    val mappings = getMappingsForElement(identifierOrNamedElement)
    if (mappings == null || mappings.isEmpty()) return null
    val sourceEntry = mappings[0]
    val generatedName: String?
    try {
      generatedName = extractName(getGeneratedName(identifierOrNamedElement, transpiledDocument, sourceMap, mappings), identifierOrNamedElement)
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

    if (saveMapping) {
      addMapping(generatedName, sourceName)
    }
    return generatedName
  }

  protected open fun getMappingsForElement(element: PsiElement): List<MappingEntry>? {
    val mappings = mutableListOf<MappingEntry>()

    val offset = element.textOffset
    val line = document.getLineNumber(offset)
    val elementColumn = offset - document.getLineStartOffset(line)
    val elementEndColumn = elementColumn + element.textLength - 1

    val sourceEntryIndex = sourceMappings.indexOf(line, elementColumn)
    if (sourceEntryIndex == -1) {
      return null
    }

    val sourceEntry = sourceMappings.getByIndex(sourceEntryIndex)
    val next = sourceMappings.getNextOnTheSameLine(sourceEntryIndex, false)
    if (next != null && sourceMappings.getColumn(next) == sourceMappings.getColumn(sourceEntry)) {
      warnSeveralMapping(element)
      return null
    }

    val file = element.containingFile
    val namedElementsCounter = AtomicInteger(0)
    val processor = object : MappingsProcessorInLine {
      override fun process(entry: MappingEntry, nextEntry: MappingEntry?): Boolean {
        val entryColumn = entry.sourceColumn
        // next entry column could be equal to prev, see https://code.google.com/p/google-web-toolkit/issues/detail?id=9103
        val isSuitable = if (nextEntry == null || (entryColumn == 0 && nextEntry.sourceColumn == 0)) {
          entryColumn <= elementColumn
        }
        else {
          entryColumn in elementColumn..elementEndColumn
        }
        if (isSuitable) {
          val startOffset = document.getLineStartOffset(line) + entryColumn
          val endOffset =
            if (nextEntry != null) document.getLineStartOffset(line) + nextEntry.sourceColumn
            else document.getLineEndOffset(line)
          if (collectNamedElementsInRange(TextRange(startOffset, endOffset))) {
            return false
          }

          mappings.add(entry)
        }
        return true
      }

      private fun collectNamedElementsInRange(range: TextRange): Boolean {
        return SyntaxTraverser.psiTraverser(file)
          .onRange(range)
          .filter { it is PsiNamedElement && range.contains(it.textRange) && namedElementsCounter.incrementAndGet() > 1 }
          .traverse()
          .isNotEmpty
      }
    }
    if (!sourceMap.processSourceMappingsInLine(sourceEntry.source, sourceEntry.sourceLine, processor)) {
      return null
    }
    return mappings
  }

  fun addMapping(generatedName: String, sourceName: String) {
    if (rawNameToSource == null) {
      rawNameToSource = HashMap<String, String>()
    }
    rawNameToSource!!.put(generatedName, sourceName)
  }

  protected open fun extractName(rawGeneratedName: CharSequence?, context: PsiElement? = null):String? = rawGeneratedName?.let {
    NAME_TRIMMER.trimFrom(it)
  }

  companion object {
    fun trimName(rawGeneratedName: CharSequence, isLastToken: Boolean): String? = (if (isLastToken) NAME_TRIMMER else OPERATOR_TRIMMER).trimFrom(rawGeneratedName)
  }

  protected open fun getGeneratedName(element: PsiElement, document: Document, sourceMap: SourceMap, mappings: List<MappingEntry>): CharSequence? {
    val sourceEntry = mappings[0]
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
}

fun warnSeveralMapping(element: PsiElement) {
  // see https://dl.dropboxusercontent.com/u/43511007/s/Screen%20Shot%202015-01-21%20at%2020.33.44.png
  // var1 mapped to the whole "var c, notes, templates, ..." expression text + unrelated text "   ;"
  LOG.warn("incorrect sourcemap, several mappings for named element ${element.text}")
}

