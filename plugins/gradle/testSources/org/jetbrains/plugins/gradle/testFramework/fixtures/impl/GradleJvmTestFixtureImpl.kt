// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListenerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.SdkTestFixture
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.isSupported


internal class GradleJvmTestFixtureImpl(private val gradleVersion: GradleVersion) : SdkTestFixture {

  private lateinit var fixtureDisposable: Disposable

  private lateinit var sdkTestFixture: SdkTestFixture

  override fun getSdk(): Sdk = sdkTestFixture.getSdk()

  override fun setUp() {
    fixtureDisposable = Disposer.newDisposable()

    sdkTestFixture = IdeaTestFixtureFactory.getFixtureFactory()
      .createSdkFixture(JavaSdk.getInstance()) { isSupported(gradleVersion, it) }
    sdkTestFixture.setUp()

    installLinkedSettingsWatcher()
  }

  override fun tearDown() {
    runAll(
      { sdkTestFixture.tearDown() },
      { Disposer.dispose(fixtureDisposable) }
    )
  }

  private fun installLinkedSettingsWatcher() {
    val listener = object : ExternalSystemSettingsListenerEx {
      override fun onProjectsLinked(
        project: Project,
        manager: ExternalSystemManager<*, *, *, *, *>,
        settings: Collection<ExternalProjectSettings>
      ) {
        for (projectSettings in settings) {
          if (projectSettings is GradleProjectSettings) {
            projectSettings.gradleJvm = getSdk().name
          }
        }
      }
    }
    ExternalSystemSettingsListenerEx.EP_NAME.point
      .registerExtension(listener, fixtureDisposable)
  }
}