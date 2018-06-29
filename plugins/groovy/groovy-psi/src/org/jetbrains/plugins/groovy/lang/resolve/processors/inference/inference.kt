// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil

fun getTopLevelType(expression: GrExpression): PsiType? {
  if (expression is GrMethodCall) {
    val resolved = expression.advancedResolve()
    (resolved as? GroovyMethodResult)?.candidate?.let {
      val session = GroovyInferenceSessionBuilder(expression.invokedExpression as GrReferenceExpression, it)
        .resolveMode(false)
        .build()

      return session.inferSubst().substitute(PsiUtil.getSmartReturnType(it.method))
    }
  }

  if (expression is GrClosableBlock) {
    return TypesUtil.createTypeByFQClassName(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, expression)
  }

  return expression.type
}