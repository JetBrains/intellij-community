// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type.highlighting

import com.intellij.psi.PsiElement
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.groovy.highlighting.HighlightSink
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCallReference

class GrNewExpressionHighlighter(val newExpression: GrNewExpression,
                                 reference: GroovyCallReference,
                                 sink: HighlightSink) : ConstructorCallHighlighter(reference, sink) {

  override fun getArgumentList(): GrArgumentList? = newExpression.argumentList

  override fun getHighlightElement(): PsiElement {
    val element = getArgumentList() ?: newExpression.referenceElement
    if (element != null) return element
    throw IncorrectOperationException("reference of new expression should exist if it is a constructor call")
  }
}
