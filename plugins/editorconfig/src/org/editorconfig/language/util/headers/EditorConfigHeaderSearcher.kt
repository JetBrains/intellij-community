// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.util.headers

import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigPsiFile
import org.editorconfig.language.psi.EditorConfigSection
import org.editorconfig.language.util.EditorConfigPsiTreeUtil

abstract class EditorConfigHeaderSearcher {
  fun getMatchingHeaders(header: EditorConfigHeader): List<EditorConfigHeader> {
    if (!header.isValidGlob) return emptyList()
    val relevantHeaders = getRelevantHeaders(header)
    return getMatchingHeaders(header, relevantHeaders)
  }

  fun getMatchingHeaders(header: EditorConfigHeader, relevantHeaders: Sequence<EditorConfigHeader>): List<EditorConfigHeader> {
    return relevantHeaders.filter { isMatchingHeader(header, it) }.toList()
  }

  fun getRelevantHeaders(header: EditorConfigHeader): Sequence<EditorConfigHeader> {
    val currentFile = EditorConfigPsiTreeUtil.getOriginalFile(header.containingFile) as? EditorConfigPsiFile ?: return emptySequence()

    return findRelevantPsiFiles(currentFile)
      .map(EditorConfigPsiFile::sections)
      .flatMap(List<EditorConfigSection>::asSequence)
      .map(EditorConfigSection::getHeader)
      .filter(EditorConfigHeader::isValidGlob)
  }

  protected abstract fun findRelevantPsiFiles(file: EditorConfigPsiFile): Sequence<EditorConfigPsiFile>
  protected abstract fun isMatchingHeader(baseHeader: EditorConfigHeader, testedHeader: EditorConfigHeader): Boolean
}
