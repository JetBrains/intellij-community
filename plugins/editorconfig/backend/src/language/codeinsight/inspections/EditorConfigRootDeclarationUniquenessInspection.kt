// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.editorconfig.common.EditorConfigBundle
import com.intellij.editorconfig.common.syntax.psi.EditorConfigRootDeclaration
import com.intellij.editorconfig.common.syntax.psi.EditorConfigVisitor
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigRemoveRootDeclarationQuickFix
import java.util.concurrent.atomic.AtomicBoolean

class EditorConfigRootDeclarationUniquenessInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): EditorConfigVisitor = object : EditorConfigVisitor() {
    private val visitedRootDeclaration = AtomicBoolean()
    override fun visitRootDeclaration(declaration: EditorConfigRootDeclaration) {
      if (!visitedRootDeclaration.compareAndSet(false, true)) {
        holder.registerProblem(
          declaration,
          EditorConfigBundle.get("inspection.root-declaration.uniqueness.message"),
          EditorConfigRemoveRootDeclarationQuickFix()
        )
      }
    }
  }
}
