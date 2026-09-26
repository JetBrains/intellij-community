// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.siyeh.ig.junit.JUnitCommonClassNames

class JUnitParameterizedMethodSourceGotoRelatedProvider : JUnitParameterizedSourceGotoRelatedProvider<PsiMethod>() {
  override val annotationClassName: String get() = JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE
  override fun getPsiElementByName(directClass: PsiClass, name: String): PsiMethod? = directClass.findMethodsByName(name, false)
    .singleOrNull { it.parameters.isEmpty() }
}