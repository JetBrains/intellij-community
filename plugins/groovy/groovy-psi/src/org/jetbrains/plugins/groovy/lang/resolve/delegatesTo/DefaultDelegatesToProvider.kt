// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.delegatesTo

import com.intellij.psi.PsiMethod
import groovy.lang.Closure.*
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument

class DefaultDelegatesToProvider : GrDelegatesToProvider {

  override fun getDelegatesToInfo(expression: GrFunctionalExpression): DelegatesToInfo? {
    val call = getContainingCall(expression) ?: return null
    val result = call.advancedResolve()
    val method = result.element as? PsiMethod ?: return null

    if (GdkMethodUtil.isWithOrIdentity(method)) {
      val qualifier = inferCallQualifier(call as GrMethodCall) ?: return null
      return DelegatesToInfo(qualifier.type, DELEGATE_FIRST)
    }

    val argumentMapping = (result as? GroovyMethodResult)?.candidate?.argumentMapping ?: return null

    val parameter = argumentMapping.targetParameter(ExpressionArgument(expression)) ?: return null

    parameter.getUserData(DELEGATES_TO_KEY)?.let {
      return it
    }

    val delegateFqnData = parameter.getUserData(DELEGATES_TO_TYPE_KEY)
    val strategyData = parameter.getUserData(DELEGATES_TO_STRATEGY_KEY)
    if (delegateFqnData != null) {
      return DelegatesToInfo(
        TypesUtil.createType(delegateFqnData, expression),
        strategyData ?: OWNER_FIRST
      )
    }

    val modifierList = parameter.modifierList ?: return null
    val delegatesTo = modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_LANG_DELEGATES_TO) ?: return null
    val strategyValue = getStrategyValue(delegatesTo.findAttributeValue("strategy"))
    val delegateType = if (strategyValue == OWNER_ONLY || strategyValue == TO_SELF) {
      null
    }
    else {
      getFromValue(delegatesTo)
      ?: getFromTarget(method.parameterList, delegatesTo, argumentMapping)
      ?: getFromType(call, result, delegatesTo)
    }
    return DelegatesToInfo(delegateType, strategyValue)
  }


  private fun inferCallQualifier(call: GrMethodCall): GrExpression? {
    val expression = call.invokedExpression
    return if (expression !is GrReferenceExpression) null else expression.qualifier
  }

}
