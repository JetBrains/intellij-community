// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_CONFIGURATION
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_CONFIGURATION_CONTAINER
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest

class GradleConfigurationsTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource("$DECORATORS, buildscript")
  fun `test configurations closure delegate`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "configurations { <caret> }") {
        closureDelegateTest(GRADLE_API_CONFIGURATION_CONTAINER, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$DECORATORS, buildscript")
  fun `test configuration via unqualified property reference`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "configurations { <caret>foo }") {
        val ref = elementUnderCaret(GrReferenceExpression::class.java)
        assertNotNull(ref.resolve())
        assertTrue(ref.type!!.equalsToText(GRADLE_API_CONFIGURATION))
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$DECORATORS, buildscript")
  fun `test configuration via unqualified method call`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "configurations { <caret>foo {} }") {
        val call = elementUnderCaret(GrMethodCall::class.java)
        assertNotNull(call.resolveMethod())
        assertTrue(call.type!!.equalsToText(GRADLE_API_CONFIGURATION))
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$DECORATORS, buildscript")
  fun `test configuration closure delegate in unqualified method call`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "configurations { foo { <caret> } }") {
        closureDelegateTest(GRADLE_API_CONFIGURATION, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$DECORATORS, buildscript")
  fun `test configuration member via unqualified method call closure delegate`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "configurations { foo { <caret>extendsFrom() } }") {
        val call = elementUnderCaret(GrMethodCall::class.java)
        val method = requireNotNull(call.resolveMethod())
        assertEquals(GRADLE_API_CONFIGURATION, method.containingClass!!.qualifiedName)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$DECORATORS, buildscript")
  fun `test configuration via qualified property reference`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "configurations { foo }; configurations.<caret>foo") {
        val ref = elementUnderCaret(GrReferenceExpression::class.java)
        assertNotNull(ref.resolve())
        assertTrue(ref.type!!.equalsToText(GRADLE_API_CONFIGURATION))
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$DECORATORS, buildscript")
  fun `test configuration via qualified method call`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "configurations { foo }; configurations.<caret>foo {}") {
        val call = elementUnderCaret(GrMethodCall::class.java)
        assertNotNull(call.resolveMethod())
        assertTrue(call.type!!.equalsToText(GRADLE_API_CONFIGURATION))
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$DECORATORS, buildscript")
  fun `test configuration closure delegate in qualified method call`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "configurations { foo }; configurations.foo { <caret> }") {
        closureDelegateTest(GRADLE_API_CONFIGURATION, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$DECORATORS, buildscript")
  fun `test configuration member via qualified method call closure delegate`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "configurations { foo }; configurations.foo { <caret>extendsFrom() }") {
        val call = elementUnderCaret(GrMethodCall::class.java)
        val method = requireNotNull(call.resolveMethod())
        assertEquals(GRADLE_API_CONFIGURATION, method.containingClass!!.qualifiedName)
      }
    }
  }
}
