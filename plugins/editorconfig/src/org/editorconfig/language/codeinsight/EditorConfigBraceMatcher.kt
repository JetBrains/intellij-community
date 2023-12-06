// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import org.editorconfig.language.psi.EditorConfigElementTypes

internal class EditorConfigBraceMatcher : PairedBraceMatcher {
  override fun getPairs() = PAIRS
  override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?) = true
  override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int) = openingBraceOffset

  private val PAIRS = arrayOf(
    BracePair(EditorConfigElementTypes.L_BRACKET, EditorConfigElementTypes.R_BRACKET, true),
    BracePair(EditorConfigElementTypes.L_CURLY, EditorConfigElementTypes.R_CURLY, true)
  )
}