// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil.isAssignableByConversion
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability
import org.jetbrains.plugins.groovy.lang.resolve.api.ApplicabilityResult.ArgumentApplicability
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.CallParameter

fun mapApplicability(map: Map<Argument, CallParameter>, context: PsiElement): Applicability {
  var result = Applicability.applicable
  for ((argument, parameter) in map) {
    val parameterType = TypeConversionUtil.erasure(parameter.type)
    val argumentType = argument.runtimeType
    when (val applicability = argumentApplicability(parameterType, argumentType, context)) {
      Applicability.inapplicable -> return applicability
      Applicability.canBeApplicable -> result = Applicability.canBeApplicable
      Applicability.applicable -> Unit
    }
  }
  return result
}

fun highlightApplicabilities(map: Map<Argument, CallParameter>,
                             substitutor: PsiSubstitutor,
                             context: PsiElement): Map<Argument, ArgumentApplicability> {
  return map.entries.associate { (argument, parameter) ->
    val parameterType = parameterType(parameter.type, substitutor, false)
    val applicability = argumentApplicability(parameterType, argument.type, context)
    return@associate argument to ArgumentApplicability(parameterType, applicability)
  }
}

fun argumentApplicability(parameterType: PsiType?, argumentType: PsiType?, context: PsiElement): Applicability {
  if (parameterType == null) {
    return Applicability.canBeApplicable
  }

  if (parameterType is PsiClassType && parameterType.resolve()?.qualifiedName == JAVA_LANG_OBJECT) {
    return Applicability.applicable
  }

  if (argumentType == null) {
    // argument passed but we cannot infer its type
    return Applicability.canBeApplicable
  }

  return if (isAssignableByConversion(parameterType, argumentType, context)) {
    Applicability.applicable
  }
  else {
    Applicability.inapplicable
  }
}

fun parameterType(type: PsiType?, substitutor: PsiSubstitutor, erase: Boolean): PsiType? {
  return if (erase) {
    TypeConversionUtil.erasure(type, substitutor)
  }
  else {
    substitutor.substitute(type)
  }
}
