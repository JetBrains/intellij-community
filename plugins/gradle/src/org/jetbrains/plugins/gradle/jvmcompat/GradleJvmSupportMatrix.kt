// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat

import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.util.Ranges

@State(name = "GradleJvmSupportMatrix", storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE)])
class GradleJvmSupportMatrix : IdeVersionedDataStorage<GradleCompatibilityState>(
  parser = GradleCompatibilityDataParser,
  defaultState = DEFAULT_DATA
) {
  @Volatile
  private var mySupportedGradleVersions: List<GradleVersion> = emptyList()

  @Volatile
  private var mySupportedJavaVersions: List<JavaVersion> = emptyList()

  @Volatile
  private var myCompatibility: List<Pair<Ranges<JavaVersion>, Ranges<GradleVersion>>> = emptyList()

  private fun applyState(state: GradleCompatibilityState) {
    myCompatibility = getCompatibilityRanges(state)
    mySupportedGradleVersions = state.supportedGradleVersions.map(GradleVersion::version)
    mySupportedJavaVersions = state.supportedJavaVersions.map(JavaVersion::parse)
  }

  init {
    applyState(DEFAULT_DATA)
  }

  override fun newState(): GradleCompatibilityState = GradleCompatibilityState()

  private fun getCompatibilityRanges(data: GradleCompatibilityState): List<Pair<Ranges<JavaVersion>, Ranges<GradleVersion>>> {
    return data.compatibility.map { entry ->
      val gradleVersionInfo = entry.gradleVersionInfo ?: ""
      val javaVersionInfo = entry.javaVersionInfo ?: ""
      val gradleRange = IdeVersionedDataParser.parseRange(gradleVersionInfo.split(','), GradleVersion::version)
      val javaRange = IdeVersionedDataParser.parseRange(javaVersionInfo.split(','), JavaVersion::parse)
      javaRange to gradleRange
    }
  }

  override fun onStateChanged(newState: GradleCompatibilityState) {
    applyState(newState)
  }

  fun getAllSupportedGradleVersions(): List<GradleVersion> {
    return mySupportedGradleVersions
  }

  fun getAllSupportedJavaVersions(): List<JavaVersion> {
    return mySupportedJavaVersions
  }

  private fun isSupportedImpl(gradleVersion: GradleVersion, javaVersion: JavaVersion): Boolean {
    return myCompatibility.any { (javaVersions, gradleVersions) ->
      javaVersion in javaVersions && gradleVersion in gradleVersions
    }
  }

  private fun isJavaSupportedByIdeaImpl(javaVersion: JavaVersion): Boolean {
    return mySupportedJavaVersions.min() <= javaVersion
  }

  private fun isGradleSupportedByIdeaImpl(gradleVersion: GradleVersion): Boolean {
    return minimalSupportedGradleVersion <= gradleVersion
  }

  private fun isGradleDeprecatedByIdeaImpl(gradleVersion: GradleVersion): Boolean {
    return gradleVersion < minimalRecommendedGradleVersion
  }

  val minimalSupportedGradleVersion
    get() = mySupportedGradleVersions.min()

  val minimalRecommendedGradleVersion = GradleVersion.version("4.5")

  companion object {

    @JvmStatic
    fun getInstance(): GradleJvmSupportMatrix {
      return service<GradleJvmSupportMatrix>()
    }

    /**
     * Checks that Gradle [gradleVersion] supports execution on Java [javaVersion].
     */
    @JvmStatic
    fun isSupported(gradleVersion: GradleVersion, javaVersion: JavaVersion): Boolean {
      return getInstance().isSupportedImpl(gradleVersion, javaVersion)
    }

    @JvmStatic
    fun isSupported(gradleVersion: GradleVersion, javaVersionString: String): Boolean {
      val javaVersion = JavaVersion.tryParse(javaVersionString) ?: return false
      return isSupported(gradleVersion, javaVersion)
    }

    /**
     * Checks that current Idea supports integration with any Gradle which executes on Java [javaVersion].
     */
    @JvmStatic
    fun isJavaSupportedByIdea(javaVersion: JavaVersion): Boolean {
      return getInstance().isJavaSupportedByIdeaImpl(javaVersion)
    }

    @JvmStatic
    fun isJavaSupportedByIdea(javaVersionString: String): Boolean {
      val javaVersion = JavaVersion.tryParse(javaVersionString) ?: return false
      return getInstance().isJavaSupportedByIdeaImpl(javaVersion)
    }

    @JvmStatic
    fun isJavaHomeSupportedByIdea(javaHome: String): Boolean {
      val javaSdkType = ExternalSystemJdkUtil.getJavaSdkType()
      val javaVersionString = javaSdkType.getVersionString(javaHome) ?: return false
      return isJavaSupportedByIdea(javaVersionString)
    }

    /**
     * Checks that current Idea supports integration with Gradle which executes on any Java.
     */
    @JvmStatic
    fun isGradleSupportedByIdea(gradleVersion: GradleVersion): Boolean {
      return getInstance().isGradleSupportedByIdeaImpl(gradleVersion)
    }

    @JvmStatic
    fun isGradleDeprecatedByIdea(gradleVersion: GradleVersion): Boolean {
      return getInstance().isGradleDeprecatedByIdeaImpl(gradleVersion)
    }
  }
}