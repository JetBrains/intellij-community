// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.editor.selection

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList

/**
 * @author Maxim.Medvedev
 */
class GroovyParameterListSelectioner : ExtendWordSelectionHandlerBase() {

  private fun getParameterList(e: PsiElement): GrParameterList? = e as? GrParameterList ?: e.parent as? GrParameterList

  override fun canSelect(e: PsiElement): Boolean = getParameterList(e) != null

  override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange> {
    val list = getParameterList(e) ?: return emptyList()
    return listOf(list.parametersRange)
  }
}
