// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class GradleTaskClassifierTest {

  @MethodSource("argumentToValidateTaskClassification")
  @ParameterizedTest
  fun testClassifyTask(expected: String, toClassify: String?) {
    assertEquals(expected, GradleTaskClassifier.classifyTaskName(toClassify))
  }

  @Test
  fun testClassifiedVerifier() {
    assertTrue(GradleTaskClassifier.isClassified("other"))
    assertTrue(GradleTaskClassifier.isClassified("test"))
    assertTrue(GradleTaskClassifier.isClassified("lint"))
    assertTrue(GradleTaskClassifier.isClassified("compileJava"))
    assertTrue(GradleTaskClassifier.isClassified("compileTestKotlin"))
    assertFalse(GradleTaskClassifier.isClassified("compileIntegrationTestKotlin"))
    assertFalse(GradleTaskClassifier.isClassified("Abcd"))
    assertFalse(GradleTaskClassifier.isClassified("publishAbcToRepository"))
    assertFalse(GradleTaskClassifier.isClassified("publish"))
  }

  private companion object {
    @JvmStatic
    fun argumentToValidateTaskClassification(): List<Arguments> = listOf(
      Arguments.of("compileKotlin", "compileKotlinLibrarySources"),
      Arguments.of("compileJava", "compileUnknownJava"),
      Arguments.of("lint", "lint"),
      Arguments.of("lintTest", "lintUnitTestSources"),
      Arguments.of("compileTestKotlin", "compileJava11TestKotlin"),
      Arguments.of("other", "publish"),
      Arguments.of("compileTestKotlin", "compileMyIntegrationTestKotlin"),
      Arguments.of("other", null),
      Arguments.of("other", ""),
      Arguments.of("compileJava", "userCompileJava"),
      Arguments.of("dokkaJavadoc", "dokkaJavadoc")
    )
  }
}
