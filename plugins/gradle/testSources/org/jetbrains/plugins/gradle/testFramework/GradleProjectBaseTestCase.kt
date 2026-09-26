// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.observable.operation.core.onFailureCatching
import com.intellij.testFramework.common.runAll
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleProjectTestFixture
import org.junit.jupiter.api.AfterAll

abstract class GradleProjectBaseTestCase {

  private var _gradleVersion: GradleVersion? = null
  val gradleVersion: GradleVersion get() = requireNotNull(_gradleVersion) {
    "Gradle version wasn't setup. Please use [${GradleProjectBaseTestCase::test}] function inside your tests."
  }

  private var _fixtureBuilder: GradleTestFixtureBuilder? = null
  private val fixtureBuilder: GradleTestFixtureBuilder
    get() = requireNotNull(_fixtureBuilder) {
      "Gradle fixture builder wasn't setup. Please use [${GradleProjectBaseTestCase::test}] function inside your tests."
    }

  open fun setUp() {
    reuseOrSetUpGradleFixture(gradleVersion, fixtureBuilder)
  }

  open fun tearDown() {
    rollbackOrTearDownGradleFixture()
  }

  fun test(gradleVersion: GradleVersion, fixtureBuilder: GradleTestFixtureBuilder, test: () -> Unit) {
    runAll(
      {
        _gradleVersion = gradleVersion
        _fixtureBuilder = fixtureBuilder
        setUp()
        test()
      },
      { tearDown() },
      { _fixtureBuilder = null },
      { _gradleVersion = null }
    )
  }

  companion object {

    private var _gradleFixture: GradleProjectTestFixture? = null
    val gradleFixture: GradleProjectTestFixture
      get() = requireNotNull(_gradleFixture) {
        "Gradle fixture wasn't setup. Please use [${GradleProjectBaseTestCase::test}] function inside your tests."
      }

    @JvmStatic
    @AfterAll
    fun destroyAllGradleFixtures() {
      tearDownGradleFixture()
    }

    private fun reuseOrSetUpGradleFixture(gradleVersion: GradleVersion, builder: GradleTestFixtureBuilder) {
      if (_gradleFixture?.getFixtureId() != builder.getFixtureId(gradleVersion)) {
        tearDownGradleFixture()
        setUpGradleFixture(gradleVersion, builder)
      }
    }

    private fun rollbackOrTearDownGradleFixture() {
      val gradleFixture = _gradleFixture ?: return
      gradleFixture.fileFixture.rollbackAll()
      if (gradleFixture.fileFixture.hasErrors()) {
        tearDownGradleFixture()
      }
    }

    private fun setUpGradleFixture(gradleVersion: GradleVersion, builder: GradleTestFixtureBuilder) {
      _gradleFixture = builder.createFixture(gradleVersion)
        .also { fixture ->
          runCatching { fixture.setUp() }
            .onFailureCatching { fixture.tearDown() }
            .getOrThrow()
        }
    }

    private fun tearDownGradleFixture() {
      _gradleFixture?.tearDown()
      _gradleFixture = null
    }

    private data class FixtureId(val projectName: String, val version: GradleVersion)

    private fun GradleTestFixtureBuilder.getFixtureId(gradleVersion: GradleVersion) =
      FixtureId(projectName, gradleVersion)

    private fun GradleProjectTestFixture.getFixtureId() =
      FixtureId(projectName, gradleVersion)
  }
}