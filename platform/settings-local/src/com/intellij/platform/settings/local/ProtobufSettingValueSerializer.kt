// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.platform.settings.local

import com.intellij.platform.settings.ObjectSettingValueSerializerFactory
import com.intellij.platform.settings.SettingValueSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer

private class ProtobufObjectSettingValueSerializerFactory : ObjectSettingValueSerializerFactory {
  override fun <T : Any> protobufSettingValueSerializer(aClass: Class<T>): SettingValueSerializer<T> {
    return ProtobufSettingValueSerializer(aClass)
  }
}

private class ProtobufSettingValueSerializer<T : Any>(private val aClass: Class<T>) : SettingValueSerializer<T> {
  override fun encode(value: T): ByteArray {
    return ProtoBuf.encodeToByteArray(serializer(aClass), value)
  }

  override fun decode(input: ByteArray): T {
    @Suppress("UNCHECKED_CAST")
    return ProtoBuf.decodeFromByteArray(serializer(aClass), input) as T
  }
}