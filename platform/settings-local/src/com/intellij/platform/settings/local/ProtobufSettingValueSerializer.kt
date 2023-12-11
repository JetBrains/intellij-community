// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.platform.settings.local

import com.intellij.platform.settings.ObjectSettingValueSerializerFactory
import com.intellij.platform.settings.SettingValueSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer

private class ProtobufObjectSettingValueSerializerFactory : ObjectSettingValueSerializerFactory {
  override fun <T : Any> objectValueSerializer(aClass: Class<T>): SettingValueSerializer<T> {
    @Suppress("UNCHECKED_CAST")
    return ProtobufSettingValueSerializer(serializer(aClass) as KSerializer<T>)
  }

  override fun <K : Any, V> mapSerializer(keyClass: Class<K>, valueClass: Class<V>): SettingValueSerializer<Map<K, V>> {
    @Suppress("UNCHECKED_CAST")
    return ProtobufSettingValueSerializer(MapSerializer(serializer(keyClass), serializer(valueClass)) as KSerializer<Map<K, V>>)
  }
}

private class ProtobufSettingValueSerializer<T : Any>(private val serializer: KSerializer<T>) : SettingValueSerializer<T> {
  override fun encode(value: T): ByteArray {
    return ProtoBuf.encodeToByteArray(serializer, value)
  }

  override fun decode(input: ByteArray): T {
    return ProtoBuf.decodeFromByteArray(serializer, input)
  }
}