// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveHeaderElementQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigAsteriskPattern
import org.editorconfig.language.psi.EditorConfigDoubleAsteriskPattern
import org.editorconfig.language.psi.EditorConfigVisitor

class EditorConfigWildcardRedundancyInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : EditorConfigVisitor() {
    override fun visitDoubleAsteriskPattern(pattern: EditorConfigDoubleAsteriskPattern) = checkSiblings(pattern)
    override fun visitAsteriskPattern(pattern: EditorConfigAsteriskPattern) = checkSiblings(pattern)
    fun checkSiblings(element: PsiElement) {
      if (element.prevSibling is EditorConfigDoubleAsteriskPattern || element.nextSibling is EditorConfigDoubleAsteriskPattern) {
        val message = EditorConfigBundle["inspection.pattern.double-asterisk-sibling.message"]
        holder.registerProblem(
          element,
          message,
          ProblemHighlightType.LIKE_UNUSED_SYMBOL,
          EditorConfigRemoveHeaderElementQuickFix()
        )
      }
    }
  }
}
