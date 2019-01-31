// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigConvertToPlainPatternQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigCharClass
import org.editorconfig.language.psi.EditorConfigVisitor

class EditorConfigCharClassRedundancyInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : EditorConfigVisitor() {
    override fun visitCharClass(charClass: EditorConfigCharClass) {
      if (charClass.charClassExclamation != null) return
      if (charClass.charClassLetterList.size != 1) return

      val message = EditorConfigBundle["inspection.charclass.redundant.message"]
      holder.registerProblem(charClass, message, EditorConfigConvertToPlainPatternQuickFix())
    }
  }
}
