// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parameterInfo

import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature
import org.jetbrains.plugins.groovy.lang.typing.FunctionalSignature

internal class GrSignatureWrapper {
  val isVararg: Boolean
  val parameterList: List<GrParameterWrapper>

  constructor(signature: GrSignature) {
    isVararg = signature.isVarargs
    parameterList = signature.parameters.map { it ->
      GrParameterWrapper(
        it.name,
        it.type,
        it.defaultInitializer
      )
    }.toList()
  }

  constructor(signature: FunctionalSignature) {
    isVararg = signature.isVararg
    parameterList = signature.parameters.map {
      GrParameterWrapper(
        it.parameterName,
        it.type
      )
    }
  }
}