// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import java.io.InputStream
import java.io.OutputStream

interface EntityStorageSerializer {
  val serializerDataFormatVersion: String

  fun serializeCache(stream: OutputStream, storage: EntityStorageSnapshot): SerializationResult
  fun deserializeCache(stream: InputStream): Result<MutableEntityStorage?>
}

interface EntityTypesResolver {
  fun getPluginId(clazz: Class<*>): String?
  fun resolveClass(name: String, pluginId: String?): Class<*>
}

sealed class SerializationResult {
  object Success : SerializationResult()
  class Fail<T>(val info: T) : SerializationResult()
}

sealed interface EntityInformation {
  interface Serializer : EntityInformation {
    fun saveInt(i: Int)
    fun saveString(s: String)
    fun saveBoolean(b: Boolean)
    fun saveBlob(b: Any, javaSimpleName: String)
    fun saveNull()
  }

  interface Deserializer : EntityInformation {
    fun readBoolean(): Boolean
    fun readString(): String
    fun readInt(): Int
    fun acceptNull(): Boolean
  }
}

interface SerializableEntityData {
  fun serialize(ser: EntityInformation.Serializer)
  fun deserialize(de: EntityInformation.Deserializer)
}
