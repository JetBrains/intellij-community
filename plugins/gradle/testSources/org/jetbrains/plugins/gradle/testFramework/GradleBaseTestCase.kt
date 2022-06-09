// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixture
import org.jetbrains.plugins.gradle.testFramework.builders.GradleTestFixtureBuilder
import org.junit.jupiter.api.AfterAll

abstract class GradleBaseTestCase : ExternalSystemTestCase() {

  private var fixture: GradleTestFixture? = null

  val gradleFixture: GradleTestFixture
    get() = requireNotNull(fixture) {
      "Gradle fixture isn't setup. Please use [GradleBaseTestCase.test] function inside your tests."
    }

  open fun test(gradleVersion: GradleVersion, fixtureBuilder: GradleTestFixtureBuilder, test: () -> Unit) {
    fixture = createGradleTestFixture(gradleVersion, fixtureBuilder)
    try {
      test()
    }
    finally {
      gradleFixture.fileFixture.rollbackAll()
      if (gradleFixture.fileFixture.hasErrors()) {
        destroyGradleFixture(gradleFixture)
      }
      fixture = null
    }
  }

  companion object {
    private val fixtures = LinkedHashMap<Any, GradleTestFixture>()

    @JvmStatic
    @AfterAll
    fun destroyGradleFixtures() {
      for (fixture in fixtures.values.reversed()) {
        destroyGradleFixture(fixture)
      }
    }

    private fun createGradleTestFixture(gradleVersion: GradleVersion, builder: GradleTestFixtureBuilder): GradleTestFixture {
      return fixtures.getOrPut(gradleVersion.version) {
        val fixture = builder.createFixture(gradleVersion)
        fixture.setUp()
        fixture
      }
    }

    private fun destroyGradleFixture(fixture: GradleTestFixture) {
      fixtures.remove(fixture.gradleVersion.version)
      fixture.tearDown()
    }
  }
}