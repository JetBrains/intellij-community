// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.patterns

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.psi.PsiMethod
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall

class GroovyMethodCallPattern : GroovyExpressionPattern<GrMethodCall, GroovyMethodCallPattern>(GrMethodCall::class.java) {

  fun resolvesTo(methodPattern: ElementPattern<out PsiMethod>): GroovyMethodCallPattern {
    return with(object : PatternCondition<GrMethodCall>("resolves to method") {
      override fun accepts(t: GrMethodCall, context: ProcessingContext?): Boolean {
        val result = t.advancedResolve().element as? PsiMethod ?: return false
        return methodPattern.accepts(result, context)
      }
    })
  }
}
