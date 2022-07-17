// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.inline

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.util.parentsOfType
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression

/**
 * @return a performer for which [element] is a transformation root
 */
fun getRootInlineTransformationPerformer(element: GroovyPsiElement): GroovyInlineASTTransformationPerformer? {
  for (extension in EP_NAME.extensionList) {
    val performer = extension.getPerformer(element)
    if (performer != null) {
      return performer
    }
  }
  return null
}

private val EP_NAME: ExtensionPointName<GroovyInlineASTTransformationSupport> = ExtensionPointName.create("org.intellij.groovy.inlineASTTransformationSupport")

fun getHierarchicalInlineTransformationData(element: PsiElement): Pair<GroovyPsiElement, GroovyInlineASTTransformationPerformer>? {
  return element.parentsOfType<GroovyPsiElement>().firstNotNullOfOrNull { getRootInlineTransformationPerformer(it)?.let(it::to) }
}

fun getHierarchicalInlineTransformationPerformer(element: PsiElement): GroovyInlineASTTransformationPerformer? {
  return getHierarchicalInlineTransformationData(element)?.second
}

internal fun getTypeFromInlineTransformation(expr: GrExpression): PsiType? {
  val support = getHierarchicalInlineTransformationPerformer(expr)
  if (support != null) {
    val macroType = support.computeType(expr)
    if (macroType != null) {
      return macroType
    }
  }
  return null
}
