// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveRootDeclarationQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigRootDeclaration
import org.editorconfig.language.psi.EditorConfigVisitor

class EditorConfigRootDeclarationUniquenessInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : EditorConfigVisitor() {
    private var visitedRootDeclaration = false
    override fun visitRootDeclaration(declaration: EditorConfigRootDeclaration) {
      if (!visitedRootDeclaration) {
        visitedRootDeclaration = true
        return
      }

      holder.registerProblem(
        declaration,
        EditorConfigBundle.get("inspection.root-declaration.uniqueness.message"),
        EditorConfigRemoveRootDeclarationQuickFix()
      )
    }
  }
}
