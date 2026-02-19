// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListenerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.testFramework.fixtures.IdeaTestFixture
import org.gradle.util.GradleVersion
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.jetbrains.jps.model.java.JdkVersionDetector.JdkVersionInfo
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.tooling.GradleJvmResolver
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction

/**
 * A fixture which provides SDKs for Gradle JVM and cleans up it when test will be finished.
 * Expected that application will be started before the first SDK is set up by this fixture.
 */
class GradleJvmTestFixture(
  private val gradleVersion: GradleVersion,
  private val javaVersionRestriction: JavaVersionRestriction,
) : IdeaTestFixture {

  private lateinit var fixtureDisposable: Disposable

  private lateinit var sdk: Sdk

  lateinit var gradleJvm: String
    private set

  lateinit var gradleJvmPath: String
    private set

  lateinit var gradleJvmInfo: JdkVersionInfo
    private set

  override fun setUp() {
    fixtureDisposable = Disposer.newDisposable()
    sdk = GradleJvmResolver.resolveGradleJvm(gradleVersion, fixtureDisposable, javaVersionRestriction)
    gradleJvm = sdk.name
    gradleJvmPath = sdk.homePath!!
    gradleJvmInfo = JdkVersionDetector.getInstance().detectJdkVersionInfo(gradleJvmPath)!!
  }

  override fun tearDown() {
    Disposer.dispose(fixtureDisposable)
  }

  inline fun <R> withProjectSettingsConfigurator(action: () -> R): R {
    return Disposer.newDisposable().use { disposable ->
      installProjectSettingsConfigurator(disposable)
      action()
    }
  }

  fun installProjectSettingsConfigurator(parentDisposable: Disposable): GradleJvmTestFixture = apply {
    val listener = object : ExternalSystemSettingsListenerEx {
      override fun onProjectsLinked(
        project: Project,
        manager: ExternalSystemManager<*, *, *, *, *>,
        settings: Collection<ExternalProjectSettings>
      ) {
        for (projectSettings in settings) {
          if (projectSettings is GradleProjectSettings) {
            println("Configured Gradle JVM (${sdk.name}) for project ${projectSettings.externalProjectPath}")
            projectSettings.gradleJvm = gradleJvm
          }
        }
      }
    }
    ExternalSystemSettingsListenerEx.EP_NAME.point
      .registerExtension(listener, parentDisposable)
  }
}
