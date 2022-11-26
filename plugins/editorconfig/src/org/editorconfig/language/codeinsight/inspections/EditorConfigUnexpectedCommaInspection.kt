// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigCleanupValueListQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigElementTypes
import org.editorconfig.language.psi.EditorConfigOptionValueList
import org.editorconfig.language.psi.EditorConfigVisitor
import org.editorconfig.language.util.EditorConfigPsiTreeUtil

class EditorConfigUnexpectedCommaInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : EditorConfigVisitor() {
    override fun visitOptionValueList(list: EditorConfigOptionValueList) {
      val badCommas = findBadCommas(list)
      val message by lazy { EditorConfigBundle["inspection.value.list.comma.unexpected.message"] }
      val quickFix by lazy { EditorConfigCleanupValueListQuickFix() }
      badCommas.forEach {
        holder.registerProblem(it, message, quickFix)
      }
    }
  }

  companion object {
    fun findBadCommas(list: EditorConfigOptionValueList): List<PsiElement> {
      val ranges = list.optionValueIdentifierList
        .asSequence()
        .zipWithNext()
        .map { (previous, current) -> TextRange(previous.textRange.endOffset, current.textRange.startOffset) }
        .toMutableList()
      val commas = findCommas(list)

      return commas.filter { comma ->
        val range = ranges.firstOrNull { it.contains(comma.textOffset) } ?: return@filter true
        ranges.remove(range)
        false
      }
    }

    private fun findCommas(list: EditorConfigOptionValueList): List<PsiElement> {
      val result = mutableListOf<PsiElement>()
      EditorConfigPsiTreeUtil.iterateVisibleChildren(list) {
        if (it.node.elementType == EditorConfigElementTypes.COMMA) {
          result.add(it)
        }
      }
      return result
    }
  }
}
