// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.util.lang.JavaVersion
import fleet.util.multiplatform.Actual
import org.jetbrains.annotations.ApiStatus

private var current: JavaVersion? = null

/**
 * actual for [com.intellij.util.currentJavaVersionPlatformSpecific]
 */
@Actual("currentJavaVersionPlatformSpecific")
@ApiStatus.Internal
fun currentJavaVersionPlatformSpecificJvm(): JavaVersion {
  current?.let { return it }

  val fallback = JavaVersion.parse(System.getProperty("java.version"))
  val rt = rtVersion() ?: try {
    JavaVersion.parse(System.getProperty("java.runtime.version"))
  }
  catch (_: Throwable) {
    null
  }
  val version = rt.takeIf { rt != null && rt.feature == fallback.feature && rt.minor == fallback.minor } ?: fallback
  return version.also { current = it }
}

/**
 * Attempts to use Runtime.version() method available since Java 9.
 */
// @ReviseWhenPortedToJDK("9") TODO make ReviseWhenPortedToJDK kmp-compatible
private fun rtVersion(): JavaVersion? {
  try {
    val version = Runtime::class.java.getMethod("version").invoke(null)
    val major = version.javaClass.getMethod("major").invoke(version) as Int
    val minor = version.javaClass.getMethod("minor").invoke(version) as Int
    val security = version.javaClass.getMethod("security").invoke(version) as Int
    val buildOpt = version.javaClass.getMethod("build").invoke(version)
    val build = buildOpt.javaClass.getMethod("orElse", Any::class.java).invoke(buildOpt, 0) as Int
    val preOpt = version.javaClass.getMethod("pre").invoke(version)
    val ea = preOpt.javaClass.getMethod("isPresent").invoke(preOpt) as Boolean
    return JavaVersion.compose(major, minor, security, build, ea)
  }
  catch (_: Throwable) {
    return null
  }
}
