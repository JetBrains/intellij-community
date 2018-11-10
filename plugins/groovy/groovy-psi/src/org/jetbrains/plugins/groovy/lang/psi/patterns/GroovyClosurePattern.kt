/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.patterns

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.psi.PsiMethod
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall

class GroovyClosurePattern : GroovyExpressionPattern<GrClosableBlock, GroovyClosurePattern>(GrClosableBlock::class.java) {

  fun inMethod(methodPattern: ElementPattern<out PsiMethod>): GroovyClosurePattern = with(object : PatternCondition<GrClosableBlock>("closureInMethod") {
    override fun accepts(closure: GrClosableBlock, context: ProcessingContext?): Boolean {
      val parent = closure.parent
      val call = when (parent) {
        is GrCall -> {
          if (closure !in parent.closureArguments) return false
          parent
        }
        is GrArgumentList -> {
          val grandParent = parent.parent as? GrCall ?: return false
          if (grandParent.closureArguments.isNotEmpty()) return false
          if (grandParent.expressionArguments.lastOrNull() != closure) return false
          grandParent
        }
        else -> return false
      }
      context?.put(closureCallKey, call)

      val method = call.resolveMethod() ?: return false
      return methodPattern.accepts(method)
    }
  })
}