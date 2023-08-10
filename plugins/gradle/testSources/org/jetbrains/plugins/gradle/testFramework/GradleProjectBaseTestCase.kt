// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.roots.impl.libraries.LibraryTableTracker
import com.intellij.openapi.vfs.impl.VirtualFilePointerTracker
import com.intellij.testFramework.SdkLeakTracker
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.BareTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleProjectTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.tracker.ESListenerLeakTracker
import org.jetbrains.plugins.gradle.testFramework.util.onFailureCatching
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll


abstract class GradleProjectBaseTestCase {

  private var fixture: GradleProjectTestFixture? = null

  val gradleFixture: GradleProjectTestFixture
    get() = requireNotNull(fixture) {
      "Gradle fixture isn't setup. Please use [GradleBaseTestCase.test] function inside your tests."
    }

  open fun setUp() = Unit

  open fun tearDown() = Unit

  open fun patchFixtureBuilder(fixtureBuilder: GradleTestFixtureBuilder): GradleTestFixtureBuilder = fixtureBuilder

  open fun test(gradleVersion: GradleVersion, fixtureBuilder: GradleTestFixtureBuilder, test: () -> Unit) {
    val patchedBuilder = patchFixtureBuilder(fixtureBuilder)
    fixture = getOrCreateGradleTestFixture(gradleVersion, patchedBuilder)
    setUp()
    runAll(
      { test() },
      { tearDown() },
      { rollbackOrDestroyGradleTestFixture(gradleFixture) },
      { fixture = null }
    )
  }

  companion object {

    private lateinit var applicationFixture: ApplicationFixture

    private lateinit var listenerLeakTracker: ESListenerLeakTracker

    private val initializedFixtures = LinkedHashSet<FixtureId>()
    private val fixtures = LinkedHashMap<FixtureId, GradleProjectTestFixture>()

    @JvmStatic
    @BeforeAll
    fun setUpGradleBaseTestCase() {
      applicationFixture = ApplicationFixture()
      applicationFixture.setUp()

      listenerLeakTracker = ESListenerLeakTracker()
      listenerLeakTracker.setUp()
    }

    @JvmStatic
    @AfterAll
    fun tearDownGradleBaseTestCase() {
      runAll(
        { destroyAllGradleFixtures() },
        { listenerLeakTracker.tearDown() },
        { applicationFixture.tearDown() }
      )
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

    private fun destroyAllGradleFixtures() {
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

  /**
   * @see com.intellij.testFramework.junit5.TestApplication
   */
  private class ApplicationFixture : IdeaTestFixture {

    private lateinit var bareFixture: BareTestFixture

    private lateinit var virtualFilePointerTracker: VirtualFilePointerTracker
    private lateinit var libraryLeakTracker: LibraryTableTracker
    private lateinit var sdkLeakTracker: SdkLeakTracker

    override fun setUp() {
      bareFixture = IdeaTestFixtureFactory.getFixtureFactory()
        .createBareFixture()
      bareFixture.setUp()

      virtualFilePointerTracker = VirtualFilePointerTracker()
      libraryLeakTracker = LibraryTableTracker()
      sdkLeakTracker = SdkLeakTracker()
    }

    override fun tearDown() {
      runAll(
        { invokeAndWaitIfNeeded { sdkLeakTracker.checkForJdkTableLeaks() } },
        { libraryLeakTracker.assertDisposed() },
        { virtualFilePointerTracker.assertPointersAreDisposed() },
        { bareFixture.tearDown() }
      )
    }
  }
}