// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.util.headers

import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigPsiFile
import org.editorconfig.language.util.EditorConfigPsiTreeUtil.findAllChildrenFiles

class EditorConfigOverriddenHeaderSearcher : EditorConfigHeaderSearcher() {
  override fun findRelevantPsiFiles(file: EditorConfigPsiFile) = (findAllChildrenFiles(file) + file).asSequence()

  override fun isMatchingHeader(baseHeader: EditorConfigHeader, testedHeader: EditorConfigHeader) =
    EditorConfigHeaderSearcherUtil.isOverride(baseHeader, testedHeader)
}

class EditorConfigPartiallyOverriddenHeaderSearcher : EditorConfigHeaderSearcher() {
  override fun findRelevantPsiFiles(file: EditorConfigPsiFile) = (findAllChildrenFiles(file) + file).asSequence()

  override fun isMatchingHeader(baseHeader: EditorConfigHeader, testedHeader: EditorConfigHeader) =
    EditorConfigHeaderSearcherUtil.isPartialOverride(baseHeader, testedHeader)
}
