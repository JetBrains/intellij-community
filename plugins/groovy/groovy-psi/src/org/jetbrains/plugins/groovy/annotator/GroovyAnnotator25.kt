// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.elementType
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.annotator.inspections.GroovyComplexArgumentLabelQuickFix
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GrLambdaExpression
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.isCompileStatic
import org.jetbrains.plugins.groovy.transformations.immutable.hasImmutableAnnotation
import org.jetbrains.plugins.groovy.transformations.immutable.isImmutable
import org.jetbrains.plugins.groovy.transformations.impl.namedVariant.collectAllParamsFromNamedVariantMethod
import org.jetbrains.plugins.groovy.transformations.impl.namedVariant.collectNamedParams

/**
 * Check features introduced in groovy 2.5
 */
class GroovyAnnotator25(private val holder: AnnotationHolder) : GroovyElementVisitor() {

  override fun visitMethod(method: GrMethod) {
    collectAllParamsFromNamedVariantMethod(method).groupBy { it.first }.filter { it.value.size > 1 }.forEach { (name, parameters) ->
      val parametersList = parameters.joinToString { "'${it.second.name}'" }
      val duplicatingParameters = parameters.drop(1).map { (_, parameter) -> parameter }
      for (parameter in duplicatingParameters) {
        val nameIdentifier = parameter.nameIdentifier ?: continue
        holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("duplicating.named.parameter", name, parametersList)).range(nameIdentifier).create()
      }
    }
    super.visitMethod(method)
  }

  override fun visitField(field: GrField) {
    super.visitField(field)
    immutableCheck(field)
  }

  private fun immutableCheck(field: GrField) {
    val containingClass = field.containingClass ?: return
    if (!field.hasModifierProperty(PsiModifier.STATIC) && hasImmutableAnnotation(containingClass) && !isImmutable(field)) {
      holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("field.should.be.immutable", field.name)).range(field.nameIdentifierGroovy).create()
    }
  }

  override fun visitCallExpression(callExpression: GrCallExpression) {
    checkRequiredNamedArguments(callExpression)

    super.visitCallExpression(callExpression)
  }

  override fun visitArgumentLabel(argument: GrArgumentLabel) {
    val element = argument.nameElement
    if (element !is GrExpression) return
    val elementType = element.elementType ?: return

    if (elementType == STRING_SQ || elementType == STRING_DQ || elementType == STRING_TSQ || elementType == STRING_TDQ) return

    if (element is GrLiteral || element is GrListOrMap || element is GrLambdaExpression || element is GrClosableBlock) return

    if (element is GrParenthesizedExpression) return


    holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("groovy.complex.argument.label.annotator.message")).range(argument)
      .withFix(GroovyComplexArgumentLabelQuickFix(element)).create()
  }

  private fun checkRequiredNamedArguments(callExpression: GrCallExpression) {
    if (!isCompileStatic(callExpression)) return
    val namedArguments = callExpression.namedArguments.mapNotNull { it.labelName }.toSet()
    if (namedArguments.isEmpty()) return

    val resolveResult = callExpression.advancedResolve()
    val method = resolveResult.element as? PsiMethod ?: return

    val parameters = method.parameterList.parameters
    val mapParameter = parameters.firstOrNull() ?: return

    val requiredNamedParams = collectNamedParams(mapParameter).filter { it.required }

    for (namedParam in requiredNamedParams) {
      if (!namedArguments.contains(namedParam.name)) {
        val message = GroovyBundle.message("missing.required.named.parameter", namedParam.name)
        holder.newAnnotation(HighlightSeverity.ERROR, message).create()
      }
    }
  }
}