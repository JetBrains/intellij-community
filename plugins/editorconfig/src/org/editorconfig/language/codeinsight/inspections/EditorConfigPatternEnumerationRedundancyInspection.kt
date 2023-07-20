// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveBracesQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigEnumerationPattern
import org.editorconfig.language.psi.EditorConfigVisitor

class EditorConfigPatternEnumerationRedundancyInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : EditorConfigVisitor() {
    override fun visitEnumerationPattern(patternEnumeration: EditorConfigEnumerationPattern) {
      if (!containsIssue(patternEnumeration)) return
      val message = EditorConfigBundle.get("inspection.pattern-enumeration.redundant.message")
      holder.registerProblem(patternEnumeration, message, ProblemHighlightType.WARNING, EditorConfigRemoveBracesQuickFix())
    }
  }

  companion object {
    fun containsIssue(patternEnumeration: EditorConfigEnumerationPattern) =
      patternEnumeration.patternList.size <= 1
  }
}
