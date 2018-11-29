// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.ThreeState.*
import com.intellij.util.ThrowableRunnable
import org.jetbrains.plugins.gradle.settings.GradleSystemRunningSettings.PreferredTestRunner.*
import org.junit.After
import org.junit.Before
import org.junit.Test

class GradleSettingsTest : UsefulTestCase() {

  private lateinit var myTestFixture: IdeaProjectTestFixture
  private lateinit var myProject: Project
  private lateinit var systemRunningSettings: GradleSystemRunningSettings
  private lateinit var gradleProjectSettings: GradleProjectSettings

  @Before
  override fun setUp() {
    super.setUp()
    myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name).fixture
    myTestFixture.setUp()
    myProject = myTestFixture.project

    systemRunningSettings = GradleSystemRunningSettings.getInstance()
    systemRunningSettings.loadState(GradleSystemRunningSettings.MyState())
    gradleProjectSettings = GradleProjectSettings().apply { externalProjectPath = myProject.guessProjectDir()!!.path }
    GradleSettings.getInstance(myProject).linkProject(gradleProjectSettings)
  }

  @After
  override fun tearDown() {
    RunAll()
      .append(ThrowableRunnable { myTestFixture.tearDown() })
      .append(ThrowableRunnable { super.tearDown() })
      .run()
  }

  @Test
  fun `test delegation settings default configuration`() {
    // check test runner defaults
    assertNull(gradleProjectSettings.testRunner)
    assertEquals(PLATFORM_TEST_RUNNER, gradleProjectSettings.effectiveTestRunner)
    assertEquals(PLATFORM_TEST_RUNNER, systemRunningSettings.defaultTestRunner)
    assertEquals(PLATFORM_TEST_RUNNER, systemRunningSettings.getTestRunner(myProject, gradleProjectSettings.externalProjectPath))

    // check build/run defaults
    assertEquals(UNSURE, gradleProjectSettings.delegatedBuild)
    assertEquals(NO, gradleProjectSettings.effectiveDelegatedBuild)
    assertFalse(systemRunningSettings.isDelegatedBuildEnabledByDefault)
    assertFalse(systemRunningSettings.isDelegatedBuildEnabled(myProject, gradleProjectSettings.externalProjectPath))
  }

  @Test
  fun `test delegation settings per linked project`() {
    // check test runner configuration change
    gradleProjectSettings.testRunner = CHOOSE_PER_TEST
    assertEquals(CHOOSE_PER_TEST, gradleProjectSettings.testRunner)
    assertEquals(CHOOSE_PER_TEST, gradleProjectSettings.effectiveTestRunner)
    assertEquals(PLATFORM_TEST_RUNNER, systemRunningSettings.defaultTestRunner)
    assertEquals(CHOOSE_PER_TEST, systemRunningSettings.getTestRunner(myProject, gradleProjectSettings.externalProjectPath))

    //// check app default change
    systemRunningSettings.defaultTestRunner = GRADLE_TEST_RUNNER
    assertEquals(CHOOSE_PER_TEST, gradleProjectSettings.testRunner)
    assertEquals(CHOOSE_PER_TEST, gradleProjectSettings.effectiveTestRunner)
    assertEquals(GRADLE_TEST_RUNNER, systemRunningSettings.defaultTestRunner)
    assertEquals(CHOOSE_PER_TEST, systemRunningSettings.getTestRunner(myProject, gradleProjectSettings.externalProjectPath))

    // check build/run configuration change
    gradleProjectSettings.delegatedBuild = YES
    assertEquals(YES, gradleProjectSettings.delegatedBuild)
    assertEquals(YES, gradleProjectSettings.effectiveDelegatedBuild)
    assertFalse(systemRunningSettings.isDelegatedBuildEnabledByDefault)
    assertTrue(systemRunningSettings.isDelegatedBuildEnabled(myProject, gradleProjectSettings.externalProjectPath))

    //// check app default change
    systemRunningSettings.isDelegatedBuildEnabledByDefault = true
    gradleProjectSettings.delegatedBuild = NO
    assertEquals(NO, gradleProjectSettings.delegatedBuild)
    assertEquals(NO, gradleProjectSettings.effectiveDelegatedBuild)
    assertTrue(systemRunningSettings.isDelegatedBuildEnabledByDefault)
    assertFalse(systemRunningSettings.isDelegatedBuildEnabled(myProject, gradleProjectSettings.externalProjectPath))
  }
}
