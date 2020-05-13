// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.signatures

import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.util.MethodSignature
import com.intellij.psi.util.MethodSignatureUtil.createMethodSignature
import com.intellij.util.SmartList
import org.jetbrains.plugins.groovy.lang.resolve.api.CallParameter
import org.jetbrains.plugins.groovy.lang.resolve.api.CallSignature
import java.util.*

fun generateAllMethodSignaturesBySignature(name: String, signatures: List<CallSignature<*>>): List<MethodSignature> {
  return signatures.flatMap {
    generateAllMethodSignaturesByClosureSignature(name, it)
  }
}

private fun generateAllMethodSignaturesByClosureSignature(name: String, signature: CallSignature<*>): List<MethodSignature> {
  val result = SmartList<MethodSignature>()
  val params = signature.parameters
  val newParams = ArrayList<PsiType?>(params.size)
  val opts = ArrayList<CallParameter>(params.size)
  val optInds = ArrayList<Int>(params.size)
  for (i in params.indices) {
    if (params[i].isOptional) {
      opts.add(params[i])
      optInds.add(i)
    }
    else {
      newParams.add(params[i].type)
    }
  }
  result.add(createMethodSignature(name, newParams))
  for (i in opts.indices) {
    newParams.add(optInds[i], opts[i].type)
    result.add(createMethodSignature(name, newParams))
  }
  return result
}

private fun createMethodSignature(name: String, newParams: ArrayList<PsiType?>): MethodSignature {
  return createMethodSignature(name, newParams.toArray(PsiType.EMPTY_ARRAY), PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY)
}
