// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.completion.GradleLookupWeigher
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest

class GradleCompletionTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testBasicCompletion(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      testCompletion("ex<caret>", "ext")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCompletionOfImplementationConsumer(gradleVersion: GradleVersion) {
    // 'implementation' should appear before 'invokeMethod'
    testJavaProject(gradleVersion) {
      testCompletion("dependencies { im<caret> }", "implementation", "invokeMethod")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testGrayOutForeignCompletionElement(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      testCompletion("build.gradle", "repositories { mavenCentral { go<caret> } }") {
        var hasGoogle = false
        for (element in it) {
          if (element.lookupString != "google") {
            continue
          } else {
            hasGoogle = true
          }
          assertTrue(GradleLookupWeigher.getGradleCompletionPriority(element) == GradleLookupWeigher.DEFAULT_COMPLETION_PRIORITY - 1)
        }
        assertTrue(hasGoogle)
      }
    }
  }
}