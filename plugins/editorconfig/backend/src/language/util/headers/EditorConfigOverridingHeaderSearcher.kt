// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.util.headers

import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigPsiFile
import org.editorconfig.language.util.EditorConfigPsiTreeUtil.findAllParentsFiles

class EditorConfigOverridingHeaderSearcher : EditorConfigHeaderOverrideSearcherBase() {
  override fun findRelevantPsiFiles(file: EditorConfigPsiFile): Sequence<EditorConfigPsiFile> = findAllParentsFiles(file).asSequence()

  override fun getOverrideKind(baseHeader: EditorConfigHeader, testedHeader: EditorConfigHeader): OverrideKind {
    if (EditorConfigHeaderSearcherUtil.isStrictOverride(testedHeader, baseHeader)) return OverrideKind.STRICT
    if (EditorConfigHeaderSearcherUtil.isPartialOverride(testedHeader, baseHeader)) return OverrideKind.PARTIAL
    return OverrideKind.NONE
  }
}