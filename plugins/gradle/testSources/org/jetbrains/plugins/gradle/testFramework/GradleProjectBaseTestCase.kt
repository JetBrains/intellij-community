// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.testFramework.common.runAll
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleProjectTestFixture
import org.jetbrains.plugins.gradle.testFramework.util.onFailureCatching
import org.junit.jupiter.api.AfterAll

abstract class GradleProjectBaseTestCase : ExternalSystemTestCase() {

  private var fixture: GradleProjectTestFixture? = null

  val gradleFixture: GradleProjectTestFixture
    get() = requireNotNull(fixture) {
      "Gradle fixture isn't setup. Please use [GradleBaseTestCase.test] function inside your tests."
    }

  open fun test(gradleVersion: GradleVersion, fixtureBuilder: GradleTestFixtureBuilder, test: () -> Unit) {
    fixture = getOrCreateGradleTestFixture(gradleVersion, fixtureBuilder)
    runAll(
      { test() },
      { rollbackOrDestroyGradleTestFixture(gradleFixture) },
      { fixture = null }
    )
  }

  companion object {
    private val initializedFixtures = LinkedHashSet<FixtureId>()
    private val fixtures = LinkedHashMap<FixtureId, GradleProjectTestFixture>()

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

    @JvmStatic
    @AfterAll
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