// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
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
    parseMyState()
  }

  private fun parseMyState() {
    val data = state?.data ?: DEFAULT_DATA
    myCompatibility = CompatibilityDataParser(ApplicationInfo.getInstance().fullVersion).getCompatibilityRanges(data);
    mySupportedGradleVersions = data.supportedGradleVersions.map(GradleVersion::version)
    mySupportedJavaVersions = data.supportedJavaVersions.map(JavaVersion::parse)
  }

  override fun noStateLoaded() {
    parseMyState()
  }

  fun isSupported(gradleVersion: GradleVersion, javaVersion: JavaVersion): Boolean {
    return myCompatibility.any { (javaVersions, gradleVersions) ->
      javaVersion in javaVersions && gradleVersion in gradleVersions
    }
  }

  fun suggestGradleVersion(javaVersion: JavaVersion): GradleVersion? {
    val gradleVersion = GradleVersion.current()
    if (isSupported(gradleVersion, javaVersion)) {
      return gradleVersion
    }
    return mySupportedGradleVersions.reversed().find { isSupported(it, javaVersion) }
  }

  fun suggestJavaVersion(gradleVersion: GradleVersion): JavaVersion? {
    return mySupportedJavaVersions.reversed().find { isSupported(gradleVersion, it) }
  }

  fun suggestOldestCompatibleGradleVersion(javaVersion: JavaVersion): GradleVersion? {
    return mySupportedGradleVersions.find { isSupported(it, javaVersion) }
  }

  fun suggestOldestCompatibleJavaVersion(gradleVersion: GradleVersion): JavaVersion? {
    return mySupportedJavaVersions.find { isSupported(gradleVersion, it) }
  }

  fun minSupportedJava(): JavaVersion {
    return mySupportedJavaVersions.first()
  }

  fun maxSupportedJava(): JavaVersion {
    return mySupportedJavaVersions.last()
  }


  companion object {
    @JvmStatic
    @get:JvmName("getInstance")
    val INSTANCE: GradleJvmSupportMatrix
      get() = ApplicationManager.getApplication().getService(GradleJvmSupportMatrix::class.java)
  }
}