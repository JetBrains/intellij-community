// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.cs

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner
import org.jetbrains.plugins.groovy.refactoring.DefaultGroovyVariableNameValidator
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil

internal const val defaultFixVariableName = "storedList"

class GrReplaceMultiAssignmentFix(val size: Int) : PsiUpdateModCommandQuickFix() {
  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    if (element !is GrExpression) return
    val grStatement = element.parent as? GrStatement ?: return
    val grStatementOwner = grStatement.parent as? GrStatementOwner ?: return

    var initializer = element.text
    if (element !is GrReferenceExpression || element.resolve() !is GrVariable) {
      val factory = GroovyPsiElementFactory.getInstance(element.project)
      val fixVariableName = generateVariableName(element)
      val varDefinition = factory.createStatementFromText("def ${fixVariableName} = ${initializer}")
      grStatementOwner.addStatementBefore(varDefinition, grStatement)
      initializer = fixVariableName
    }

    GrInspectionUtil.replaceExpression(element, generateListLiteral(initializer))
  }

  private fun generateVariableName(expression: GrExpression): String {
    val validator = DefaultGroovyVariableNameValidator(expression)
    val suggestedNames = GroovyNameSuggestionUtil.suggestVariableNameByType(expression.type, validator)
    return if (suggestedNames.isNotEmpty()) suggestedNames[0] else defaultFixVariableName
  }

  private fun generateListLiteral(varName: String): String {
    return (0..<size).joinToString(", ", "[", "]") { "$varName[$it]" }
  }

  override fun getName(): String {
    return GroovyBundle.message("replace.with.list.literal")
  }

  @Nls
  override fun getFamilyName(): String {
    return GroovyBundle.message("replace.with.list.literal")
  }
}
