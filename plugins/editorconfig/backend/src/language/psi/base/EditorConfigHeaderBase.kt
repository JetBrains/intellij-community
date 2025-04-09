// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.psi.base

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import org.editorconfig.language.codeinsight.inspections.hasNumerousWildcards
import org.editorconfig.language.codeinsight.inspections.hasRedundancy
import org.editorconfig.language.codeinsight.inspections.isEmptyHeader
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
    if (header.isEmptyHeader()) return false
    if (header.hasNumerousWildcards()) return false
    if (PsiTreeUtil.findChildrenOfAnyType(header, EditorConfigEnumerationPattern::class.java).any { it.hasRedundancy() }) return false
    return true
  }

  final override fun getReference() = EditorConfigHeaderReference(this)

}
