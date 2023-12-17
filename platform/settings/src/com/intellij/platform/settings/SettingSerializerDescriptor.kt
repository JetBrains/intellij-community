// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings

import kotlinx.serialization.KSerializer
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.NonExtendable

/**
 * Please note that backward compatibility is not guaranteed for this API.
 *
 * You **must not** implement this interface.
 */
// Implementation note: not intended to have custom serializers - only String and kotlinx Serializable (CBOR).
@NonExtendable
sealed interface SettingSerializerDescriptor<T : Any>

inline fun <reified T : Any> SettingDescriptorFactory.objectSerializer(): SettingSerializerDescriptor<T> {
  return objectSerializer(T::class.java)
}

inline fun <reified K : Any, reified V : Any?> SettingDescriptorFactory.mapSerializer(): SettingSerializerDescriptor<Map<K, V>> {
  return mapSerializer(K::class.java, V::class.java)
}

@Internal
object RawSettingSerializerDescriptor : SettingSerializerDescriptor<ByteArray>, SettingValueSerializer<ByteArray> {
  override val serializer: KSerializer<ByteArray>
    get() = throw UnsupportedOperationException()
}
