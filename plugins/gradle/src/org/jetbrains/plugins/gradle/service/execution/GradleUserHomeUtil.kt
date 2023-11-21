// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleUserHomeUtil")

package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.externalSystem.util.environment.Environment
import org.gradle.internal.FileUtils
import org.gradle.internal.SystemProperties
import java.io.File

private val DEFAULT_GRADLE_USER_HOME = File(SystemProperties.getInstance().userHome + "/.gradle")

fun gradleUserHomeDir(): File {
  var gradleUserHome = Environment.getProperty("gradle.user.home")
  if (gradleUserHome == null) {
    gradleUserHome = Environment.getVariable("GRADLE_USER_HOME")
  }

  return FileUtils.canonicalize(File(gradleUserHome ?: DEFAULT_GRADLE_USER_HOME.absolutePath))
}