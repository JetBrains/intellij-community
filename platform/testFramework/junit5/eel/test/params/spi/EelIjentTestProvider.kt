// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.spi

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.platform.ijent.IjentApi
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.TestOnly
import java.io.Closeable
import kotlin.reflect.KClass

/**
 * Provides eel api for tests by registering itself using SPI.
 * To be used by `ij-dev-environments` team only.
 */
@TestOnly
interface EelIjentTestProvider<T : Annotation> {
  val name: String
  val mandatoryAnnotationClass: KClass<T>

  suspend fun start(scope: CoroutineScope, annotation: T?): StartResult

  fun annotationToUserVisibleString(annotation: T): String
  fun isMandatory(annotation: T): Boolean

  sealed interface StartResult {
    data class Skipped(val skippedReason: String) : StartResult
    data class Started(val eel: IjentApi, val target: TargetEnvironmentConfiguration, val closeable: Closeable? = null) : StartResult
  }
}