// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.psi.PsiMethod
import com.intellij.testFramework.assertInstanceOf
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_PROJECT
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest

class GradleProjectTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource(DECORATORS)
  fun `test resolve explicit getter`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "<caret>getGroup()") {
        val results = elementUnderCaret(GrMethodCall::class.java).multiResolve(false)
        assertEquals(1, results.size)
        val method = assertInstanceOf<PsiMethod>(results[0].element)
        assertEquals("getGroup", method.name)
        assertEquals(GRADLE_API_PROJECT, method.containingClass!!.qualifiedName)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(DECORATORS)
  fun `test resolve property`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "<caret>group") {
        val results = elementUnderCaret(GrReferenceExpression::class.java).multiResolve(false)
        assertEquals(1, results.size)
        val method = assertInstanceOf<PsiMethod>(results[0].element)
        assertEquals("getGroup", method.name)
        assertEquals(GRADLE_API_PROJECT, method.containingClass!!.qualifiedName)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(DECORATORS)
  fun `test resolve explicit setter`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "<caret>setGroup(1)") {
        val results = elementUnderCaret(GrMethodCall::class.java).multiResolve(false)
        assertEquals(1, results.size)
        val method = assertInstanceOf<PsiMethod>(results[0].element)
        assertEquals("setGroup", method.name)
        assertEquals(GRADLE_API_PROJECT, method.containingClass!!.qualifiedName)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(DECORATORS)
  fun `test resolve explicit setter without argument`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "<caret>setGroup()") {
        val results = elementUnderCaret(GrMethodCall::class.java).multiResolve(false)
        assertEquals(1, results.size)
        val method = assertInstanceOf<PsiMethod>(results[0].element)
        assertEquals("setGroup", method.name)
        assertEquals(GRADLE_API_PROJECT, method.containingClass!!.qualifiedName)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(DECORATORS)
  fun `test resolve property setter`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "<caret>group = 42") {
        val results = elementUnderCaret(GrReferenceExpression::class.java).multiResolve(false)
        assertEquals(1, results.size)
        val method = assertInstanceOf<PsiMethod>(results[0].element)
        assertEquals("setGroup", method.name)
        assertEquals(GRADLE_API_PROJECT, method.containingClass!!.qualifiedName)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(DECORATORS)
  fun `test resolve implicit setter`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "<caret>group(42)") {
        setterMethodTest("group", "setGroup", GRADLE_API_PROJECT)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(DECORATORS)
  fun `test resolve implicit setter without argument`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "<caret>group()") {
        setterMethodTest("group", "setGroup", GRADLE_API_PROJECT)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(DECORATORS)
  fun `test property vs task`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "<caret>dependencies") {
        methodTest(resolveTest(PsiMethod::class.java), "getDependencies", GRADLE_API_PROJECT)
      }
    }
  }
}