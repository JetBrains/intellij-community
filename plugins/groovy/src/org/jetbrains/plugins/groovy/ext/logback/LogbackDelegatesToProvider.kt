// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.logback

import groovy.lang.Closure
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.patterns.groovyClosure
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.GrDelegatesToProvider

class LogbackDelegatesToProvider : GrDelegatesToProvider {

  private companion object {
    const val appenderDelegateFqn = "ch.qos.logback.classic.gaffer.AppenderDelegate"
    val appendClosure = groovyClosure().inMethod(appenderMethodPattern)
    val receiverClosure = groovyClosure().inMethod(psiMethod(configDelegateFqn, "receiver"))
    val turboFilterClosure = groovyClosure().inMethod(psiMethod(configDelegateFqn, "turboFilter"))
  }

  override fun getDelegatesToInfo(expression: GrFunctionalExpression): DelegatesToInfo? {
    if (appendClosure.accepts(expression)) {
      return DelegatesToInfo(TypesUtil.createType(appenderDelegateFqn, expression), Closure.DELEGATE_FIRST)
    }
    if (receiverClosure.accepts(expression) || turboFilterClosure.accepts(expression)) {
      return DelegatesToInfo(TypesUtil.createType(componentDelegateFqn, expression), Closure.DELEGATE_FIRST)
    }
    return null
  }
}