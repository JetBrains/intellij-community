// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.util.headers

import com.intellij.util.AstLoadingFilter
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigPsiFile
import org.editorconfig.language.psi.EditorConfigSection
import org.editorconfig.language.util.EditorConfigPsiTreeUtil

abstract class EditorConfigHeaderOverrideSearcherBase {
  fun findMatchingHeaders(header: EditorConfigHeader): List<OverrideSearchResult> {
    if (!header.isValidGlob) return emptyList()
    val relevantHeaders = getRelevantHeaders(header)
    return findMatchingHeaders(header, relevantHeaders)
  }

  fun findMatchingHeaders(header: EditorConfigHeader, relevantHeaders: Sequence<EditorConfigHeader>): List<OverrideSearchResult> {
    return relevantHeaders.mapNotNull {
      when (getOverrideKind(header, it)) {
        OverrideKind.NONE -> null
        OverrideKind.PARTIAL -> OverrideSearchResult(it, true)
        OverrideKind.STRICT -> OverrideSearchResult(it, false)
      }
    }.toList()
  }

  fun getRelevantHeaders(header: EditorConfigHeader): Sequence<EditorConfigHeader> {
    val currentFile = EditorConfigPsiTreeUtil.getOriginalFile(header.containingFile) as? EditorConfigPsiFile ?: return emptySequence()

    return findRelevantPsiFiles(currentFile)
      .map { AstLoadingFilter.forceAllowTreeLoading<List<EditorConfigSection>, RuntimeException>(it) { it.sections } }
      .flatMap(List<EditorConfigSection>::asSequence)
      .map(EditorConfigSection::getHeader)
      .filter(EditorConfigHeader::isValidGlob)
  }

  protected abstract fun findRelevantPsiFiles(file: EditorConfigPsiFile): Sequence<EditorConfigPsiFile>
  protected abstract fun getOverrideKind(baseHeader: EditorConfigHeader, testedHeader: EditorConfigHeader): OverrideKind

  data class OverrideSearchResult(val header: EditorConfigHeader, val isPartial: Boolean)

  enum class OverrideKind {
    NONE,
    PARTIAL,
    STRICT
  }
}
