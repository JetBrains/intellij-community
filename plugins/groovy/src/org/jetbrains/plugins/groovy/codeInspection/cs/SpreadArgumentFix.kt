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
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrSpreadArgument
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner
import org.jetbrains.plugins.groovy.refactoring.DefaultGroovyVariableNameValidator
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil

class SpreadArgumentFix(val size: Int) : GroovyFix() {
  override fun doFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement as? GrSpreadArgument ?: return
    val grArgumentList = PsiTreeUtil.getParentOfType(element, GrArgumentList::class.java) ?: return

    val replaceText = handleListLiteral(element) ?: genReplaceText(element) ?: return

    val newArgumentList = GroovyPsiElementFactory.getInstance(element.project).createArgumentListFromText(replaceText)
    grArgumentList.replaceWithArgumentList(newArgumentList)
  }

  private fun handleListLiteral(element: GrSpreadArgument) : String? {
    val grListOrMap = element.argument as? GrListOrMap ?: return null
    if (grListOrMap.isMap) return null
    return grListOrMap.initializers.joinToString(", ", "(", ")") { it.text }
  }

  private fun genReplaceText(element: GrSpreadArgument) : String? {
    val grStatementOwner = PsiTreeUtil.getParentOfType(element, GrStatementOwner::class.java) ?: return null
    val prevStatement = PsiTreeUtil.findPrevParent(grStatementOwner, element) as? GrStatement ?: return null
    val expression = element.argument
    var initializer = expression.text
    if (expression !is GrReferenceExpression || expression.resolve() !is GrVariable) {
      val factory = GroovyPsiElementFactory.getInstance(element.project)
      val fixVariableName = generateVariableName(element)
      val varDefinition = factory.createStatementFromText("def ${fixVariableName} = ${initializer}")
      grStatementOwner.addStatementBefore(varDefinition, prevStatement)
      initializer = fixVariableName
    }
    return generateFixLine(initializer)
  }

  private fun generateVariableName(expression: GrExpression): String {
    val validator = DefaultGroovyVariableNameValidator(expression)
    val suggestedNames = GroovyNameSuggestionUtil.suggestVariableNameByType(expression.type, validator)
    return if (suggestedNames.isNotEmpty()) suggestedNames[0] else defaultFixVariableName
  }

  private fun generateFixLine(varName: String): String {
    return (0..(size - 1)).joinToString(", ", "(", ")") { "$varName[$it]" }
  }

  override fun getName(): String {
    return GroovyBundle.message("replace.with.get.at")
  }

  @Nls
  override fun getFamilyName(): String {
    return GroovyBundle.message("replace.with.get.at")
  }
}