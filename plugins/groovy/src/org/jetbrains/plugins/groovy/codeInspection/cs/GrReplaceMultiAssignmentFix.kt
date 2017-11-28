/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.codeInspection.cs

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner
import org.jetbrains.plugins.groovy.refactoring.DefaultGroovyVariableNameValidator
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil

internal val defaultFixVariableName = "storedList"

class GrReplaceMultiAssignmentFix(val size: Int) : GroovyFix() {
  override fun doFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement as? GrExpression ?: return
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
    return (0..(size - 1)).joinToString(", ", "[", "]") { "$varName[$it]" }
  }

  override fun getName(): String {
    return GroovyBundle.message("replace.with.list.literal")
  }

  @Nls
  override fun getFamilyName(): String {
    return GroovyBundle.message("replace.with.list.literal")
  }
}
