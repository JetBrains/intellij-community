// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.*
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.util.*

@State(name = "GradleJvmSupportMatrix", storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE)])
class GradleJvmSupportMatrix : PersistentStateComponent<JvmCompatibilityState?> {
  private lateinit var mySupportedGradleVersions: List<GradleVersion>
  private lateinit var mySupportedJavaVersions: List<JavaVersion>
  private var myState: JvmCompatibilityState? = null
  private lateinit var myCompatibility: List<Pair<Ranges<JavaVersion>, Ranges<GradleVersion>>>
  override fun getState(): JvmCompatibilityState? {
    return myState
  }

  override fun loadState(state: JvmCompatibilityState) {
    if (state.isDefault || state.ideVersion != ApplicationInfo.getInstance().fullVersion) {
      myState = null
    }
    else {
      myState = state
    }
  }

  private fun parseMyState() {
    val data = state?.data ?: DEFAULT_DATA
    myCompatibility = CompatibilityDataParser(ApplicationInfo.getInstance().fullVersion).getCompatibilityRanges(data)
    mySupportedGradleVersions = data.supportedGradleVersions.map(GradleVersion::version)
    mySupportedJavaVersions = data.supportedJavaVersions.map(JavaVersion::parse)
  }

  override fun noStateLoaded() {
    parseMyState()
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

  fun setStateAsString(json: String) {
    val parser = CompatibilityDataParser(ApplicationInfo.getInstance().fullVersion)
    val compatibilityData = parser.parseJson(json)
    if (compatibilityData != null) {
      val newState = JvmCompatibilityState()
      newState.data = compatibilityData
      newState.isDefault = false
      newState.ideVersion = ApplicationInfo.getInstance().fullVersion
      newState.lastUpdateTime = System.currentTimeMillis()
      myState = newState
      parseMyState()
    }
  }


  companion object {
    @JvmStatic
    fun getInstance(): GradleJvmSupportMatrix {
      return service<GradleJvmSupportMatrix>()
    }
  }
}