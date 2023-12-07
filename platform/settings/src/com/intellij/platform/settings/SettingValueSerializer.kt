// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.NonExtendable

/**
 * Please note that backward compatibility is not guaranteed for this API.
 *
 * You **must not** implement this interface.
 */
// Implementation note: not intended to have custom serializers - only String and kotlinx Serializable (protobuf).
// `sealed` cannot be used - protobuf located in another module.
@NonExtendable
interface SettingValueSerializer<T : Any> {
  fun encode(value: T): ByteArray

  fun decode(input: ByteArray): T
}

//todo quite unclear and confusing API
interface ObjectSettingValueSerializerFactory {
  //todo should we rename it to "objectSettingValueSerializer" or "binarySettingValueSerializer"?
  //todo how to inform client that class expected to be kotlinx serializable
  fun <T : Any> protobufSettingValueSerializer(aClass: Class<T>): SettingValueSerializer<T>
}

@Internal
inline fun <reified T : Any> objectSettingValueSerializer(): SettingValueSerializer<T> {
  return ObjectSettingValueSerializerDelegate(T::class.java)
}

@PublishedApi
internal class ObjectSettingValueSerializerDelegate<T : Any>(private val aClass: Class<T>) : SettingValueSerializer<T> {
  // don't resolve service as a part of service descriptor creation
  private val impl by lazy(LazyThreadSafetyMode.NONE) {
    ApplicationManager.getApplication().getService(ObjectSettingValueSerializerFactory::class.java).protobufSettingValueSerializer(aClass)
  }

  override fun encode(value: T): ByteArray = impl.encode(value)

  override fun decode(input: ByteArray): T = impl.decode(input)
}

@Suppress("ConvertObjectToDataObject")
object StringSettingValueSerializer : SettingValueSerializer<String> {
  override fun encode(value: String): ByteArray = value.encodeToByteArray()

  override fun decode(input: ByteArray): String = input.decodeToString()

  override fun toString(): String = "StringSettingValueSerializer"
}
