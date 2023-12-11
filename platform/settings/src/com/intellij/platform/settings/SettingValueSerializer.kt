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
  fun <T : Any> objectValueSerializer(aClass: Class<T>): SettingValueSerializer<T>

  fun <K : Any, V: Any?> mapSerializer(keyClass: Class<K>, valueClass: Class<V>): SettingValueSerializer<Map<K, V>>
}

@Internal
inline fun <reified T : Any> objectSettingValueSerializer(): SettingValueSerializer<T> {
  return ObjectSettingValueSerializerDelegate(T::class.java)
}

@PublishedApi
internal class ObjectSettingValueSerializerDelegate<T : Any>(private val aClass: Class<T>) : SettingValueSerializer<T> {
  // don't resolve service as a part of service descriptor creation
  private val impl by lazy(LazyThreadSafetyMode.NONE) {
    ApplicationManager.getApplication().getService(ObjectSettingValueSerializerFactory::class.java).objectValueSerializer(aClass)
  }

  override fun encode(value: T): ByteArray = impl.encode(value)

  override fun decode(input: ByteArray): T = impl.decode(input)
}

// todo hide as soon as will be coroutine scope service injection API
@Internal
class MapSettingValueSerializerDelegate<K : Any, V: Any?>(keyClass: Class<K>, valueClass: Class<V>) : SettingValueSerializer<Map<K, V>> {
  // don't resolve service as a part of service descriptor creation
  private val impl by lazy(LazyThreadSafetyMode.NONE) {
    ApplicationManager.getApplication().getService(ObjectSettingValueSerializerFactory::class.java).mapSerializer(keyClass, valueClass)
  }

  override fun encode(value: Map<K, V>): ByteArray = impl.encode(value)

  override fun decode(input: ByteArray): Map<K, V> = impl.decode(input)
}

// todo hide as soon as will be coroutine scope service injection API
object ByteArraySettingValueSerializer : SettingValueSerializer<ByteArray> {
  override fun encode(value: ByteArray): ByteArray = value

  override fun decode(input: ByteArray): ByteArray = input
}

@Suppress("ConvertObjectToDataObject")
object StringSettingValueSerializer : SettingValueSerializer<String> {
  override fun encode(value: String): ByteArray = value.encodeToByteArray()

  override fun decode(input: ByteArray): String = input.decodeToString()

  override fun toString(): String = "StringSettingValueSerializer"
}
