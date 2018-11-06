// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions

import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrSuperReferenceResolver.resolveSuperExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrThisReferenceResolver.resolveThisExpression
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.resolve.GrReferenceResolveRunner
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCachingReference
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyRValueProcessor
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolveKind.*
import java.util.*

abstract class GrReferenceExpressionReference(ref: GrReferenceExpressionImpl) : GroovyCachingReference<GrReferenceExpressionImpl>(ref) {

  override fun doResolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    val staticResults = element.staticReference.resolve(incomplete)
    if (staticResults.isNotEmpty()) {
      return staticResults
    }
    return doResolveNonStatic(incomplete)
  }

  abstract fun doResolveNonStatic(incomplete: Boolean): Collection<GroovyResolveResult>
}

class GrRValueExpressionReference(ref: GrReferenceExpressionImpl) : GrReferenceExpressionReference(ref) {

  override fun doResolveNonStatic(incomplete: Boolean): Collection<GroovyResolveResult> {
    val expression = element
    if (expression.parent is GrMethodCall || incomplete) {
      return expression.doPolyResolve(incomplete, true)
    }

    expression.handleSpecialCases()?.let {
      return it
    }
    val name = expression.referenceName ?: return emptyList()
    val kinds = if (expression.isQualified) {
      EnumSet.of(FIELD, PROPERTY, VARIABLE)
    }
    else {
      EnumSet.of(FIELD, PROPERTY, VARIABLE, BINDING)
    }
    val processor = GroovyRValueProcessor(name, expression, kinds)
    GrReferenceResolveRunner(expression, processor).resolveReferenceExpression()
    return processor.results
  }
}

class GrLValueExpressionReference(ref: GrReferenceExpressionImpl) : GrReferenceExpressionReference(ref) {

  override fun doResolveNonStatic(incomplete: Boolean): Collection<GroovyResolveResult> {
    return element.doPolyResolve(incomplete, false)
  }
}

private fun GrReferenceExpression.handleSpecialCases(): Collection<GroovyResolveResult>? {
  when (referenceNameElement?.node?.elementType) {
    GroovyElementTypes.KW_THIS -> return resolveThisExpression(this)
    GroovyElementTypes.KW_SUPER -> return resolveSuperExpression(this)
    GroovyElementTypes.KW_CLASS -> {
      if (!PsiUtil.isCompileStatic(this) && qualifier?.type == null) {
        return emptyList()
      }
    }
  }
  return null
}
