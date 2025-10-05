// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_SOURCE_SET
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_SOURCE_SET_CONTAINER
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest

class GradleSourceSetsTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test sourceSets closure delegate`(gradleVersion: GradleVersion, decorator: String) {
    testJavaProject(gradleVersion) {
      testBuildscript(decorator, "sourceSets { <caret> }") {
        closureDelegateTest(GRADLE_API_SOURCE_SET_CONTAINER, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test source set via unqualified property reference`(gradleVersion: GradleVersion, decorator: String) {
    testJavaProject(gradleVersion) {
      testBuildscript(decorator, "sourceSets { <caret>main }") {
        val ref = elementUnderCaret(GrReferenceExpression::class.java)
        assertNotNull(ref.resolve())
        assertTrue(ref.type!!.equalsToText(GRADLE_API_SOURCE_SET))
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test source set via unqualified method call`(gradleVersion: GradleVersion, decorator: String) {
    testJavaProject(gradleVersion) {
      testBuildscript(decorator, "sourceSets { <caret>main {} }") {
        val call = elementUnderCaret(GrMethodCall::class.java)
        assertNotNull(call.resolveMethod())
        assertTrue(call.type!!.equalsToText(GRADLE_API_SOURCE_SET))
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test source set closure delegate in unqualified method call`(gradleVersion: GradleVersion, decorator: String) {
    testJavaProject(gradleVersion) {
      testBuildscript(decorator, "sourceSets { main { <caret> } }") {
        closureDelegateTest(GRADLE_API_SOURCE_SET, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test source set member via unqualified method call closure delegate`(gradleVersion: GradleVersion, decorator: String) {
    testJavaProject(gradleVersion) {
      testBuildscript(decorator, "sourceSets { main { <caret>getJarTaskName() } }") {
        val call = elementUnderCaret(GrMethodCall::class.java)
        val method = requireNotNull(call.resolveMethod())
        assertEquals(GRADLE_API_SOURCE_SET, method.containingClass!!.qualifiedName)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test source set via qualified property reference`(gradleVersion: GradleVersion, decorator: String) {
    testJavaProject(gradleVersion) {
      testBuildscript(decorator, "sourceSets.<caret>main") {
        val ref = elementUnderCaret(GrReferenceExpression::class.java)
        assertNotNull(ref.resolve())
        assertTrue(ref.type!!.equalsToText(GRADLE_API_SOURCE_SET))
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test source set via qualified method call`(gradleVersion: GradleVersion, decorator: String) {
    testJavaProject(gradleVersion) {
      testBuildscript(decorator, "sourceSets.<caret>main {}") {
        val call = elementUnderCaret(GrMethodCall::class.java)
        assertNotNull(call.resolveMethod())
        assertTrue(call.type!!.equalsToText(GRADLE_API_SOURCE_SET))
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test source set closure delegate in qualified method call`(gradleVersion: GradleVersion, decorator: String) {
    testJavaProject(gradleVersion) {
      testBuildscript(decorator, "sourceSets.main { <caret> }") {
        closureDelegateTest(GRADLE_API_SOURCE_SET, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test source set member via qualified method call closure delegate`(gradleVersion: GradleVersion, decorator: String) {
    testJavaProject(gradleVersion) {
      testBuildscript(decorator, "sourceSets.main { <caret>getJarTaskName() }") {
        val call = elementUnderCaret(GrMethodCall::class.java)
        val method = requireNotNull(call.resolveMethod())
        assertEquals(GRADLE_API_SOURCE_SET, method.containingClass!!.qualifiedName)
      }
    }
  }
}
