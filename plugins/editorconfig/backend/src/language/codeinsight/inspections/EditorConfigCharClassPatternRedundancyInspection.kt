// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.editorconfig.common.EditorConfigBundle
import com.intellij.editorconfig.common.syntax.psi.EditorConfigCharClassPattern
import com.intellij.editorconfig.common.syntax.psi.EditorConfigVisitor
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigConvertToPlainPatternQuickFix

class EditorConfigCharClassPatternRedundancyInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): EditorConfigVisitor = object : EditorConfigVisitor() {
    override fun visitCharClassPattern(charClass: EditorConfigCharClassPattern) {
      if (charClass.charClassExclamation != null) return
      if (charClass.charClassLetterList.size != 1) return

      val message = EditorConfigBundle["inspection.charclass.redundant.message"]
      holder.registerProblem(charClass, message, EditorConfigConvertToPlainPatternQuickFix())
    }
  }
}
