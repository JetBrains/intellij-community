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

fun mapApplicability(map: Map<Argument, PsiParameter>, substitutor: PsiSubstitutor, erase: Boolean, context: PsiElement): Applicability {
  for ((argument, parameter) in map) {
    val parameterType = parameterType(parameter.type, substitutor, erase)
    val applicability = argumentApplicability(parameterType, argument, context)
    if (applicability != Applicability.applicable) {
      return applicability
    }
  }
  return Applicability.applicable
}

fun argumentApplicability(parameterType: PsiType?, argument: Argument, context: PsiElement): Applicability {
  if (parameterType == null) {
    return Applicability.canBeApplicable
  }

  val argumentType = argument.runtimeType
  if (argumentType == null) {
    // argument passed but we cannot infer its type
    return Applicability.canBeApplicable
  }

  val assignability = TypesUtil.canAssign(parameterType, argumentType, context, GrTypeConverter.ApplicableTo.METHOD_PARAMETER)
  if (assignability == ConversionResult.ERROR) {
    return Applicability.inapplicable
  }

  return Applicability.applicable
}

fun parameterType(type: PsiType, substitutor: PsiSubstitutor, erase: Boolean): PsiType? {
  return if (erase) {
    TypeConversionUtil.erasure(type, substitutor)
  }
  else {
    substitutor.substitute(type)
  }
}
