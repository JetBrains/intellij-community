// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiTypes
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.testFramework.assertInstanceOf
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_ARTIFACT_HANDLER
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_CONFIGURABLE_PUBLISH_ARTIFACT
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest

class GradleArtifactsTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test closure delegate`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      testBuildscript("artifacts { <caret> }") {
        closureDelegateTest(GRADLE_API_ARTIFACT_HANDLER, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test member`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      testBuildscript("artifacts { <caret>add('conf', 'notation') }") {
        methodTest(resolveTest(PsiMethod::class.java), "add", GRADLE_API_ARTIFACT_HANDLER)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(DECORATORS)
  fun `test unresolved reference`(gradleVersion: GradleVersion, decorator: String) {
    testJavaProject(gradleVersion) {
      testBuildscript(decorator, "artifacts { <caret>foo }") {
        resolveTest<Nothing>(null)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test unresolved configuration reference`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      testBuildscript("artifacts { <caret>archives }") {
        resolveTest<Nothing>(null)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test invalid artifact addition`(gradleVersion: GradleVersion) {
    // foo configuration doesn't exist
    testJavaProject(gradleVersion) {
      testBuildscript("artifacts { <caret>foo('artifactNotation') }") {
        assertEmpty(elementUnderCaret(GrMethodCall::class.java).multiResolve(false))
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("""
    "artifacts { <caret>archives('artifactNotation') }",
    "artifacts { <caret>archives('artifactNotation', 'artifactNotation2', 'artifactNotation3') }",
    "artifacts.<caret>archives('artifactNotation')",
    "artifacts.<caret>archives('artifactNotation', 'artifactNotation2', 'artifactNotation3')"
  """)
  fun `test artifact addition`(gradleVersion: GradleVersion, expression: String) {
    testJavaProject(gradleVersion) {
      testBuildscript(expression) {
        val call = elementUnderCaret(GrMethodCall::class.java)
        val result = assertOneElement(call.multiResolve(false))
        val method = assertInstanceOf<PsiMethod>(result.element)
        methodTest(method, "archives", GRADLE_API_ARTIFACT_HANDLER)
        assertTrue(result.isApplicable)
        assertEquals(PsiTypes.nullType(), call.type)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("""
    "artifacts { <caret>archives('artifactNotation') {} }",
    "artifacts.<caret>archives('artifactNotation') {}"
  """)
  fun `test configurable artifact addition`(gradleVersion: GradleVersion, expression: String) {
    testJavaProject(gradleVersion) {
      testBuildscript(expression) {
        val call = elementUnderCaret(GrMethodCall::class.java)
        val result = assertOneElement(call.multiResolve(false))
        val method = assertInstanceOf<PsiMethod>(result.element)
        methodTest(method, "archives", GRADLE_API_ARTIFACT_HANDLER)
        assertTrue(result.isApplicable)
        assertTrue(call.type!!.equalsToText(GRADLE_API_CONFIGURABLE_PUBLISH_ARTIFACT))
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test configuration delegate`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      testBuildscript("artifacts { archives('artifactNotation') { <caret> } }") {
        closureDelegateTest(GRADLE_API_CONFIGURABLE_PUBLISH_ARTIFACT, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test configuration delegate method setter`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      testBuildscript("artifacts { archives('artifactNotation') { <caret>name('hi') } }") {
        setterMethodTest("name", "setName", GRADLE_API_CONFIGURABLE_PUBLISH_ARTIFACT)
      }
    }
  }
}