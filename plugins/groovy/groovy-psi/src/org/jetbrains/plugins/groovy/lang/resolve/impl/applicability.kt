// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument

fun mapApplicability(map: Map<Argument, PsiParameter>, erasureSubstitutor: PsiSubstitutor, context: PsiElement): Applicability {
  for ((argument, parameter) in map) {
    val argumentAssignability = argumentApplicability(argument, parameter, erasureSubstitutor, context)
    if (argumentAssignability != Applicability.applicable) {
      return argumentAssignability
    }
  }
  return Applicability.applicable
}

fun argumentApplicability(argument: Argument,
                          parameter: PsiParameter,
                          erasureSubstitutor: PsiSubstitutor,
                          context: PsiElement): Applicability {
  return argumentApplicability(argument, context) {
    TypeConversionUtil.erasure(parameter.type, erasureSubstitutor)
  }
}

fun argumentApplicability(argument: Argument, context: PsiElement, parameterTypeComputable: () -> PsiType?): Applicability {
  val argumentType = argument.runtimeType
  if (argumentType == null) {
    // argument passed but we cannot infer its type
    return Applicability.canBeApplicable
  }

  val parameterType = parameterTypeComputable()
  if (parameterType == null) {
    return Applicability.canBeApplicable
  }

  val assignability = TypesUtil.canAssign(parameterType, argumentType, context, GrTypeConverter.ApplicableTo.METHOD_PARAMETER)
  if (assignability == ConversionResult.ERROR) {
    return Applicability.inapplicable
  }

  return Applicability.applicable
}
