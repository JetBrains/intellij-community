// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dsl

import groovy.transform.CompileStatic
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixture
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_ARTIFACTS_REPOSITORIES_MAVEN_ARTIFACT_REPOSITORY

@CompileStatic
class GradlePublishingTest extends GradleHighlightingLightTestCase implements ResolveTest {

  @Override
  GradleTestFixture createGradleTestFixture(@NotNull GradleVersion gradleVersion) {
    return createGradleTestFixture(gradleVersion, "maven-publish")
  }

  @Override
  List<String> getParentCalls() {
    return super.getParentCalls() + 'buildscript'
  }

  @Test
  void 'test publishing closure delegate'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('publishing { <caret> }') {
      closureDelegateTest(getPublishingExtensionFqn(), 1)
    }
  }

  @Test
  void 'test publishing repositories maven url'() {
    doTest('publishing { repositories { maven { url<caret> "" } } }') {
      setterMethodTest('url', 'setUrl', GRADLE_API_ARTIFACTS_REPOSITORIES_MAVEN_ARTIFACT_REPOSITORY)
    }
  }
}
