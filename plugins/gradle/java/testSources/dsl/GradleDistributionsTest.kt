// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.psi.PsiMethod
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.GradleDomainObjectProperty
import org.jetbrains.plugins.gradle.service.resolve.GradleExtensionProperty
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_DISTRIBUTION
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_FILE_COPY_SPEC
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest

class GradleDistributionsTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test distributions container`(gradleVersion: GradleVersion, decorator: String) {
    test(gradleVersion, FIXTURE_BUILDER) {
      testBuildscript(decorator, "<caret>distributions") {
        referenceExpressionTest(GradleExtensionProperty::class.java, getDistributionContainerFqn())
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test distributions call`(gradleVersion: GradleVersion, decorator: String) {
    test(gradleVersion, FIXTURE_BUILDER) {
      testBuildscript(decorator, "<caret>distributions {}") {
        methodCallTest(PsiMethod::class.java, getDistributionContainerFqn())
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test distributions closure delegate`(gradleVersion: GradleVersion, decorator: String) {
    test(gradleVersion, FIXTURE_BUILDER) {
      testBuildscript(decorator, "distributions { <caret> }") {
        closureDelegateTest(getDistributionContainerFqn(), 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test distribution via unqualified property reference`(gradleVersion: GradleVersion, decorator: String) {
    test(gradleVersion, FIXTURE_BUILDER) {
      testBuildscript(decorator, "distributions { <caret>foo }") {
        referenceExpressionTest(GradleDomainObjectProperty::class.java, GRADLE_API_DISTRIBUTION)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test distribution via unqualified method call`(gradleVersion: GradleVersion, decorator: String) {
    test(gradleVersion, FIXTURE_BUILDER) {
      testBuildscript(decorator, "distributions { <caret>foo {} }") {
        methodCallTest(PsiMethod::class.java, GRADLE_API_DISTRIBUTION)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test distribution closure delegate in unqualified method call`(gradleVersion: GradleVersion, decorator: String) {
    test(gradleVersion, FIXTURE_BUILDER) {
      testBuildscript(decorator, "distributions { foo { <caret> } }") {
        closureDelegateTest(GRADLE_API_DISTRIBUTION, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test distribution member via unqualified method call closure delegate`(gradleVersion: GradleVersion, decorator: String) {
    test(gradleVersion, FIXTURE_BUILDER) {
      testBuildscript(decorator, "distributions { foo { <caret>${getDistributionBaseNameMethod()} } }") {
        val method = resolveMethodTest(PsiMethod::class.java)
        assertEquals(GRADLE_API_DISTRIBUTION, method.containingClass!!.qualifiedName)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test distribution via qualified property reference`(gradleVersion: GradleVersion, decorator: String) {
    test(gradleVersion, FIXTURE_BUILDER) {
      testBuildscript(decorator, "distributions.<caret>foo") {
        referenceExpressionTest(GradleDomainObjectProperty::class.java, GRADLE_API_DISTRIBUTION)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test distribution via qualified method call`(gradleVersion: GradleVersion, decorator: String) {
    test(gradleVersion, FIXTURE_BUILDER) {
      testBuildscript(decorator, "distributions.<caret>foo {}") {
        methodCallTest(PsiMethod::class.java, GRADLE_API_DISTRIBUTION)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test distribution closure delegate in qualified method call`(gradleVersion: GradleVersion, decorator: String) {
    test(gradleVersion, FIXTURE_BUILDER) {
      testBuildscript(decorator, "distributions.foo { <caret> }") {
        closureDelegateTest(GRADLE_API_DISTRIBUTION, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test distribution member via qualified method call closure delegate`(gradleVersion: GradleVersion, decorator: String) {
    test(gradleVersion, FIXTURE_BUILDER) {
      testBuildscript(decorator, "distributions.foo { <caret>${getDistributionBaseNameMethod()} }") {
        val method = resolveMethodTest(PsiMethod::class.java)
        assertEquals(GRADLE_API_DISTRIBUTION, method.containingClass!!.qualifiedName)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test distribution contents closure delegate`(gradleVersion: GradleVersion, decorator: String) {
    test(gradleVersion, FIXTURE_BUILDER) {
      testBuildscript(decorator, "distributions { foo { contents { <caret> } } }") {
        closureDelegateTest(GRADLE_API_FILE_COPY_SPEC, 1)
      }
    }
  }

  companion object {

    private val FIXTURE_BUILDER = GradleTestFixtureBuilder.create("GradleDistributionsTest") { gradleVersion ->
      withSettingsFile(gradleVersion) {
        setProjectName("GradleDistributionsTest")
      }
      withBuildFile(gradleVersion) {
        withPlugin("distribution")
      }
    }
  }
}