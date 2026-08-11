// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup.util

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.minutes

/**
 * Provides additional thread dump functionality for diagnostic purposes.
 */
@ApiStatus.Internal
interface AdditionalThreadDumpSupport {

  companion object {
    var EP_NAME: ExtensionPointName<AdditionalThreadDumpSupport> = ExtensionPointName("com.intellij.warmup.threadDumpSupport")
    suspend fun dumpThreads(): String {
      return buildString {
        for (support in EP_NAME.extensionList) {
          val dump = withTimeoutOrNull(1.minutes) {
            support.dumpThreads().orEmpty()
          } ?: "Timeout during dump collecting from provider ${support.name}"

          appendLine(dump)
        }
      }
    }
  }

  val name: @NlsSafe String
  suspend fun dumpThreads(): String?
}
