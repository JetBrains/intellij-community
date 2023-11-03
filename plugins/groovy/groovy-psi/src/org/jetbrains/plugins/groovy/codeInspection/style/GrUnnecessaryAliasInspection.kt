// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.style

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.fixes.RemoveElementQuickFix
import org.jetbrains.plugins.groovy.lang.psi.api.GrImportAlias
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement

class GrUnnecessaryAliasInspection : LocalInspectionTool(), CleanupLocalInspectionTool {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {

    override fun visitElement(element: PsiElement) {
      val alias = element as? GrImportAlias ?: return
      val aliasName = alias.name ?: return
      val statement = alias.parent as? GrImportStatement ?: return
      val reference = statement.importReference ?: return
      val name = reference.referenceName ?: return
      if (aliasName == name) {
        holder.registerProblem(
          alias,
          GroovyBundle.message("unnecessary.alias.description"),
          RemoveElementQuickFix(GroovyBundle.message("unnecessary.alias.fix"))
        )
      }
    }
  }
}