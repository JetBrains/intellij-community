// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat

import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.gradle.util.Ranges

@State(name = "GradleJvmSupportMatrix", storages = [Storage(StoragePathMacros.CACHE_FILE)])
class GradleJvmSupportMatrix : IdeVersionedDataStorage<GradleCompatibilityState>(
  parser = GradleCompatibilityDataParser,
  defaultState = DEFAULT_DATA
) {
  @Volatile
  private var supportedGradleVersions: List<GradleVersion> = emptyList()

  @Volatile
  private var supportedJavaVersions: List<JavaVersion> = emptyList()

  @Volatile
  private var compatibility: List<Pair<Ranges<JavaVersion>, Ranges<GradleVersion>>> = emptyList()

  private fun applyState(state: GradleCompatibilityState) {
    compatibility = getCompatibilityRanges(state)
    supportedGradleVersions = state.supportedGradleVersions.map(GradleVersion::version)
    supportedJavaVersions = state.supportedJavaVersions.mapNotNull(JavaVersion::tryParse)
  }

  init {
    applyState(DEFAULT_DATA)
  }

  override fun newState(): GradleCompatibilityState = GradleCompatibilityState()

  private fun getCompatibilityRanges(data: GradleCompatibilityState): List<Pair<Ranges<JavaVersion>, Ranges<GradleVersion>>> {
    return data.compatibility.mapNotNull { entry ->
      val gradleVersionInfo = entry.gradleVersionInfo ?: ""
      val javaVersionInfo = entry.javaVersionInfo ?: ""
      val gradleRange = IdeVersionedDataParser.parseRange(gradleVersionInfo.split(','), GradleVersion::version)
      val javaRange = runCatching {
        IdeVersionedDataParser.parseRange(javaVersionInfo.split(','), JavaVersion::parse)
      }.getOrNull() ?: return@mapNotNull null
      javaRange to gradleRange
    }
  }

  override fun onStateChanged(newState: GradleCompatibilityState) {
    applyState(newState)
  }

  private fun isSupportedImpl(gradleVersion: GradleVersion, javaVersion: JavaVersion): Boolean {
    return compatibility.any { (javaVersions, gradleVersions) ->
      javaVersion in javaVersions && gradleVersion.baseVersion in gradleVersions
    }
  }

  private fun isJavaSupportedByIdeaImpl(javaVersion: JavaVersion): Boolean {
    return getOldestSupportedJavaVersionByIdeaImpl() <= javaVersion
  }

  private fun isGradleSupportedByIdeaImpl(gradleVersion: GradleVersion): Boolean {
    return getOldestSupportedGradleVersionByIdeaImpl() <= gradleVersion.baseVersion
  }

  private fun isGradleDeprecatedByIdeaImpl(gradleVersion: GradleVersion): Boolean {
    return gradleVersion.baseVersion < getOldestNonDeprecatedGradleVersionByIdeaImpl()
  }

  private fun getSupportedGradleVersionsImpl(javaVersion: JavaVersion): List<GradleVersion> {
    return getAllSupportedGradleVersionsByIdeaImpl().filter {
      isSupportedImpl(it, javaVersion)
    }
  }

  private fun getSupportedJavaVersionsImpl(gradleVersion: GradleVersion): List<JavaVersion> {
    return getAllSupportedJavaVersionsByIdeaImpl().filter {
      isSupportedImpl(gradleVersion, it)
    }
  }

  private fun suggestLatestGradleVersionImpl(javaVersion: JavaVersion): GradleVersion? {
    return getSupportedGradleVersionsImpl(javaVersion).lastOrNull()
  }

  private fun suggestLatestJavaVersionImpl(gradleVersion: GradleVersion): JavaVersion? {
    return getSupportedJavaVersionsImpl(gradleVersion).lastOrNull()
  }

  private fun suggestOldestCompatibleGradleVersionImpl(javaVersion: JavaVersion): GradleVersion? {
    return getSupportedGradleVersionsImpl(javaVersion).firstOrNull()
  }

  private fun suggestOldestCompatibleJavaVersionImpl(gradleVersion: GradleVersion): JavaVersion? {
    return getSupportedJavaVersionsImpl(gradleVersion).firstOrNull()
  }

  private fun getAllSupportedGradleVersionsByIdeaImpl(): List<GradleVersion> {
    return supportedGradleVersions
  }

  private fun getAllSupportedJavaVersionsByIdeaImpl(): List<JavaVersion> {
    return supportedJavaVersions
  }

  private fun getOldestSupportedGradleVersionByIdeaImpl(): GradleVersion {
    return getAllSupportedGradleVersionsByIdeaImpl().min()
  }

  private fun getLatestSupportedGradleVersionByIdeaImpl(): GradleVersion {
    return getAllSupportedGradleVersionsByIdeaImpl().max()
  }

  private fun getOldestNonDeprecatedGradleVersionByIdeaImpl(): GradleVersion {
    return GradleVersion.version("6.0")
  }

  private fun getRecommendedGradleVersionByIdeaImpl(): GradleVersion {
    return GradleVersion.current()
  }

  private fun getOldestSupportedJavaVersionByIdeaImpl(): JavaVersion {
    return getAllSupportedJavaVersionsByIdeaImpl().min()
  }

  @TestOnly
  fun resetState() {
    onStateChanged(newState())
  }

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

    /**
     * Checks that current Idea supports integration with any Gradle which executes on Java [javaVersion].
     */
    @JvmStatic
    fun isJavaSupportedByIdea(javaVersion: JavaVersion): Boolean {
      return getInstance().isJavaSupportedByIdeaImpl(javaVersion)
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

    fun getSupportedGradleVersions(javaVersion: JavaVersion): List<GradleVersion> {
      return getInstance().getSupportedGradleVersionsImpl(javaVersion)
    }

    fun getSupportedJavaVersions(gradleVersion: GradleVersion): List<JavaVersion> {
      return getInstance().getSupportedJavaVersionsImpl(gradleVersion)
    }

    fun suggestLatestSupportedGradleVersion(javaVersion: JavaVersion): GradleVersion? {
      return getInstance().suggestLatestGradleVersionImpl(javaVersion)
    }

    fun suggestLatestSupportedJavaVersion(gradleVersion: GradleVersion): JavaVersion? {
      return getInstance().suggestLatestJavaVersionImpl(gradleVersion)
    }

    fun suggestOldestSupportedGradleVersion(javaVersion: JavaVersion): GradleVersion? {
      return getInstance().suggestOldestCompatibleGradleVersionImpl(javaVersion)
    }

    fun suggestOldestSupportedJavaVersion(gradleVersion: GradleVersion): JavaVersion? {
      return getInstance().suggestOldestCompatibleJavaVersionImpl(gradleVersion)
    }

    /**
     * Returns sorted list (from min to max) of Gradle versions which supported by current Idea.
     */
    fun getAllSupportedGradleVersionsByIdea(): List<GradleVersion> {
      return getInstance().getAllSupportedGradleVersionsByIdeaImpl()
    }

    /**
     * Returns sorted list (from min to max) of Java versions which supported by current Idea.
     */
    fun getAllSupportedJavaVersionsByIdea(): List<JavaVersion> {
      return getInstance().getAllSupportedJavaVersionsByIdeaImpl()
    }

    fun getOldestSupportedGradleVersionByIdea(): GradleVersion {
      return getInstance().getOldestSupportedGradleVersionByIdeaImpl()
    }

    fun getLatestSupportedGradleVersionByIdea(): GradleVersion {
      return getInstance().getLatestSupportedGradleVersionByIdeaImpl()
    }

    fun getRecommendedGradleVersionByIdea(): GradleVersion {
      return getInstance().getRecommendedGradleVersionByIdeaImpl()
    }

    fun getOldestSupportedJavaVersionByIdea(): JavaVersion {
      return getInstance().getOldestSupportedJavaVersionByIdeaImpl()
    }
  }
}