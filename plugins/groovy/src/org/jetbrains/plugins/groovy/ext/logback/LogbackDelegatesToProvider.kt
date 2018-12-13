// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.logback

import groovy.lang.Closure
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
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

  override fun getDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? {
    if (appendClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(appenderDelegateFqn, closure), Closure.DELEGATE_FIRST)
    }
    if (receiverClosure.accepts(closure) || turboFilterClosure.accepts(closure)) {
      return DelegatesToInfo(TypesUtil.createType(componentDelegateFqn, closure), Closure.DELEGATE_FIRST)
    }
    return null
  }
}