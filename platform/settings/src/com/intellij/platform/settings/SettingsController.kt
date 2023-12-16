// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings

import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus.*
import java.util.*

@NonExtendable
interface SettingsController {
  @RequiresBackgroundThread(generateAssertion = false)
  fun <T : Any> getItem(key: SettingDescriptor<T>): T?

  /**
   * The read-write policy cannot be enforced at compile time.
   * The actual controller implementation may rely on runtime rules. Therefore, handle [ReadOnlySettingException] where necessary.
   */
  @Throws(ReadOnlySettingException::class)
  suspend fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?)

  @Internal
  fun createStateStorage(collapsedPath: String): Any?
}

/**
 * Caveat: do not touch telemetry API during init, it is not ready yet.
 */
@Internal
interface ChainedSettingsController {
  fun <T : Any> getItem(key: SettingDescriptor<T>, chain: List<ChainedSettingsController>): T?

  @Throws(ReadOnlySettingException::class)
  suspend fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?, chain: List<ChainedSettingsController>)
}

class ReadOnlySettingException(val key: SettingDescriptor<*>) : IllegalStateException()