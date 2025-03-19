// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.observable.util.toEnumOrNull
import com.intellij.util.xmlb.annotations.Attribute
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.util.GradleEnvironment
import org.jetbrains.plugins.gradle.util.GradleVersionXmlConverter

class GradleDefaultProjectSettings internal constructor() : BaseState() {

  var gradleJvm by string(ExternalSystemJdkUtil.USE_PROJECT_JDK)

  var delegatedBuild by property(GradleProjectSettings.DEFAULT_DELEGATE)

  var testRunner by enum(GradleProjectSettings.DEFAULT_TEST_RUNNER)

  var distributionType by enum(DistributionType.DEFAULT_WRAPPED)

  @get:Attribute(converter = GradleVersionXmlConverter::class)
  var gradleVersion by property<GradleVersion?>(null) { it == null }

  var gradleHome by string()

  fun copy(): GradleDefaultProjectSettings {
    val settings = GradleDefaultProjectSettings()
    settings.copyFrom(this)
    return settings
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as GradleDefaultProjectSettings

    if (gradleJvm != other.gradleJvm) return false
    if (delegatedBuild != other.delegatedBuild) return false
    if (testRunner != other.testRunner) return false
    if (distributionType != other.distributionType) return false
    if (gradleVersion != other.gradleVersion) return false
    return gradleHome == other.gradleHome
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + (gradleJvm?.hashCode() ?: 0)
    result = 31 * result + delegatedBuild.hashCode()
    result = 31 * result + testRunner.hashCode()
    result = 31 * result + distributionType.hashCode()
    result = 31 * result + (gradleVersion?.hashCode() ?: 0)
    result = 31 * result + (gradleHome?.hashCode() ?: 0)
    return result
  }

  @State(name = "GradleDefaultProjectSettings",
         category = SettingsCategory.TOOLS,
         exportable = true,
         storages = [Storage("gradle.default.xml", roamingType = RoamingType.DISABLED)])
  internal class Service : SimplePersistentStateComponent<GradleDefaultProjectSettings>(GradleDefaultProjectSettings()) {

    fun createProjectSettings(externalProjectPath: String): GradleProjectSettings {
      val headlessDistributionType = GradleEnvironment.Headless.GRADLE_DISTRIBUTION_TYPE?.toEnumOrNull<DistributionType>()
      val headlessGradleHome = GradleEnvironment.Headless.GRADLE_HOME
      return GradleProjectSettings(externalProjectPath).apply {
        gradleJvm = state.gradleJvm
        delegatedBuild = state.delegatedBuild
        testRunner = state.testRunner
        distributionType = headlessDistributionType ?: state.distributionType
        gradleHome = headlessGradleHome ?: state.gradleHome
      }
    }
  }

  companion object {

    @JvmStatic
    fun getInstance(): GradleDefaultProjectSettings {
      return service<Service>().state
    }

    @JvmStatic
    fun setInstance(settings: GradleDefaultProjectSettings) {
      service<Service>().state.copyFrom(settings)
    }

    @JvmStatic
    fun createProjectSettings(externalProjectPath: String): GradleProjectSettings {
      return service<Service>().createProjectSettings(externalProjectPath)
    }
  }
}