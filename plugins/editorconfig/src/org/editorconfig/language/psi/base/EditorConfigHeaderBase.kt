// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.base

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import org.editorconfig.language.codeinsight.inspections.EditorConfigEmptyHeaderInspection
import org.editorconfig.language.codeinsight.inspections.EditorConfigNumerousWildcardsInspection
import org.editorconfig.language.codeinsight.inspections.EditorConfigPatternEnumerationRedundancyInspection
import org.editorconfig.language.psi.EditorConfigEnumerationPattern
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.reference.EditorConfigHeaderReference
import org.editorconfig.language.util.EditorConfigPsiTreeUtil.containsErrors

abstract class EditorConfigHeaderBase(node: ASTNode) : EditorConfigHeaderElementBase(node), EditorConfigHeader {
  final override fun isValidGlob(): Boolean {
    if (header.textMatches("[")) return false
    if (containsErrors(header)) return false
    // That is, if closing bracket is missing
    if (nextSibling is PsiErrorElement) return false
    if (EditorConfigEmptyHeaderInspection.containsIssue(header)) return false
    if (EditorConfigNumerousWildcardsInspection.containsIssue(header)) return false
    if (PsiTreeUtil.findChildrenOfAnyType(header, EditorConfigEnumerationPattern::class.java).any(patternChecker)) return false
    return true
  }

  final override fun getReference() = EditorConfigHeaderReference(this)

  private companion object {
    private val patternChecker = EditorConfigPatternEnumerationRedundancyInspection.Companion::containsIssue
  }
}
