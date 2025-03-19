// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleUserHomeUtil")

package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.externalSystem.util.environment.Environment
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asNioPath
import org.gradle.internal.FileUtils
import org.gradle.internal.SystemProperties
import java.io.File
import java.nio.file.Path

private const val GRADLE_HOME_FOLDER_PATH = "/.gradle"
private const val GRADLE_USER_HOME_ENV_KEY = "GRADLE_USER_HOME"
private const val GRADLE_USER_HOME_PROPERTY_KEY = "gradle.user.home"
private val DEFAULT_GRADLE_USER_HOME = File(SystemProperties.getInstance().userHome + GRADLE_HOME_FOLDER_PATH)

fun gradleUserHomeDir(): File {
  var gradleUserHome = Environment.getProperty(GRADLE_USER_HOME_PROPERTY_KEY)
  if (gradleUserHome == null) {
    gradleUserHome = Environment.getVariable(GRADLE_USER_HOME_ENV_KEY)
  }

  return FileUtils.canonicalize(File(gradleUserHome ?: DEFAULT_GRADLE_USER_HOME.absolutePath))
}

fun gradleUserHomeDir(descriptor: EelDescriptor): Path {
  if (descriptor == LocalEelDescriptor) {
    return gradleUserHomeDir().toPath()
  }
  return runBlockingMaybeCancellable {
    val eel = descriptor.upgrade()
    val env = eel.exec.fetchLoginShellEnvVariables()
    val gradleUserHome = env[GRADLE_USER_HOME_PROPERTY_KEY] ?: env[GRADLE_USER_HOME_ENV_KEY]
    if (gradleUserHome != null) {
      val nioUserHome = gradleUserHome.toNioPathOrNull()
      if (nioUserHome != null) {
        return@runBlockingMaybeCancellable nioUserHome
      }
    }
    return@runBlockingMaybeCancellable eel.userInfo.home.asNioPath().resolve(".gradle")
  }
}