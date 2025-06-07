// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings

import com.intellij.openapi.components.ComponentManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.xmlb.SettingsInternalApi
import kotlinx.serialization.json.JsonElement
import org.jetbrains.annotations.ApiStatus.*
import java.nio.file.Path

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
  fun createStateStorage(collapsedPath: String, file: Path): Any?

  @Internal
  fun createChild(container: ComponentManager): SettingsController?

  @Internal
  @SettingsInternalApi
  fun release()

  @Internal
  @SettingsInternalApi
  fun <T : Any> doGetItem(key: SettingDescriptor<T>): GetResult<T?>

  @Internal
  @SettingsInternalApi
  fun <T : Any> doSetItem(key: SettingDescriptor<T>, value: T?): SetResult

  @Internal
  @SettingsInternalApi
  fun isPersistenceStateComponentProxy(): Boolean
}

@Internal
@JvmInline
value class GetResult<out T : Any?> @PublishedApi internal constructor(internal val value: Any?) {
  companion object {
    fun <T : Any> resolved(value: T?): GetResult<T?> = GetResult(value)

    fun <T> inapplicable(): GetResult<T> = GetResult(Inapplicable)
  }

  val isResolved: Boolean
    get() = value !is Inapplicable

  @Suppress("UNCHECKED_CAST")
  fun get(): T? = if (value is Inapplicable) null else value as T

  override fun toString(): String = "GetResult($value)"

  private object Inapplicable
}

@Internal
@JvmInline
value class SetResult @PublishedApi internal constructor(@Internal @SettingsInternalApi val value: Any?) {
  companion object {
    fun inapplicable(): SetResult = SetResult(SetResultResolution.INAPPLICABLE)

    fun forbid(): SetResult = SetResult(SetResultResolution.FORBID)

    fun done(): SetResult = SetResult(SetResultResolution.DONE)

    fun substituted(value: JsonElement): SetResult = SetResult(value)
  }

  @OptIn(SettingsInternalApi::class)
  override fun toString(): String = "SetResult($value)"
}

private enum class SetResultResolution {
  /**
   * Indicates that the controller can't be used to set this particular setting.
   */
  INAPPLICABLE,

  /**
   * Indicates that the setting was set successfully.
   */
  DONE,
  /**
   * Indicates that the attempted setting is read-only, therefore it can't be set.
   */
  FORBID
}

/**
 * Caveat: do not touch telemetry API during init, it is not ready yet.
 */
@Internal
interface DelegatedSettingsController {
  fun <T : Any> getItem(key: SettingDescriptor<T>): GetResult<T?>

  fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?): SetResult

  fun createChild(container: ComponentManager): DelegatedSettingsController? = null

  /**
   * Called when the configuration store is closed, which occurs prior to a full disposal of the service container.
   */
  fun close() {
  }
}

class ReadOnlySettingException(val key: SettingDescriptor<*>) : IllegalStateException()