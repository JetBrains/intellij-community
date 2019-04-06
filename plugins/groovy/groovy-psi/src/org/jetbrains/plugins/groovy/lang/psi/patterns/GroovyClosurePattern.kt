// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.patterns

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.psi.PsiMethod
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall

class GroovyClosurePattern : GroovyExpressionPattern<GrClosableBlock, GroovyClosurePattern>(GrClosableBlock::class.java) {

  fun inMethod(methodPattern: ElementPattern<out PsiMethod>): GroovyClosurePattern {
    return with(object : PatternCondition<GrClosableBlock>("closureInMethod") {
      override fun accepts(closure: GrClosableBlock, context: ProcessingContext?): Boolean {
        val call = getCall(closure) ?: return false
        context?.put(closureCallKey, call)

        val method = call.resolveMethod() ?: return false
        return methodPattern.accepts(method)
      }
    })
  }

  fun inMethodResult(condition: PatternCondition<in GroovyMethodResult>): GroovyClosurePattern {
    return with(object : PatternCondition<GrClosableBlock>("closureInMethodResult") {
      override fun accepts(closure: GrClosableBlock, context: ProcessingContext?): Boolean {
        val call = getCall(closure) ?: return false
        context?.put(closureCallKey, call)

        val result = call.advancedResolve() as? GroovyMethodResult ?: return false
        return condition.accepts(result, context)
      }
    })
  }

  companion object {
    private fun getCall(closure: GrClosableBlock): GrCall? {
      val parent = closure.parent
      when (parent) {
        is GrCall -> {
          return if (closure in parent.closureArguments) parent else null
        }
        is GrArgumentList -> {
          val grandParent = parent.parent as? GrCall ?: return null
          if (grandParent.closureArguments.isNotEmpty()) return null
          if (grandParent.expressionArguments.lastOrNull() != closure) return null
          return grandParent
        }
        else -> return null
      }
    }
  }
}