// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage

import java.nio.file.Path

public interface EntityStorageSerializer {
  public val serializerDataFormatVersion: String

  public fun serializeCache(file: Path, storage: EntityStorageSnapshot): SerializationResult

  public fun deserializeCache(file: Path): Result<MutableEntityStorage?>
}

public interface EntityTypesResolver {
  public fun getPluginId(clazz: Class<*>): String?
  public fun resolveClass(name: String, pluginId: String?): Class<*>
}

public sealed class SerializationResult {
  /**
   * [size] is the size in bytes
   */
  public class Success(public val size: Long) : SerializationResult()
  public class Fail<T>(public val info: T) : SerializationResult()
}

public sealed interface EntityInformation {
  public interface Serializer : EntityInformation {
    public fun saveInt(i: Int)
    public fun saveString(s: String)
    public fun saveBoolean(b: Boolean)
    public fun saveBlob(b: Any, javaSimpleName: String)
    public fun saveNull()
  }

  public interface Deserializer : EntityInformation {
    public fun readBoolean(): Boolean
    public fun readString(): String
    public fun readInt(): Int
    public fun acceptNull(): Boolean
  }
}

public interface SerializableEntityData {
  public fun serialize(ser: EntityInformation.Serializer)
  public fun deserialize(de: EntityInformation.Deserializer)
}
