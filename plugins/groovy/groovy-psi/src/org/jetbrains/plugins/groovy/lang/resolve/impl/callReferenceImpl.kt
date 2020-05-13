// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.rValueProcessor
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.resolveKinds
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.processReceiver
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodProcessor

fun resolveWithArguments(receiver: Argument,
                         methodName: String,
                         arguments: Arguments?,
                         place: PsiElement): List<GroovyResolveResult> {
  val state = ResolveState.initial()
  val methodProcessor = MethodProcessor(methodName, place, arguments, PsiType.EMPTY_ARRAY)
  receiver.processReceiver(methodProcessor, state, place)
  methodProcessor.applicableCandidates?.let {
    return it
  }

  val propertyProcessor = rValueProcessor(methodName, place, resolveKinds(true))
  receiver.processReceiver(propertyProcessor, state, place)
  val properties = propertyProcessor.results
  if (properties.size == 1) {
    return properties
  }

  val methods = filterBySignature(filterByArgumentsCount(methodProcessor.allCandidates, arguments))
  return methods + properties
}
