// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.annotator.intentions.AddParenthesisToLambdaParameterAction
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrRemoveModifierFix
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GrLambdaExpression
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition


/**
 * Check features introduced in groovy 3.0
 */
class GroovyAnnotator30(private val holder: AnnotationHolder) : GroovyElementVisitor() {

  override fun visitModifierList(modifierList: GrModifierList) {
    checkDefaultModifier(modifierList)
  }

  private fun checkDefaultModifier(modifierList: GrModifierList) {
    val modifier = modifierList.getModifier(PsiModifier.DEFAULT) ?: return

    val parentClass = PsiTreeUtil.getParentOfType(modifier, PsiClass::class.java) ?: return
    if (!parentClass.isInterface || (parentClass as? GrTypeDefinition)?.isTrait == true) {
      val annotation = holder.createWarningAnnotation(modifier, GroovyBundle.message("illegal.default.modifier"))
      registerFix(annotation, GrRemoveModifierFix(PsiModifier.DEFAULT, GroovyBundle.message("illegal.default.modifier.fix")), modifier)
    }
  }

  override fun visitLambdaExpression(expression: GrLambdaExpression) {
    checkSingleArgumentLambda(expression)
    super.visitLambdaExpression(expression)
  }

  private fun checkSingleArgumentLambda(lambda: GrLambdaExpression) {
    val parameterList = lambda.parameterList
    if (parameterList.lParen != null) return
    val parent = lambda.parent
    when (parent) {
      is GrAssignmentExpression, is GrVariable, is GrParenthesizedExpression -> return
      is GrArgumentList -> if (parent.parent is GrMethodCallExpression) return
    }

    holder.createErrorAnnotation(parameterList, GroovyBundle.message("illegal.single.argument.lambda")).apply {
      registerFix(AddParenthesisToLambdaParameterAction(lambda))
    }
  }
}

