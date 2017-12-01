// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.style

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle.message
import org.jetbrains.plugins.groovy.codeInspection.GroovySuppressableInspectionTool
import org.jetbrains.plugins.groovy.codeInspection.fixes.RemoveElementQuickFix
import org.jetbrains.plugins.groovy.lang.psi.api.GrImportAlias
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement

class GrUnnecessaryAliasInspection : GroovySuppressableInspectionTool(), CleanupLocalInspectionTool {

  companion object {
    @JvmStatic
    private val fix = RemoveElementQuickFix(message("unnecessary.alias.fix"))
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : PsiElementVisitor() {

    override fun visitElement(element: PsiElement) {
      val alias = element as? GrImportAlias ?: return
      val aliasName = alias.name ?: return
      val statement = alias.parent as? GrImportStatement ?: return
      val reference = statement.importReference ?: return
      val name = reference.referenceName ?: return
      if (aliasName == name) {
        holder.registerProblem(
          alias,
          message("unnecessary.alias.description"),
          ProblemHighlightType.LIKE_UNUSED_SYMBOL,
          fix
        )
      }
    }
  }
}