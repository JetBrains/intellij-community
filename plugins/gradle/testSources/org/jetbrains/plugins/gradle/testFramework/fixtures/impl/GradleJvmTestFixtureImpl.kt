// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListenerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.impl.AbstractSdkTestFixture
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.isSupported


internal class GradleJvmTestFixtureImpl(private val gradleVersion: GradleVersion) : AbstractSdkTestFixture() {

  private lateinit var fixtureDisposable: Disposable

  override val sdkType: SdkType
    get() = JavaSdk.getInstance()

  override fun isSdkSupported(versionString: String): Boolean {
    return isSupported(gradleVersion, versionString)
  }

  override fun findOrCreateSdk(): Sdk {
    return findSdkInTable() ?: findAndAddSdk() ?: throw AssertionError(
      "Cannot find JDK for $gradleVersion.\n" +
      "Please, research JDK restrictions or discuss it with test author, and install JDK manually.\n" +
      "Checked paths: " + sdkType.suggestHomePaths()
    )
  }

  override fun setUp() {
    super.setUp()

    fixtureDisposable = Disposer.newDisposable()

    installLinkedSettingsWatcher()
  }

  override fun tearDown() {
    runAll(
      { Disposer.dispose(fixtureDisposable) },
      { super.tearDown() }
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