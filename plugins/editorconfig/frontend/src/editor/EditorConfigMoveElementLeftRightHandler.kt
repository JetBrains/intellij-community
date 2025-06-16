// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.editorconfig.frontend.editor

import com.intellij.codeInsight.editorActions.moveLeftRight.MoveElementLeftRightHandler
import com.intellij.editorconfig.common.syntax.psi.EditorConfigEnumerationPattern
import com.intellij.editorconfig.common.syntax.psi.EditorConfigOptionValueList
import com.intellij.psi.PsiElement

internal class EditorConfigMoveElementLeftRightHandler : MoveElementLeftRightHandler() {

  override fun getMovableSubElements(element: PsiElement): Array<PsiElement> = when (element) {
    is EditorConfigOptionValueList -> element.optionValueIdentifierList.toTypedArray()
    is EditorConfigEnumerationPattern -> element.patternList.toTypedArray()
    else -> emptyArray()
  }
}
