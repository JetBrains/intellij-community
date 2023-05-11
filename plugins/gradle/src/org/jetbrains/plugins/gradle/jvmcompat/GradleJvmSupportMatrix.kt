// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.util.Ranges

@State(name = "GradleJvmSupportMatrix", storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE)])
class GradleJvmSupportMatrix : IdeVersionedDataStorage<GradleCompatibilityState>(
  parser = GradleCompatibilityDataParser,
  defaultState = DEFAULT_DATA
) {
  private lateinit var mySupportedGradleVersions: List<GradleVersion>
  private lateinit var mySupportedJavaVersions: List<JavaVersion>
  private lateinit var myCompatibility: List<Pair<Ranges<JavaVersion>, Ranges<GradleVersion>>>

  override fun newState(): GradleCompatibilityState = GradleCompatibilityState()

  private fun getCompatibilityRanges(data: GradleCompatibilityState): List<Pair<Ranges<JavaVersion>, Ranges<GradleVersion>>> {
    return data.versionMappings.map { entry ->
      val gradleVersionInfo = entry.gradleVersionInfo ?: ""
      val javaVersionInfo = entry.javaVersionInfo ?: ""
      val gradleRange = IdeVersionedDataParser.parseRange(gradleVersionInfo.split(','), GradleVersion::version)
      val javaRange = IdeVersionedDataParser.parseRange(javaVersionInfo.split(','), JavaVersion::parse)
      javaRange to gradleRange
    }
  }

  override fun onStateChanged(newState: GradleCompatibilityState) {
    myCompatibility = getCompatibilityRanges(newState)
    mySupportedGradleVersions = newState.supportedGradleVersions.map(GradleVersion::version)
    mySupportedJavaVersions = newState.supportedJavaVersions.map(JavaVersion::parse)
  }

  fun getAllSupportedGradleVersions(): List<GradleVersion> {
    return mySupportedGradleVersions
  }

  fun getAllSupportedJavaVersions(): List<JavaVersion> {
    return mySupportedJavaVersions
  }

  fun isSupported(gradleVersion: GradleVersion, javaVersion: JavaVersion): Boolean {
    return myCompatibility.any { (javaVersions, gradleVersions) ->
      javaVersion in javaVersions && gradleVersion in gradleVersions
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): GradleJvmSupportMatrix {
      return service<GradleJvmSupportMatrix>()
    }
  }
}