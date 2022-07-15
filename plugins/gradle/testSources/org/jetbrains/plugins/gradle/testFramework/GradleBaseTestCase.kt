// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.testFramework.common.runAll
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixture
import org.jetbrains.plugins.gradle.testFramework.util.onFailureCatching
import org.junit.jupiter.api.AfterAll

abstract class GradleBaseTestCase : ExternalSystemTestCase() {

  private var fixture: GradleTestFixture? = null

  val gradleFixture: GradleTestFixture
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
    private val fixtures = LinkedHashMap<FixtureId, GradleTestFixture>()

    private fun getOrCreateGradleTestFixture(gradleVersion: GradleVersion, builder: GradleTestFixtureBuilder): GradleTestFixture {
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

    private fun rollbackOrDestroyGradleTestFixture(fixture: GradleTestFixture) {
      fixture.fileFixture.rollbackAll()
      if (fixture.fileFixture.hasErrors()) {
        initializedFixtures.remove(fixture.getFixtureId())
        destroyGradleFixture(fixture)
      }
    }

    private fun destroyGradleFixture(fixture: GradleTestFixture) {
      fixtures.remove(fixture.getFixtureId())
      fixture.tearDown()
    }

    private data class FixtureId(val projectName: String, val version: GradleVersion)

    private fun GradleTestFixtureBuilder.getFixtureId(gradleVersion: GradleVersion) =
      FixtureId(projectName, gradleVersion)

    private fun GradleTestFixture.getFixtureId() =
      FixtureId(projectName, gradleVersion)
  }
}