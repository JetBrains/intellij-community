// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import dk.brics.automaton.Automaton
import dk.brics.automaton.BasicOperations
import org.editorconfig.core.EditorConfigAutomatonBuilder
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveHeaderElementQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigPattern
import org.editorconfig.language.psi.EditorConfigPatternEnumeration
import org.editorconfig.language.psi.EditorConfigVisitor
import org.editorconfig.language.util.isSubcaseOf

class EditorConfigPatternRedundancyInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : EditorConfigVisitor() {
    override fun visitPattern(pattern: EditorConfigPattern) {
      if (!pattern.header.isValidGlob) return
      val otherPatterns = findOtherPatterns(pattern)
      if (otherPatterns.isEmpty()) return

      if (isDuplicate(pattern, otherPatterns)) {
        val message = EditorConfigBundle["inspection.pattern.duplicate.message"]
        holder.registerProblem(pattern, message, EditorConfigRemoveHeaderElementQuickFix())
        return
      }

      val supercase = otherPatterns.firstOrNull { pattern !== it && pattern isSubcaseOf it }
      if (supercase != null) {
        val message = EditorConfigBundle.get("inspection.pattern.redundant.message", supercase.text)
        holder.registerProblem(pattern, message, ProblemHighlightType.LIKE_UNUSED_SYMBOL, EditorConfigRemoveHeaderElementQuickFix())
        return
      }

      val otherAutomatons = otherPatterns.map(EditorConfigAutomatonBuilder::getCachedPatternAutomaton)
      val otherAutomatonsUnion = otherAutomatons.fold(Automaton(), BasicOperations::union)

      if (pattern isSubcaseOf otherAutomatonsUnion) {
        val message = EditorConfigBundle["inspection.pattern.redundant.to.union.message"]
        holder.registerProblem(pattern, message, ProblemHighlightType.LIKE_UNUSED_SYMBOL, EditorConfigRemoveHeaderElementQuickFix())
      }
    }
  }

  private fun isDuplicate(pattern: EditorConfigPattern, allPatterns: List<EditorConfigPattern>) =
    allPatterns.count(pattern::textMatches) >= 2

  private fun findOtherPatterns(pattern: EditorConfigPattern): List<EditorConfigPattern> {
    val parent = pattern.parent
    val allPatterns = when (parent) {
      is EditorConfigPatternEnumeration -> parent.patternList
      else -> listOf(pattern)
    }
    return allPatterns.filter { it !== pattern }
  }
}
