// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_ARTIFACTS_MUTABLE_VERSION_CONSTRAINT
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_TASKS_JAVADOC_JAVADOC
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.junit.jupiter.params.ParameterizedTest

class GradleActionTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test domain collection forEach`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      testBuildscript("tasks.withType(Javadoc).configureEach { <caret> }") {
        closureDelegateTest(GRADLE_API_TASKS_JAVADOC_JAVADOC, 1)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test nested version block`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      testBuildscript("dependencies { implementation('group:artifact') { version { <caret> } }") {
        closureDelegateTest(GRADLE_API_ARTIFACTS_MUTABLE_VERSION_CONSTRAINT, 1)
      }
    }
  }
}
