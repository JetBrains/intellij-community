// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveLineBreaksInOptionQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigOption
import org.editorconfig.language.psi.EditorConfigVisitor

class EditorConfigSuspiciousLineBreakInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : EditorConfigVisitor() {
    override fun visitOption(option: EditorConfigOption) {
      if (!option.textContains('\n')) return
      val message = EditorConfigBundle["inspection.option.suspicious.line.break.message"]
      holder.registerProblem(option, message, EditorConfigRemoveLineBreaksInOptionQuickFix())
    }
  }
}
