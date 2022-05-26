// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dsl


import groovy.transform.CompileStatic
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixture
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule
import org.junit.Test
import org.junit.runners.Parameterized

import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_TASKS_JAVADOC_JAVADOC

@CompileStatic
class GradleActionTest extends GradleHighlightingLightTestCase {

  @Parameterized.Parameters(name = "with Gradle-{0}")
  static Collection<Object[]> data() {
    return [[VersionMatcherRule.BASE_GRADLE_VERSION].toArray()]
  }

  @Override
  GradleTestFixture createGradleTestFixture(@NotNull GradleVersion gradleVersion) {
    return createGradleTestFixture(gradleVersion, "java")
  }

  @Override
  List<String> getParentCalls() {
    return []
  }

  @Test
  void 'test domain collection forEach'() {
    doTest('tasks.withType(Javadoc).configureEach {\n' +
           '  <caret>\n' +
           '}') {
      closureDelegateTest(GRADLE_API_TASKS_JAVADOC_JAVADOC, 1)
    }
  }

  @Test
  void 'test nested version block'() {
    doTest('dependencies {' +
           ' implementation("group:artifact") {\n' +
           '  version { <caret> }\n' +
           '}') {
      closureDelegateTest("org.gradle.api.artifacts.MutableVersionConstraint", 1)
    }
  }
}
