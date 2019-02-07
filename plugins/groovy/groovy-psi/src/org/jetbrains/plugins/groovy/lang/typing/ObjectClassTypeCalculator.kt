// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.PsiWildcardType
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createJavaLangClassType
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments

class ObjectClassTypeCalculator : GrCallTypeCalculator {

  override fun getType(receiver: PsiType?, method: PsiMethod, arguments: Arguments?, context: PsiElement): PsiType? {
    if (receiver == null) return null
    if (method.name != "getClass" || method.containingClass?.qualifiedName != JAVA_LANG_OBJECT) return null
    return createJavaLangClassType(PsiWildcardType.createExtends(context.manager, TypeConversionUtil.erasure(receiver)), context)
  }
}
