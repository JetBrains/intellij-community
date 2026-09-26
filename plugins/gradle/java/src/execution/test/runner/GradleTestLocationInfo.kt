// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface GradleTestLocationInfo {
  val sourceElement: PsiElement
  val testClass: PsiClass?
  val testMethod: PsiMethod?
  val testFilter: String?
    get() = null
}
