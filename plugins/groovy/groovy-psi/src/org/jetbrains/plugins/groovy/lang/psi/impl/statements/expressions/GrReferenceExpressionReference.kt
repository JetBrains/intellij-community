// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions

import com.intellij.lang.java.beans.PropertyKind
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrSuperReferenceResolver.resolveSuperExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrThisReferenceResolver.resolveThisExpression
import org.jetbrains.plugins.groovy.lang.psi.util.getRValue
import org.jetbrains.plugins.groovy.lang.psi.util.isCompileStatic
import org.jetbrains.plugins.groovy.lang.psi.util.isPropertyName
import org.jetbrains.plugins.groovy.lang.resolve.GrReferenceResolveRunner
import org.jetbrains.plugins.groovy.lang.resolve.GrResolverProcessor
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCachingReference
import org.jetbrains.plugins.groovy.lang.resolve.processors.AccessorAwareProcessor
import org.jetbrains.plugins.groovy.lang.resolve.processors.AccessorProcessor
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolveKind
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolveKind.*
import java.util.*

abstract class GrReferenceExpressionReference(ref: GrReferenceExpressionImpl) : GroovyCachingReference<GrReferenceExpressionImpl>(ref) {

  override fun doResolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    require(!incomplete)
    val staticResults = element.staticReference.resolve(incomplete)
    if (staticResults.isNotEmpty()) {
      return staticResults
    }
    return doResolveNonStatic()
  }

  protected open fun doResolveNonStatic(): Collection<GroovyResolveResult> {
    val expression = element
    val name = expression.referenceName ?: return emptyList()
    val kinds = expression.resolveKinds()
    val processor = buildProcessor(name, expression, kinds)
    GrReferenceResolveRunner(expression, processor).resolveReferenceExpression()
    return processor.results
  }

  protected abstract fun buildProcessor(name: String, place: PsiElement, kinds: Set<GroovyResolveKind>): GrResolverProcessor<*>
}

class GrRValueExpressionReference(ref: GrReferenceExpressionImpl) : GrReferenceExpressionReference(ref) {

  override fun doResolveNonStatic(): Collection<GroovyResolveResult> {
    return element.handleSpecialCases()
           ?: super.doResolveNonStatic()
  }

  override fun buildProcessor(name: String, place: PsiElement, kinds: Set<GroovyResolveKind>): GrResolverProcessor<*> {
    return rValueProcessor(name, place, kinds)
  }
}

class GrLValueExpressionReference(ref: GrReferenceExpressionImpl) : GrReferenceExpressionReference(ref) {

  override fun buildProcessor(name: String, place: PsiElement, kinds: Set<GroovyResolveKind>): GrResolverProcessor<*> {
    val rValue = requireNotNull(element.getRValue())
    return lValueProcessor(name, place, kinds, rValue)
  }
}

private fun GrReferenceExpression.handleSpecialCases(): Collection<GroovyResolveResult>? {
  when (referenceNameElement?.node?.elementType) {
    GroovyElementTypes.KW_THIS -> return resolveThisExpression(this)
    GroovyElementTypes.KW_SUPER -> return resolveSuperExpression(this)
    GroovyElementTypes.KW_CLASS -> {
      if (!isCompileStatic(this) && qualifier?.type == null) {
        return emptyList()
      }
    }
  }
  return null
}

fun GrReferenceExpression.resolveKinds(): Set<GroovyResolveKind> {
  return resolveKinds(isQualified)
}

fun resolveKinds(qualified: Boolean): Set<GroovyResolveKind> {
  return if (qualified) {
    EnumSet.of(FIELD, PROPERTY, VARIABLE)
  }
  else {
    EnumSet.of(FIELD, PROPERTY, VARIABLE, BINDING)
  }
}

fun rValueProcessor(name: String, place: PsiElement, kinds: Set<GroovyResolveKind>): GrResolverProcessor<*> {
  val accessorProcessors = if (name.isPropertyName())
    listOf(
      AccessorProcessor(name, PropertyKind.GETTER, emptyList(), place),
      AccessorProcessor(name, PropertyKind.BOOLEAN_GETTER, emptyList(), place)
    )
  else {
    emptyList()
  }
  return AccessorAwareProcessor(name, place, kinds, accessorProcessors)
}

fun lValueProcessor(name: String, place: PsiElement, kinds: Set<GroovyResolveKind>, argument: Argument?): GrResolverProcessor<*> {
  val accessorProcessors = if (name.isPropertyName()) {
    listOf(AccessorProcessor(name, PropertyKind.SETTER, argument?.let(::listOf), place))
  }
  else {
    emptyList()
  }
  return AccessorAwareProcessor(name, place, kinds, accessorProcessors)
}
