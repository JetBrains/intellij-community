// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.api

import com.intellij.execution.target.EelTargetEnvironmentRequest
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.LocalEelApi
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * Accept as an argument of your test, be sure to use [EelSource]
 * See [TestApplicationWithEel]
 */
@TestOnly
@ApiStatus.NonExtendable
sealed interface EelHolder {
  val eel: EelApi

  /**
   * For those rare cases when you need to test something WSL or Docker-specific. Do not use unless absolutely necessary.
   * More-or-less legal usage is
   * ```kotlin
   * when (type) {
   *   EelType.Docker -> throw TestAbortedException("skip test for docker")
   *   EelType.Wsl, EelType.Local -> Unit
   * }
   * ```
   */
  val type: EelType
}

val EelHolder.target: TargetEnvironmentConfiguration get() = EelTargetEnvironmentRequest.Configuration(eel)