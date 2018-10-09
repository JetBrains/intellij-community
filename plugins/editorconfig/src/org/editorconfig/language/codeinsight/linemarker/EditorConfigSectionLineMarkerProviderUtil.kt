// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.linemarker

import com.intellij.psi.PsiElement
import org.editorconfig.core.EditorConfigAutomatonBuilder.getCachedHeaderAutomaton
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.util.isSubcaseOf

object EditorConfigSectionLineMarkerProviderUtil {
  private fun isActualParent(parent: PsiElement, child: PsiElement) =
    parent.containingFile != child.containingFile
    || parent.textOffset < child.textOffset

  fun createActualParentFilter(child: EditorConfigHeader) = { parent: EditorConfigHeader -> isActualParent(parent, child) }
  fun createActualChildFilter(parent: EditorConfigHeader) = { child: EditorConfigHeader -> isActualParent(parent, child) }

  fun isPartialOverride(parent: EditorConfigHeader, child: EditorConfigHeader): Boolean {
    if (child.isSubcaseOf(parent)) return false
    val childAutomaton = getCachedHeaderAutomaton(child)
    val parentAutomaton = getCachedHeaderAutomaton(parent)
    val intersection = childAutomaton.intersection(parentAutomaton)
    return !intersection.isEmpty
  }
}
