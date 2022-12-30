// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.types

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.util.asSafely
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyConstantExpressionEvaluator
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.typing.GrCallTypeCalculator

class GrNamedRecordCallTypeCalculator : GrCallTypeCalculator {

  override fun getType(receiver: PsiType?, method: PsiMethod, arguments: Arguments?, context: PsiElement): PsiType? {
    val namedRecordClass =
      receiver.asSafely<PsiClassType>()?.resolve()?.asSafely<GrSyntheticNamedRecordClass>() ?: return null
    if (method.name != "get") {
      return null
    }
    val argument = arguments?.singleOrNull().asSafely<ExpressionArgument>() ?: return null
    val binding = when (val argumentValue = GroovyConstantExpressionEvaluator.evaluate(argument.expression)) {
      is Number -> namedRecordClass.exposedBindings.getOrNull(argumentValue.toString().toIntOrNull() ?: -1)
      is String -> argumentValue.takeIf { it in namedRecordClass.exposedBindings }
      else -> null
    } ?: return null
    return namedRecordClass.typeMap[binding]?.value
  }
}