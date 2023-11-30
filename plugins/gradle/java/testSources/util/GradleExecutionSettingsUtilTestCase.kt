// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.JavaPsiTestCase

abstract class GradleExecutionSettingsUtilTestCase : JavaPsiTestCase() {

  fun assertClassTestFilter(expectedFilter: String, psiClass: PsiClass) {
    assertEquals(expectedFilter, createTestFilterFrom(psiClass))
  }

  fun assertMethodTestFilter(expectedFilter: String, psiMethod: PsiMethod) {
    assertEquals(expectedFilter, createTestFilterFrom(psiMethod))
  }
}