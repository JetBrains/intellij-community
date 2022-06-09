// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.GradleGroovyProperty
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixtureFactory
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyProperty
import org.junit.jupiter.params.ParameterizedTest
import com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER
import com.intellij.testFramework.assertInstanceOf
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleCodeInsightTestFixture
import org.junit.jupiter.api.Assertions.assertTrue

class GradleExtensionsTest : GradleCodeInsightTestCase() {

  override fun createGradleTestFixture(gradleVersion: GradleVersion): GradleCodeInsightTestFixture {
    val projectName = "property-project"
    return GradleTestFixtureFactory.getFixtureFactory()
      .createGradleCodeInsightTestFixture(projectName, gradleVersion) {
        withSettingsFile {
          setProjectName(projectName)
        }
        withBuildFile(gradleVersion) {
          withPrefix {
            call("ext") {
              assign("prop", 1)
            }
          }
        }
      }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test project level extension property`(gradleVersion: GradleVersion) {
    testBuildscript(gradleVersion, "ext") {
      val ref = elementUnderCaret(GrReferenceExpression::class.java)
      assertInstanceOf<GroovyProperty>(ref.resolve())
      assertTrue(ref.type!!.equalsToText(getExtraPropertiesExtensionFqn()))
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test project level extension call type`(gradleVersion: GradleVersion) {
    testBuildscript(gradleVersion, "ext {}") {
      val call = elementUnderCaret(GrMethodCallExpression::class.java)
      assertInstanceOf<GrMethod>(call.resolveMethod())
      assertTrue(call.type!!.equalsToText(getExtraPropertiesExtensionFqn()))
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test project level extension closure delegate type`(gradleVersion: GradleVersion) {
    testBuildscript(gradleVersion, "ext { <caret> }") {
      closureDelegateTest(getExtraPropertiesExtensionFqn(), 1)
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("""
      "<caret>prop",
      "project.<caret>prop"
  """)
  fun `test property reference`(gradleVersion: GradleVersion, expression: String) {
    testBuildscript(gradleVersion, expression) {
      referenceExpressionTest(GradleGroovyProperty::class.java, JAVA_LANG_INTEGER)
    }
  }
}
