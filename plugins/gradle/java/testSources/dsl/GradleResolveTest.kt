// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.psi.CommonClassNames.JAVA_UTIL_DATE
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.assertInstanceOf
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest

class GradleResolveTest: GradleCodeInsightTestCase() {

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test resolve date constructor`(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      testBuildscript("<caret>new Date()") {
        val expression = elementUnderCaret(GrNewExpression::class.java)
        val results = expression.multiResolve(false)
        assertEquals(1, results.size)
        val method = assertInstanceOf<PsiMethod>(results[0].element)
        assertTrue(method.isConstructor)
        assertEquals(JAVA_UTIL_DATE, method.containingClass!!.qualifiedName)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test resolve date constructor 2`(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      testBuildscript("<caret>new Date(1l)") {
        val expression = elementUnderCaret(GrNewExpression::class.java)
        val results = expression.multiResolve(false)
        assertEquals(1, results.size)
        val method = assertInstanceOf<PsiMethod>(results[0].element)
        assertTrue(method.isConstructor)
        assertEquals(JAVA_UTIL_DATE, method.containingClass!!.qualifiedName)
      }
    }
  }
}