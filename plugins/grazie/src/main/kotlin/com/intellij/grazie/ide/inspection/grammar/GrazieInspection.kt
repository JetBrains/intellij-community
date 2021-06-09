// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("DEPRECATION")

package com.intellij.grazie.ide.inspection.grammar

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.text.CheckerRunner
import com.intellij.grazie.text.TextChecker
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextExtractor
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiWhiteSpace
import java.util.*

class GrazieInspection : LocalInspectionTool() {

  override fun getDisplayName() = GrazieBundle.message("grazie.grammar.inspection.grammar.text")

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    val checkers = TextChecker.allCheckers()
    val checkedDomains = checkedDomains()
    val fileLanguage = holder.file.language

    return object : PsiElementVisitor() {
      override fun visitElement(element: PsiElement) {
        if (element is PsiWhiteSpace || areChecksDisabled(element, fileLanguage)) return

        val extracted = TextExtractor.findUniqueTextAt(element, checkedDomains) ?: return
        if (extracted.length > 50_000) return // too large text

        val runner = CheckerRunner(extracted)
        val warnings = runner.toProblemDescriptors(runner.run(checkers), isOnTheFly)
        warnings.forEach(holder::registerProblem)
      }
    }
  }

  companion object {
    internal fun checkedDomains(): Set<TextContent.TextDomain> {
      val result = EnumSet.of(TextContent.TextDomain.PLAIN_TEXT)
      if (GrazieConfig.get().checkingContext.isCheckInStringLiteralsEnabled) {
        result.add(TextContent.TextDomain.LITERALS)
      }
      if (GrazieConfig.get().checkingContext.isCheckInCommentsEnabled) {
        result.add(TextContent.TextDomain.COMMENTS)
      }
      if (GrazieConfig.get().checkingContext.isCheckInDocumentationEnabled) {
        result.add(TextContent.TextDomain.DOCUMENTATION)
      }
      return result
    }

    internal fun areChecksDisabled(element: PsiElement, fileLanguage: Language): Boolean {
      var psiLanguage = element.language
      if (fileLanguage.isKindOf(psiLanguage)) psiLanguage = fileLanguage // e.g. XML PSI in HTML files
      return psiLanguage.id in GrazieConfig.get().checkingContext.disabledLanguages
    }
  }
}
