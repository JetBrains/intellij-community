// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReference

fun GroovyMethodCallReference.resolveImpl(incomplete: Boolean): Collection<GroovyResolveResult> {
  val receiver = receiver ?: TypesUtil.getJavaLangObject(element)
  val methodName = methodName

  fun resolveWithArguments(args: Arguments?): Array<out GroovyResolveResult> {
    return if (args == null) {
      ResolveUtil.getMethodCandidates(receiver, methodName, element, incomplete)
    }
    else {
      ResolveUtil.getMethodCandidates(receiver, methodName, element, incomplete, *args.toTypedArray())
    }
  }

  val arguments = arguments
  val candidates = resolveWithArguments(arguments)
  val tupleType = arguments?.singleOrNull() as? GrTupleType
  if (tupleType == null || candidates.any { it.isValidResult }) {
    return candidates.toList()
  }
  return resolveWithArguments(tupleType.componentTypes).toList()
}
