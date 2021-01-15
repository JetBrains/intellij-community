// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
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