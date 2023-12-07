// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings

import java.util.*

interface SettingsController {
  suspend fun <T : Any> getItem(key: SettingDescriptor<T>): T?

  /**
   * The read-write policy cannot be enforced at compile time.
   * The actual controller implementation may rely on runtime rules. Therefore, handle [ReadOnlySettingException] where necessary.
   */
  @Throws(ReadOnlySettingException::class)
  suspend fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?)
}

class ReadOnlySettingException(val key: SettingDescriptor<*>) : IllegalStateException()