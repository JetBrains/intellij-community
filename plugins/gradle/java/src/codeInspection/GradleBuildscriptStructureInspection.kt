// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.codeInspection.GroovyLocalInspectionTool
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall

class GradleBuildscriptStructureInspection : GroovyLocalInspectionTool() {

  override fun buildGroovyVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): GroovyElementVisitor = object: GroovyElementVisitor() {
    override fun visitFile(file: GroovyFileBase) {
      val statements = file.statements
      val lastPluginsStatement = statements.indexOfFirst { it is GrMethodCall && it.invokedExpression.text == "plugins" }
      if (lastPluginsStatement == -1) {
        return
      }
      val statementsToCheck = statements.asList().subList(0, lastPluginsStatement)
      for (suspiciousStatement in statementsToCheck) {
        val psiToHighlight = getBadStatementHighlightingElement(suspiciousStatement) ?: continue
        holder.registerProblem(psiToHighlight, GradleInspectionBundle.message("inspection.message.incorrect.buildscript.structure"), ProblemHighlightType.GENERIC_ERROR)
      }
    }
  }

  private val allowedStrings = setOf("buildscript", "pluginManagement", "plugins")

  private fun getBadStatementHighlightingElement(suspiciousStatement: GrStatement): PsiElement? {
    if (suspiciousStatement !is GrMethodCall) {
      return suspiciousStatement
    }
    if (suspiciousStatement.invokedExpression.text !in allowedStrings) {
      return suspiciousStatement.invokedExpression
    }
    return null
  }


}