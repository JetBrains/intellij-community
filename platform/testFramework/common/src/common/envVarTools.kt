// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.util.EnvironmentUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * Run [code] in environment with [envs] added i.e.
 * ```kotlin
 * withEnvVars("FOO" to "BAR") {...}
 * ```
 */
@TestOnly
inline fun withEnvVars(vararg envs: Pair<String, String>, code: () -> Unit) {
  withEnvVars(envs.toMap(), code)
}

/**
 * Run [code] in environment with [envs] added i.e.
 * ```kotlin
 * withEnvVars(mapOf("FOO" to "BAR")) {...}
 * ```
 */
@TestOnly
inline fun withEnvVars(additionalEnvs: Map<String, String>, code: () -> Unit) {
  val current = EnvironmentUtil.getEnvironmentMap()
  try {
    replaceEnvLoader(additionalEnvs, current)
    code()
  }
  finally {
    EnvironmentUtil.setEnvironmentLoader { current }
  }
}

// impl part, do not use

/**
 * Replaces current environment, adds [additionalEnvs] and returns previous env, which **must** be set
 * after the test using [EnvironmentUtil.setEnvironmentLoader].
 * [current] is [EnvironmentUtil.getEnvironmentMap]
 *
 * This method is for framework implementors, do not use it directly, use [withEnvVars] or Junit5 annotation
 */
@ApiStatus.Internal
fun replaceEnvLoader(additionalEnvs: Map<String, String>, current: Map<String, String>) {
  EnvironmentUtil.setEnvironmentLoader { EnvMapWrapper(additionalEnvs, current) }
}

/**
 * [envsFromUtil] ([EnvironmentUtil.getEnvironmentMap]) might be case insensitive or do some other magic,
 * so we use [userProvidedEnvs] and then delegate to it as a last resort
 */
private class EnvMapWrapper(
  private val userProvidedEnvs: Map<String, String>,
  private val envsFromUtil: Map<String, String>,
) : Map<String, String> by envsFromUtil + userProvidedEnvs {
  override fun get(key: String): String? =
    userProvidedEnvs[key] ?: envsFromUtil[key]

  override fun containsKey(key: String): Boolean = key in userProvidedEnvs || key in envsFromUtil
}