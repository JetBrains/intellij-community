// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemHighlightType.*
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter
import org.jetbrains.plugins.groovy.highlighting.HighlightSink

internal class UnresolvedReferenceInspectionSink(private val problemsHolder: ProblemsHolder) : HighlightSink {

  override fun registerProblem(highlightElement: PsiElement,
                               highlightType: ProblemHighlightType,
                               message: String,
                               vararg fixes: LocalQuickFix?) {
    if (problemsHolder.isOnTheFly && highlightType === LIKE_UNKNOWN_SYMBOL && handleSpecial(highlightElement, message, *fixes)) {
      return
    }
    problemsHolder.registerProblem(highlightElement, message, highlightType, *fixes)
  }

  private fun handleSpecial(element: PsiElement, @InspectionMessage message: String, vararg fixes: LocalQuickFix?): Boolean {
    // at this point we register the problem with LIKE_UNKNOWN_SYMBOL type.
    val level = GrUnresolvedAccessInspection.getHighlightDisplayLevel(element)
    when (level) {
      HighlightDisplayLevel.ERROR -> {
        // If we override LIKE_UNKNOWN_SYMBOL with GENERIC_ERROR_OR_WARNING (i.e. go into else branch of this statement),
        // and the level is ERROR, then the reference would be highlighted with red waved line [HighlightInfoType#ERROR].
        // But is case of ERROR we actually want the reference to be red, and coincidentally
        // LIKE_UNKNOWN_SYMBOL + ERROR already results in [HighlightInfoType#WRONG_REF], so we just do nothing.
        // @see com.intellij.codeInspection.ProblemDescriptorUtil.getHighlightInfoType
        return false
      }
      HighlightDisplayLevel.WEAK_WARNING -> {
        // WEAK_WARNING is default inspection level, and in this case we override text attributes and severity of the reference
        val newHighlightType = if (ApplicationManager.getApplication().isUnitTestMode) WARNING else INFORMATION
        val descriptor = problemsHolder.manager.createProblemDescriptor(
          element, message, problemsHolder.isOnTheFly, fixes, newHighlightType
        )
        descriptor.setTextAttributes(GroovySyntaxHighlighter.UNRESOLVED_ACCESS)
        problemsHolder.registerProblem(descriptor)
        return true
      }
      else -> {
        // In all other cases we want the reference to be highlighted using inspection settings,
        // so we use GENERIC_ERROR_OR_WARNING instead of LIKE_UNKNOWN_SYMBOL
        problemsHolder.registerProblem(element, message, GENERIC_ERROR_OR_WARNING, *fixes)
        return true
      }
    }
  }
}
