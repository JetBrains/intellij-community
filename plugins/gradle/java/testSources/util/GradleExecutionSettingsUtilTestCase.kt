// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util

import com.intellij.execution.Location
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiImportList
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.JavaPsiTestCase

abstract class GradleExecutionSettingsUtilTestCase : JavaPsiTestCase() {

  fun assertClassTestFilter(expectedFilter: String, psiClass: PsiClass) {
    assertEquals(expectedFilter, createTestFilterFrom(psiClass))
  }

  fun assertMethodTestFilter(expectedFilter: String, psiMethod: PsiMethod) {
    assertEquals(expectedFilter, createTestFilterFrom(psiMethod))
  }

  fun assertTestFilter(expectedFilter: String, location: Location<*>?, psiClass: PsiClass?, psiMethod: PsiMethod?) {
    assertEquals(expectedFilter, createTestFilterFrom(location, psiClass, psiMethod))
  }

  fun requireResolvedJavaClass(qualifiedName: String) {
    val psiFile = createFile("resolve.java", "import $qualifiedName;")
    val psiImport = psiFile.findChildByType<PsiImportList>()
      .findChildrenByType<PsiImportStatement>()
      .find { qualifiedName == it.qualifiedName }
    requireNotNull(psiImport) {
      "The import for the ParameterizedTest annotation cannot be found"
    }
    val psiClass = psiImport.resolve()
    requireNotNull(psiClass) {
      "The $qualifiedName class cannot be resolved"
    }
    require(psiClass is PsiClass) {
      "The $qualifiedName class cannot be resolved as PsiClass ($psiClass)"
    }
    val resolvedQualifiedName = psiClass.qualifiedName
    require(qualifiedName == resolvedQualifiedName) {
      "The $qualifiedName class is resolved to incorrect class $resolvedQualifiedName"
    }
  }
}