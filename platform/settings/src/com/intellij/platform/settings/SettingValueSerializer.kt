// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings

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

@Internal
inline fun <reified T : Any> SettingDescriptorFactory.objectSerializer(): SettingValueSerializer<T> {
  return objectSerializer(T::class.java)
}

inline fun <reified K : Any, reified V : Any?> SettingDescriptorFactory.mapSerializer(): SettingValueSerializer<Map<K, V>> {
  return mapSerializer(K::class.java, V::class.java)
}

/**
 * Use [SettingDescriptorFactory.objectSerializer] and [SettingDescriptorFactory.mapSerializer] instead.
 */
@Internal
interface ObjectSettingValueSerializerFactory {
  //todo how to inform client that class expected to be kotlinx serializable
  fun <T : Any> objectSerializer(aClass: Class<T>): SettingValueSerializer<T>

  fun <K : Any, V: Any?> mapSerializer(keyClass: Class<K>, valueClass: Class<V>): SettingValueSerializer<Map<K, V>>
}

// todo hide as soon as will be coroutine scope service injection API
object ByteArraySettingValueSerializer : SettingValueSerializer<ByteArray> {
  override fun encode(value: ByteArray): ByteArray = value

  override fun decode(input: ByteArray): ByteArray = input
}