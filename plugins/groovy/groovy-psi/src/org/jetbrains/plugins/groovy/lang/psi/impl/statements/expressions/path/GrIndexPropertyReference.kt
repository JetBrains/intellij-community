// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyReferenceBase
import org.jetbrains.plugins.groovy.lang.psi.util.getArgumentListType
import org.jetbrains.plugins.groovy.lang.psi.util.isClassLiteral
import org.jetbrains.plugins.groovy.lang.psi.util.isSimpleArrayAccess
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolver
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil

class GrIndexPropertyReference(element: GrIndexProperty, val rhs: Boolean) : GroovyReferenceBase<GrIndexProperty>(element) {

  /**
   * Consider expression `foo[a, b, c]`.
   * Its argument list is `[a, b, c]`.
   * - rValue reference, i.e. reference to a getAt() method, will have range of `[`.
   * - lValue reference, i.e. reference to a putAt() method, will have range of `]`.
   */
  override fun getRangeInElement(): TextRange {
    val argumentList = element.argumentList
    val startOffsetInParent = argumentList.startOffsetInParent
    val rangeStart = if (rhs) startOffsetInParent else startOffsetInParent + argumentList.textLength - 1
    return TextRange.from(rangeStart, 1)
  }

  override fun resolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    return TypeInferenceHelper.getCurrentContext().resolve(this, incomplete, Resolver)
  }

  private object Resolver : GroovyResolver<GrIndexPropertyReference> {
    override fun resolve(ref: GrIndexPropertyReference, incomplete: Boolean): Collection<GroovyResolveResult> {
      return ref.element?.doMultiResolve(ref.rhs, incomplete) ?: emptyList()
    }
  }
}

private fun GrIndexProperty.doMultiResolve(rhs: Boolean, incomplete: Boolean): Collection<GroovyResolveResult>? {
  if (isClassLiteral()) return null
  if (isSimpleArrayAccess()) return null

  val argumentListType = getArgumentListType() ?: return null
  val thisType = invokedExpression.type ?: PsiType.getJavaLangObject(manager, resolveScope)

  val rType = if (rhs) null else (parent as? GrAssignmentExpression)?.type
  if (rType == null && !rhs && !incomplete) return null

  val name = if (rhs) "getAt" else "putAt"

  val argTypes = if (rType == null) arrayOf(argumentListType) else arrayOf(argumentListType, rType)
  val candidates = ResolveUtil.getMethodCandidates(thisType, name, this, incomplete, *argTypes)
  if (argumentListType !is GrTupleType || candidates.any { it.isValidResult }) return candidates.toList()

  val unwrappedArgTypes = if (rType == null) argumentListType.componentTypes else argumentListType.componentTypes + rType
  return ResolveUtil.getMethodCandidates(thisType, name, this, incomplete, *unwrappedArgTypes).toList()
}
