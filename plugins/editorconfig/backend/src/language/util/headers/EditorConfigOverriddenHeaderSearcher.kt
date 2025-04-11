// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.util.headers

import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigPsiFile
import org.editorconfig.language.util.EditorConfigPsiTreeUtil.findAllChildrenFiles
import org.editorconfig.language.util.headers.EditorConfigHeaderSearcherUtil.isPartialOverride
import org.editorconfig.language.util.headers.EditorConfigHeaderSearcherUtil.isStrictOverride

class EditorConfigOverriddenHeaderSearcher(private val honorRoot: Boolean = true) : EditorConfigHeaderOverrideSearcherBase() {
  override fun findRelevantPsiFiles(file: EditorConfigPsiFile): Sequence<EditorConfigPsiFile> = (findAllChildrenFiles(file, honorRoot) + file).asSequence()

  override fun getOverrideKind(baseHeader: EditorConfigHeader, testedHeader: EditorConfigHeader): OverrideKind {
    if (isStrictOverride(baseHeader, testedHeader)) return OverrideKind.STRICT
    if (isPartialOverride(baseHeader, testedHeader)) return OverrideKind.PARTIAL
    return OverrideKind.NONE
  }
}