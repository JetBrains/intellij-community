// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.util.headers

import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigPsiFile
import org.editorconfig.language.util.EditorConfigPsiTreeUtil.findAllParentsFiles

class EditorConfigOverridingHeaderSearcher : EditorConfigHeaderSearcher() {
  override fun findRelevantPsiFiles(file: EditorConfigPsiFile) = findAllParentsFiles(file).asSequence()

  override fun isMatchingHeader(baseHeader: EditorConfigHeader, testedHeader: EditorConfigHeader) =
    EditorConfigHeaderSearcherUtil.isOverride(testedHeader, baseHeader)
}

class EditorConfigPartiallyOverridingHeaderSearcher : EditorConfigHeaderSearcher() {
  override fun findRelevantPsiFiles(file: EditorConfigPsiFile) = findAllParentsFiles(file).asSequence()

  override fun isMatchingHeader(baseHeader: EditorConfigHeader, testedHeader: EditorConfigHeader) =
    EditorConfigHeaderSearcherUtil.isPartialOverride(testedHeader, baseHeader)
}
