// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.editorconfig.common.EditorConfigBundle
import com.intellij.editorconfig.common.syntax.psi.EditorConfigHeader
import com.intellij.editorconfig.common.syntax.psi.EditorConfigVisitor
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveSectionQuickFix

class EditorConfigEmptySectionInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): EditorConfigVisitor = object : EditorConfigVisitor() {
    override fun visitHeader(header: EditorConfigHeader) {
      if (header.section.optionList.isNotEmpty()) return
      val message = EditorConfigBundle["inspection.section.emptiness.message"]
      holder.registerProblem(header, message, EditorConfigRemoveSectionQuickFix())
    }
  }
}
