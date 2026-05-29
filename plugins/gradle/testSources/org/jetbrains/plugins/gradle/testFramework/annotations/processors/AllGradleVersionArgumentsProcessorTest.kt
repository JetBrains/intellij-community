// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.annotations.processors

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.setSystemProperty
import com.intellij.testFramework.junit5.TestDisposable
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule.Companion.BASE_GRADLE_VERSION
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule.Companion.SUPPORTED_GRADLE_VERSIONS
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.support.ParameterDeclarations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.Optional
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.streams.asSequence

class AllGradleVersionArgumentsProcessorTest {

  private object TestClass {

    //@ParameterizedTest
    @AllGradleVersionsSource
    fun allSupportedGradleVersions() = Unit

    //@ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(BASE_GRADLE_VERSION)
    fun singleSupportedGradleVersion() = Unit
  }

  //@ParameterizedClass
  @AllGradleVersionsSource
  private class TestParametrizedClass(@Suppress("unused") gradleVersion: GradleVersion)

  //@ParameterizedClass
  @AllGradleVersionsSource
  @TargetVersions(BASE_GRADLE_VERSION)
  private class TestParametrizedClassWithSingleSupportedGradleVersion(@Suppress("unused") gradleVersion: GradleVersion)

  enum class TestData(
    val gradleVersionsToRun: String?,
    val kotlinClass: KClass<*>,
    val kotlinMethod: KCallable<*>?,
    val expectedGradleVersions: List<String>,
  ) {
    CLASS_LEVEL_ALL_GRADLE_VERSIONS(
      gradleVersionsToRun = null,
      kotlinClass = TestParametrizedClass::class,
      kotlinMethod = null,
      expectedGradleVersions = SUPPORTED_GRADLE_VERSIONS,
    ),
    CLASS_LEVEL_SINGLE_GRADLE_VERSION(
      gradleVersionsToRun = null,
      kotlinClass = TestParametrizedClassWithSingleSupportedGradleVersion::class,
      kotlinMethod = null,
      expectedGradleVersions = listOf(BASE_GRADLE_VERSION),
    ),
    METHOD_LEVEL_ALL_GRADLE_VERSIONS(
      gradleVersionsToRun = null,
      kotlinClass = TestClass::class,
      kotlinMethod = TestClass::allSupportedGradleVersions,
      expectedGradleVersions = SUPPORTED_GRADLE_VERSIONS,
    ),
    METHOD_LEVEL_SINGLE_GRADLE_VERSION(
      gradleVersionsToRun = null,
      kotlinClass = TestClass::class,
      kotlinMethod = TestClass::singleSupportedGradleVersion,
      expectedGradleVersions = listOf(BASE_GRADLE_VERSION),
    ),
    FIRST_LAST_CLASS_LEVEL_ALL_GRADLE_VERSIONS(
      gradleVersionsToRun = "FIRST_LAST",
      kotlinClass = TestParametrizedClass::class,
      kotlinMethod = null,
      expectedGradleVersions = listOf(SUPPORTED_GRADLE_VERSIONS.first(), SUPPORTED_GRADLE_VERSIONS.last()),
    ),
    FIRST_LAST_CLASS_LEVEL_SINGLE_GRADLE_VERSION(
      gradleVersionsToRun = "FIRST_LAST",
      kotlinClass = TestParametrizedClassWithSingleSupportedGradleVersion::class,
      kotlinMethod = null,
      expectedGradleVersions = listOf(BASE_GRADLE_VERSION),
    ),
    FIRST_LAST_METHOD_LEVEL_ALL_GRADLE_VERSIONS(
      gradleVersionsToRun = "FIRST_LAST",
      kotlinClass = TestClass::class,
      kotlinMethod = TestClass::allSupportedGradleVersions,
      expectedGradleVersions = listOf(SUPPORTED_GRADLE_VERSIONS.first(), SUPPORTED_GRADLE_VERSIONS.last()),
    ),
    FIRST_LAST_METHOD_LEVEL_SINGLE_GRADLE_VERSION(
      gradleVersionsToRun = "FIRST_LAST",
      kotlinClass = TestClass::class,
      kotlinMethod = TestClass::singleSupportedGradleVersion,
      expectedGradleVersions = listOf(BASE_GRADLE_VERSION),
    ),
  }

  @ParameterizedTest
  @EnumSource(TestData::class)
  fun `test Gradle versions`(testData: TestData, @TestDisposable disposable: Disposable) {
    setSystemProperty("gradle.versions.to.run", testData.gradleVersionsToRun, disposable)

    val javaClass = testData.kotlinClass.java
    val javaMethod = testData.kotlinMethod?.let { javaClass.getDeclaredMethod(it.name) }
    val extensionContext = mock<ExtensionContext> {
      on { testClass } doReturn Optional.of(javaClass)
      on { testMethod } doReturn Optional.ofNullable(javaMethod)
    }
    val processor = AllGradleVersionArgumentsProcessor().apply {
      accept((javaMethod ?: javaClass).getAnnotation(AllGradleVersionsSource::class.java))
    }
    val parameters = mock<ParameterDeclarations>()
    val actualGradleVersions = processor.provideArguments(parameters, extensionContext).asSequence()
      .map { it.get().single() as GradleVersion }
      .map { it.version }
      .toList()
    assertEquals(testData.expectedGradleVersions, actualGradleVersions)
  }
}
