// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createType
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.DEFAULT_GROOVY_METHODS
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments

class DgmIntdivCallTypeCalculator : GrCallTypeCalculator {

  companion object {
    @NlsSafe
    private const val INTDIV = "intdiv"
  }

  override fun getType(receiver: PsiType?, method: PsiMethod, arguments: Arguments?, context: PsiElement): PsiType? {
    if (method.name == INTDIV && method.containingClass?.qualifiedName == DEFAULT_GROOVY_METHODS) {
      return createType(JAVA_LANG_INTEGER, context)
    }
    return null
  }
}
