// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.util.headers

import com.intellij.editorconfig.common.syntax.psi.EditorConfigHeader
import com.intellij.psi.PsiElement
import org.editorconfig.core.EditorConfigAutomatonBuilder.getCachedHeaderAutomaton
import org.editorconfig.language.util.isSubcaseOf

object EditorConfigHeaderSearcherUtil {
  private fun isActualParent(parent: PsiElement, child: PsiElement) =
    parent.containingFile != child.containingFile
    || parent.textOffset < child.textOffset

  fun isPartialOverride(parent: EditorConfigHeader, child: EditorConfigHeader): Boolean {
    if (!isActualParent(parent, child)) return false
    val childAutomaton = getCachedHeaderAutomaton(child)
    val parentAutomaton = getCachedHeaderAutomaton(parent)
    val intersection = childAutomaton.intersection(parentAutomaton)
    return !intersection.isEmpty
  }

  fun isStrictOverride(parent: EditorConfigHeader, child: EditorConfigHeader): Boolean {
    if (!isActualParent(parent, child)) return false
    return child.isSubcaseOf(parent)
  }
}
