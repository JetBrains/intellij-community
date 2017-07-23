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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyPolyVariantReference
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType
import org.jetbrains.plugins.groovy.lang.psi.util.getArgumentListType
import org.jetbrains.plugins.groovy.lang.psi.util.isClassLiteral
import org.jetbrains.plugins.groovy.lang.psi.util.isSimpleArrayAccess
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil

class GrIndexPropertyReference(element: GrIndexPropertyImpl, val rhs: Boolean)
  : PsiPolyVariantReferenceBase<GrIndexPropertyImpl>(element), GroovyPolyVariantReference {

  override fun getVariants() = emptyArray<Any>()

  override fun multiResolve(incompleteCode: Boolean): Array<GroovyResolveResult> {
    return TypeInferenceHelper.getCurrentContext().multiResolve(this, incompleteCode) { ref, incomplete ->
      ref.element?.doMultiResolve(rhs, incomplete) ?: GroovyResolveResult.EMPTY_ARRAY
    }
  }

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
}

private fun GrIndexProperty.doMultiResolve(rhs: Boolean, incomplete: Boolean): Array<GroovyResolveResult>? {
  if (isClassLiteral()) return null
  if (isSimpleArrayAccess()) return null

  val argumentListType = getArgumentListType() ?: return null
  val thisType = invokedExpression.type ?: PsiType.getJavaLangObject(manager, resolveScope)

  val rType = if (rhs) null else (parent as? GrAssignmentExpression)?.type
  if (rType == null && !rhs && !incomplete) return null

  val name = if (rhs) "getAt" else "putAt"

  val argTypes = if (rType == null) arrayOf(argumentListType) else arrayOf(argumentListType, rType)
  val candidates = ResolveUtil.getMethodCandidates(thisType, name, this, true, incomplete, *argTypes)
  if (argumentListType !is GrTupleType || candidates.any { it.isValidResult }) return candidates

  val unwrappedArgTypes = if (rType == null) argumentListType.componentTypes else argumentListType.componentTypes + rType
  return ResolveUtil.getMethodCandidates(thisType, name, this, true, incomplete, *unwrappedArgTypes)
}
