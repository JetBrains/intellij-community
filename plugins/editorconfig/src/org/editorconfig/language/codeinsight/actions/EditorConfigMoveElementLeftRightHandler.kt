// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.actions

import com.intellij.codeInsight.editorActions.moveLeftRight.MoveElementLeftRightHandler
import com.intellij.psi.PsiElement
import org.editorconfig.language.psi.EditorConfigOptionValueList
import org.editorconfig.language.psi.EditorConfigEnumerationPattern

class EditorConfigMoveElementLeftRightHandler : MoveElementLeftRightHandler() {
  override fun getMovableSubElements(element: PsiElement): Array<PsiElement> = when (element) {
    is EditorConfigOptionValueList -> element.optionValueIdentifierList.toTypedArray()
    is EditorConfigEnumerationPattern -> element.patternList.toTypedArray()
    else -> emptyArray()
  }
}
