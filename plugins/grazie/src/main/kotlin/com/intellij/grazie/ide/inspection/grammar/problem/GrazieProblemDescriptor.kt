// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.inspection.grammar.problem

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.grazie.grammar.Typo
import com.intellij.grazie.ide.fus.GrazieFUSCounter
import com.intellij.grazie.ide.inspection.grammar.quickfix.GrazieAddExceptionQuickFix
import com.intellij.grazie.ide.inspection.grammar.quickfix.GrazieDisableRuleQuickFix
import com.intellij.grazie.ide.inspection.grammar.quickfix.GrazieReplaceTypoQuickFix
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.utils.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.containers.Interner
import com.intellij.util.containers.toArray
import kotlinx.html.*

class GrazieProblemDescriptor(fix: Typo, isOnTheFly: Boolean) : ProblemDescriptorBase(
  fix.location.element!!,
  fix.location.element!!,
  fix.toDescriptionTemplate(isOnTheFly),
  fix.toFixes(isOnTheFly).toArray(LocalQuickFix.EMPTY_ARRAY),
  ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
  false,
  fix.toSelectionRange(),
  true,
  isOnTheFly
) {

  companion object {
    private val interner = Interner.createWeakInterner<String>()

    private fun Typo.toFixes(isOnTheFly: Boolean): List<LocalQuickFix> {
      val fixes = ArrayList<LocalQuickFix>()

      if (isOnTheFly && !ApplicationManager.getApplication().isUnitTestMode) {
        if (this.fixes.isNotEmpty()) {
          GrazieFUSCounter.typoFound(this@toFixes)
          fixes.addAll(GrazieReplaceTypoQuickFix(this@toFixes).getAllAsFixes())
        }
        fixes.add(GrazieAddExceptionQuickFix(this))
        fixes.add(GrazieDisableRuleQuickFix(this))
      }

      return fixes
    }

    @NlsSafe
    private fun Typo.toDescriptionTemplate(isOnTheFly: Boolean): String {
      if (ApplicationManager.getApplication().isUnitTestMode) return info.rule.id
      val html = html {
        p {
          info.incorrectExample?.let {
            style = "padding-bottom: 8px;"
          }

          +info.message
          if (!isOnTheFly) nbsp()
        }

        table {
          cellpading = "0"
          cellspacing = "0"

          info.incorrectExample?.let {
            tr {
              td {
                valign = "top"
                style = "padding-right: 5px; color: gray; vertical-align: top;"
                +" "
                +msg("grazie.settings.grammar.rule.incorrect")
                +" "
                if (!isOnTheFly) nbsp()
              }
              td {
                style = "width: 100%;"
                toIncorrectHtml(it)
                if (!isOnTheFly) nbsp()
              }
            }

            if (it.corrections.any { correction -> correction.isNullOrBlank().not() }) {
              tr {
                td {
                  valign = "top"
                  style = "padding-top: 5px; padding-right: 5px; color: gray; vertical-align: top;"
                  +" "
                  +msg("grazie.settings.grammar.rule.correct")
                  +" "
                  if (!isOnTheFly) nbsp()
                }
                td {
                  style = "padding-top: 5px; width: 100%;"
                  toCorrectHtml(it)
                  if (!isOnTheFly) nbsp()
                }
              }
            }
          }
        }

        p {
          style = "text-align: left; font-size: x-small; color: gray; padding-top: 10px; padding-bottom: 0px;"
          +" "
          +msg("grazie.tooltip.powered-by-language-tool")
        }
      }
      return interner.intern(html)
    }
  }
}
