// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings

import com.intellij.openapi.util.IntellijInternalApi
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
  @RequiresBackgroundThread(generateAssertion = false)
  fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?)

  @Internal
  @IntellijInternalApi
  fun createStateStorage(collapsedPath: String): Any?
}

@JvmInline
value class GetResult<out T : Any?> @PublishedApi internal constructor(@PublishedApi internal val value: Any?) {
  val isResolved: Boolean
    get() = value !is Inapplicable

  @Suppress("UNCHECKED_CAST")
  fun get(): T? = if (value is Inapplicable) null else value as T

  override fun toString(): String = if (value is Inapplicable) "Inapplicable" else "Applicable($value)"

  companion object {
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("success")
    fun <T> resolved(value: T?): GetResult<T> = GetResult(value)

    @JvmName("failure")
    fun <T : Any> inapplicable(): GetResult<T> = GetResult(Inapplicable)
  }

  internal object Inapplicable
}

/**
 * Caveat: do not touch telemetry API during init, it is not ready yet.
 */
@Internal
interface DelegatedSettingsController {
  /**
   * `null` if not applicable.
   *
   */
  fun <T : Any> getItem(key: SettingDescriptor<T>): GetResult<T?>

  /**
   * Return `true` to continue calling of `setItem` on other settings controllers or `false` to stop.
   *
   * Throw [ReadOnlySettingException] if setting must be not modified.
   */
  @Throws(ReadOnlySettingException::class)
  fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?): Boolean

  /**
   * Return `null` if unknown (controller is not applicable for setting).
   */
  @IntellijInternalApi
  fun <T : Any> hasKeyStartsWith(key: SettingDescriptor<T>): Boolean?
}

class ReadOnlySettingException(val key: SettingDescriptor<*>) : IllegalStateException()