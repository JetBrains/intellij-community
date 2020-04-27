// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.signatures

import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.util.containers.toArray
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter
import org.jetbrains.plugins.groovy.lang.resolve.api.CallSignature

class GrCallSignatureAdapter(private val callSignature: CallSignature<*>) : GrSignature {

  private val myParameters: Array<out GrClosureParameter> by lazyPub {
    callSignature.parameters.map {
      GrImmediateClosureParameterImpl(it.type, it.parameterName, it.isOptional, null)
    }.toArray(GrClosureParameter.EMPTY_ARRAY)
  }

  override fun getReturnType(): PsiType? = callSignature.returnType
  override fun getParameterCount(): Int = callSignature.parameterCount
  override fun getParameters(): Array<out GrClosureParameter> = myParameters
  override fun isVarargs(): Boolean = callSignature.isVararg
  override fun getSubstitutor(): PsiSubstitutor = PsiSubstitutor.EMPTY
  override fun isCurried(): Boolean = false
  override fun isValid(): Boolean = true
}
