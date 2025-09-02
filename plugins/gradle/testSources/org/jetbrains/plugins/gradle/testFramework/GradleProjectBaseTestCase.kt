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
    "Gradle version wasn't setup. Please use [GradleBaseTestCase.test] function inside your tests."
  }

  private var _fixtureBuilder: GradleTestFixtureBuilder? = null

  private var _gradleFixture: GradleProjectTestFixture? = null
  val gradleFixture: GradleProjectTestFixture
    get() = requireNotNull(_gradleFixture) {
      "Gradle fixture wasn't setup. Please use [GradleBaseTestCase.test] function inside your tests."
    }

  open fun setUp() {
    val fixtureBuilder = requireNotNull(_fixtureBuilder) {
      "Gradle fixture builder wasn't setup. Please use [GradleBaseTestCase.test] function inside your tests."
    }
    _gradleFixture = getOrCreateGradleTestFixture(gradleVersion, fixtureBuilder)
  }

  open fun tearDown() {
    runAll(
      { _gradleFixture?.let { rollbackOrDestroyGradleTestFixture(it) } },
      { _gradleFixture = null }
    )
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

    private val initializedFixtures = LinkedHashSet<FixtureId>()
    private val fixtures = LinkedHashMap<FixtureId, GradleProjectTestFixture>()

    @JvmStatic
    @AfterAll
    fun tearDownGradleBaseTestCase() {
      destroyAllGradleFixtures()
    }

    private fun getOrCreateGradleTestFixture(gradleVersion: GradleVersion, builder: GradleTestFixtureBuilder): GradleProjectTestFixture {
      val fixtureId = builder.getFixtureId(gradleVersion)
      if (fixtureId !in initializedFixtures) {
        destroyAllGradleFixtures()
      }
      if (fixtureId !in fixtures) {
        val fixture = builder.createFixture(gradleVersion)
        runCatching { fixture.setUp() }
          .onFailureCatching { fixture.tearDown() }
          .getOrThrow()
        fixtures[fixtureId] = fixture
        initializedFixtures.add(fixtureId)
      }
      return fixtures[fixtureId]!!
    }

    fun destroyAllGradleFixtures() {
      runAll(fixtures.values.reversed(), ::destroyGradleFixture)
    }

    private fun rollbackOrDestroyGradleTestFixture(fixture: GradleProjectTestFixture) {
      fixture.fileFixture.rollbackAll()
      if (fixture.fileFixture.hasErrors()) {
        initializedFixtures.remove(fixture.getFixtureId())
        destroyGradleFixture(fixture)
      }
    }

    private fun destroyGradleFixture(fixture: GradleProjectTestFixture) {
      fixtures.remove(fixture.getFixtureId())
      fixture.tearDown()
    }

    private data class FixtureId(val projectName: String, val version: GradleVersion)

    private fun GradleTestFixtureBuilder.getFixtureId(gradleVersion: GradleVersion) =
      FixtureId(projectName, gradleVersion)

    private fun GradleProjectTestFixture.getFixtureId() =
      FixtureId(projectName, gradleVersion)
  }
}