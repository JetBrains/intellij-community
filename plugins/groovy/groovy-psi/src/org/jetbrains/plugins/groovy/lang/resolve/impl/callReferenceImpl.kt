// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.resolveKinds
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReference
import org.jetbrains.plugins.groovy.lang.resolve.processReceiverType
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyRValueProcessor
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodProcessor

fun GroovyMethodCallReference.resolveImpl(incomplete: Boolean): Collection<GroovyResolveResult> {
  val receiver = receiver ?: TypesUtil.getJavaLangObject(element)
  val methodName = methodName

  fun resolveWithArguments(args: List<PsiType?>?): Array<out GroovyResolveResult> {
    return if (args == null) {
      ResolveUtil.getMethodCandidates(receiver, methodName, element, incomplete)
    }
    else {
      ResolveUtil.getMethodCandidates(receiver, methodName, element, incomplete, *args.toTypedArray())
    }
  }

  val arguments = arguments?.map { it?.type }
  val candidates = resolveWithArguments(arguments)
  val tupleType = arguments?.singleOrNull() as? GrTupleType
  if (tupleType == null || candidates.any { it.isValidResult }) {
    return candidates.toList()
  }
  return resolveWithArguments(tupleType.componentTypes).toList()
}

fun GroovyMethodCallReference.resolveImpl2(incomplete: Boolean): Collection<GroovyResolveResult> {
  val place = element

  val receiver = receiver ?: return emptyList()
  val state = ResolveState.initial()

  val methodProcessor = MethodProcessor(methodName, place, arguments, PsiType.EMPTY_ARRAY)
  receiver.processReceiverType(methodProcessor, state, place)
  methodProcessor.applicableCandidates?.let {
    return it
  }

  val propertyProcessor = GroovyRValueProcessor(methodName, place, resolveKinds(true))
  receiver.processReceiverType(propertyProcessor, state, place)
  val properties = propertyProcessor.results
  if (properties.size == 1) {
    return properties
  }

  val methods = filterBySignature(filterByArgumentsCount(methodProcessor.allCandidates, arguments))
  return methods + properties
}
