// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigCleanupDotsQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigElementTypes
import org.editorconfig.language.psi.EditorConfigQualifiedOptionKey
import org.editorconfig.language.psi.EditorConfigVisitor

class EditorConfigMultipleDotsInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : EditorConfigVisitor() {
    override fun visitQualifiedOptionKey(key: EditorConfigQualifiedOptionKey) {
      var firstDot: PsiElement? = null
      var lastDot: PsiElement? = null
      SyntaxTraverser.psiTraverser().children(key).forEach {
        when {
          it.node.elementType == EditorConfigElementTypes.DOT -> {
            if (firstDot == null) {
              firstDot = it
            }
            lastDot = it
          }
          firstDot != lastDot -> {
            val message = EditorConfigBundle["inspection.key.multiple-dots.message"]
            val start = firstDot!!.startOffsetInParent
            val end = lastDot!!.startOffsetInParent + lastDot!!.textLength
            val range = TextRange.create(start, end)
            holder.registerProblem(key, range, message, EditorConfigCleanupDotsQuickFix())
            firstDot = null
            lastDot = null
          }
          else -> {
            firstDot = null
            lastDot = null
          }
        }
      }
    }
  }
}
