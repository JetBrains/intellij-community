// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveSectionQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigVisitor

class EditorConfigEmptySectionInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : EditorConfigVisitor() {
    override fun visitHeader(header: EditorConfigHeader) {
      if (header.section.optionList.isNotEmpty()) return
      val message = EditorConfigBundle["inspection.section.emptiness.message"]
      holder.registerProblem(header, message, EditorConfigRemoveSectionQuickFix())
    }
  }
}
