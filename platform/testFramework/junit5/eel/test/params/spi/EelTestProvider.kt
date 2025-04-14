// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.spi

import com.intellij.platform.eel.EelApi
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.TestOnly
import java.io.Closeable
import kotlin.reflect.KClass

/**
 * Provides eel api for tests by registering itself using SPI.
 * To be used by `ij-dev-environments` team only.
 */
@TestOnly
interface EelTestProvider<T : Any> {
  val mandatoryAnnotationClass: KClass<T>?

  suspend fun start(scope: CoroutineScope, annotation: T?): StartResult

  sealed interface StartResult {
    data class Skipped(val skippedReason: String) : StartResult
    data class Started(val eel: EelApi, val closeable: Closeable? = null) : StartResult
  }
}