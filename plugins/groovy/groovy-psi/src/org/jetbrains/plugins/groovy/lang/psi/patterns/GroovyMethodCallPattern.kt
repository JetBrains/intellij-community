// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.patterns

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiMethod
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression

object GroovyMethodCallPattern : GroovyExpressionPattern<GrCallExpression, GroovyMethodCallPattern>(GrCallExpression::class.java) {

  fun withArguments(vararg arguments: ElementPattern<out GrExpression>): GroovyMethodCallPattern =
    with(object : PatternCondition<GrCallExpression>("withArguments") {
      override fun accepts(callExpression: GrCallExpression, context: ProcessingContext): Boolean {
        val argumentList = callExpression.argumentList ?: return false
        val actualArguments = argumentList.expressionArguments
        if (arguments.size != actualArguments.size) {
          return false
        }
        for ((argument, argumentPattern) in actualArguments.zip(arguments)) {
          if (!argumentPattern.accepts(argument, context)) {
            return false
          }
        }
        return true
      }
    })

  fun withMethodName(methodName: String): GroovyMethodCallPattern = withMethodName(StandardPatterns.string().equalTo(methodName))

  fun withMethodName(methodName: ElementPattern<out String>): GroovyMethodCallPattern {
    return with(object : PatternCondition<GrCallExpression>("withMethodName") {
      override fun accepts(callExpression: GrCallExpression, context: ProcessingContext): Boolean {
        if (callExpression !is GrMethodCall) return false
        val expression = callExpression.invokedExpression as? GrReferenceExpression ?: return false
        return methodName.accepts(expression.referenceName, context)
      }
    })
  }

  fun withMethod(methodPattern: ElementPattern<out PsiMethod>): GroovyMethodCallPattern {
    return with(object : PatternCondition<GrCallExpression>("methodCall") {
      override fun accepts(callExpression: GrCallExpression, context: ProcessingContext): Boolean {
        for (result in callExpression.getCallVariants(null, false)) {
          if (methodPattern.accepts(result.element, context)) {
            return true
          }
        }
        return false
      }
    })
  }

  fun resolvesTo(methodPattern: ElementPattern<out PsiMethod>): GroovyMethodCallPattern {
    return with(object : PatternCondition<GrCallExpression>("resolvesTo") {
      override fun accepts(t: GrCallExpression, context: ProcessingContext?): Boolean {
        val result = t.advancedResolve().element as? PsiMethod ?: return false
        return methodPattern.accepts(result, context)
      }
    })
  }
}
