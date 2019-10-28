// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.references

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.util.getArgumentListArgument
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReferenceBase
import org.jetbrains.plugins.groovy.lang.resolve.impl.resolveWithArguments
import org.jetbrains.plugins.groovy.lang.typing.ListLiteralType

abstract class GrIndexPropertyReference(element: GrIndexProperty) : GroovyMethodCallReferenceBase<GrIndexProperty>(element) {

  /**
   * Consider expression `foo[a, b, c]`.
   * Its argument list is `[a, b, c]`.
   * - rValue reference, i.e. reference to a getAt() method, will have range of `[`.
   * - lValue reference, i.e. reference to a putAt() method, will have range of `]`.
   */
  abstract override fun getRangeInElement(): TextRange

  final override val receiverArgument: Argument get() = ExpressionArgument(element.invokedExpression)

  override fun doResolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    val place = element

    val receiver = receiverArgument
    val methodName = methodName

    val arguments: Arguments? = if (incomplete) null else arguments
    val candidates = resolveWithArguments(receiver, methodName, arguments, place)
    val singleType = arguments?.singleOrNull()?.type as? ListLiteralType
    if (singleType == null || candidates.any { it.isValidResult }) {
      return candidates
    }
    return resolveWithArguments(receiver, methodName, singleType.expressions.map(::ExpressionArgument), place)
  }
}

class GrGetAtReference(element: GrIndexProperty) : GrIndexPropertyReference(element) {

  override fun getRangeInElement(): TextRange = TextRange.from(element.argumentList.startOffsetInParent, 1)

  override val methodName: String get() = "getAt"

  override val arguments: Arguments get() = listOf(element.getArgumentListArgument())
}

class GrPutAtReference(element: GrIndexProperty, private val rValue: Argument) : GrIndexPropertyReference(element) {

  override fun getRangeInElement(): TextRange {
    val argumentList = element.argumentList
    return TextRange.from(argumentList.startOffsetInParent + argumentList.textLength - 1, 1)
  }

  override val methodName: String get() = "putAt"

  override val arguments: Arguments get() = listOf(element.getArgumentListArgument(), rValue)
}
