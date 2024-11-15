// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER
import com.intellij.testFramework.assertInstanceOf
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.GradleGroovyProperty
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyProperty
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest

class GradleExtensionsTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test project level extension property`(gradleVersion: GradleVersion) {
    test(gradleVersion, SIMPLE_FIXTURE_BUILDER) {
      testBuildscript("<caret>ext") {
        val ref = elementUnderCaret(GrReferenceExpression::class.java)
        assertInstanceOf<GroovyProperty>(ref.resolve())
        assertTrue(ref.type!!.equalsToText(getExtraPropertiesExtensionFqn()))
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test project level extension call type`(gradleVersion: GradleVersion) {
    test(gradleVersion, SIMPLE_FIXTURE_BUILDER) {
      testBuildscript("<caret>ext {}") {
        val call = elementUnderCaret(GrMethodCallExpression::class.java)
        assertInstanceOf<GrMethod>(call.resolveMethod())
        assertTrue(call.type!!.equalsToText(getExtraPropertiesExtensionFqn()))
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test project level extension closure delegate type`(gradleVersion: GradleVersion) {
    test(gradleVersion, SIMPLE_FIXTURE_BUILDER) {
      testBuildscript("ext { <caret> }") {
        closureDelegateTest(getExtraPropertiesExtensionFqn(), 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("""
      "<caret>prop",
      "project.<caret>prop",
      "project.ext.<caret>prop"
  """)
  fun `test property reference`(gradleVersion: GradleVersion, expression: String) {
    test(gradleVersion, SIMPLE_FIXTURE_BUILDER) {
      testBuildscript(expression) {
        referenceExpressionTest(GradleGroovyProperty::class.java, JAVA_LANG_INTEGER)
      }
    }
  }

  companion object {

    private val SIMPLE_FIXTURE_BUILDER = GradleTestFixtureBuilder.create("GradleExtensionsTest") { gradleVersion ->
      withSettingsFile {
        setProjectName("GradleExtensionsTest")
      }
      withBuildFile(gradleVersion) {
        withPrefix {
          call("ext") {
            assign("prop", 1)
          }
        }
      }
    }

    private val COMPLEX_EXTENSIONS_FIXTURE_BUILDER = GradleTestFixtureBuilder.create("GradleComplexExtensionsTest") { gradleVersion ->
      withSettingsFile {
        setProjectName("GradleComplexExtensionsTest")
      }
      withBuildFile(gradleVersion) {
        code("""
          
        """.trimIndent())
      }
    }
  }
}
