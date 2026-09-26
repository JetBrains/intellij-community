// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleUserHomeUtil")

package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.externalSystem.util.environment.Environment
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.toEelApi
import org.gradle.internal.FileUtils
import org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_CACHE_DIR_NAME
import org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_USER_HOME_ENV_KEY
import org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_USER_HOME_PROPERTY_KEY
import org.jetbrains.plugins.gradle.util.GradleConstants.USER_HOME_PROPERTY_KEY
import java.io.File
import java.nio.file.Path

@Deprecated("Use GradleUserHomeUtil#gradleUserHomeDir(EelDescriptor) instead")
fun gradleUserHomeDir(): File {
  var gradleUserHome = Environment.getProperty(GRADLE_USER_HOME_PROPERTY_KEY)
  if (gradleUserHome == null) {
    gradleUserHome = Environment.getVariable(GRADLE_USER_HOME_ENV_KEY)
  }
  if (gradleUserHome == null) {
    val userHome = Environment.getProperty(USER_HOME_PROPERTY_KEY)
    gradleUserHome = "$userHome/$GRADLE_CACHE_DIR_NAME"
  }
  return FileUtils.canonicalize(File(gradleUserHome))
}

fun gradleUserHomeDir(descriptor: EelDescriptor): Path {
  if (descriptor == LocalEelDescriptor) {
    return gradleUserHomeDirLocalFS()
  }
  return runBlockingMaybeCancellable {
    val eel = descriptor.toEelApi()
    val env = eel.exec.fetchLoginShellEnvVariables()
    val gradleUserHome = env[GRADLE_USER_HOME_PROPERTY_KEY] ?: env[GRADLE_USER_HOME_ENV_KEY]
    if (gradleUserHome != null) {
      val nioUserHome = gradleUserHome.toNioPathOrNull()
      if (nioUserHome != null) {
        return@runBlockingMaybeCancellable nioUserHome
      }
    }
    return@runBlockingMaybeCancellable eel.userInfo.home.asNioPath().resolve(GRADLE_CACHE_DIR_NAME)
  }
}

private fun gradleUserHomeDirLocalFS(): Path {
  val gradleUserHome = Environment.getProperty(GRADLE_USER_HOME_PROPERTY_KEY) ?: Environment.getVariable(GRADLE_USER_HOME_ENV_KEY)
  if (gradleUserHome != null) {
    val nioUserHome = gradleUserHome.toNioPathOrNull()
    if (nioUserHome != null) {
      return nioUserHome.normalize()
    }
  }
  val userHome = checkNotNull(Environment.getProperty(USER_HOME_PROPERTY_KEY)) { "Environment variable 'user.home' is not set" }
  return Path.of(userHome, GRADLE_CACHE_DIR_NAME).normalize()
}