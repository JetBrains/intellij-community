// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.util.getArgumentListType
import org.jetbrains.plugins.groovy.lang.psi.util.isClassLiteral
import org.jetbrains.plugins.groovy.lang.psi.util.isSimpleArrayAccess
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReferenceBase

abstract class GrIndexPropertyReference(element: GrIndexProperty) : GroovyMethodCallReferenceBase<GrIndexProperty>(element) {

  /**
   * Consider expression `foo[a, b, c]`.
   * Its argument list is `[a, b, c]`.
   * - rValue reference, i.e. reference to a getAt() method, will have range of `[`.
   * - lValue reference, i.e. reference to a putAt() method, will have range of `]`.
   */
  abstract override fun getRangeInElement(): TextRange

  final override val isRealReference: Boolean get() = element.run { !isClassLiteral() && !isSimpleArrayAccess() }

  final override val receiver: PsiType? get() = element.invokedExpression.type
}

class GrGetAtReference(element: GrIndexProperty) : GrIndexPropertyReference(element) {

  override fun getRangeInElement(): TextRange = TextRange.from(element.argumentList.startOffsetInParent, 1)

  override val methodName: String get() = "getAt"

  override val arguments: Arguments get() = listOf(element.getArgumentListType())
}

class GrPutAtReference(element: GrIndexProperty) : GrIndexPropertyReference(element) {

  override fun getRangeInElement(): TextRange {
    val argumentList = element.argumentList
    return TextRange.from(argumentList.startOffsetInParent + argumentList.textLength - 1, 1)
  }

  override val methodName: String get() = "putAt"

  override val arguments: Arguments
    get() {
      val listArgument = element.getArgumentListType()
      val rValue = (element.parent as? GrAssignmentExpression)?.type
      return listOf(listArgument, rValue)
    }
}
