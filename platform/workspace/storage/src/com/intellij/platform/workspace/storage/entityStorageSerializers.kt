// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Obsolete
import java.nio.file.Path

public interface EntityStorageSerializer {
  public val serializerDataFormatVersion: String

  public fun serializeCache(file: Path, storage: ImmutableEntityStorage): SerializationResult

  public fun deserializeCache(file: Path): Result<MutableEntityStorage?>
}

@ApiStatus.Internal
public interface EntityTypesResolver {
  public fun getPluginIdAndModuleId(clazz: Class<*>): Pair<String?, String?>
  public fun resolveClass(name: String, pluginId: String?, moduleId: String?): Class<*>

  /**
   * Method is used to register collections from the kotlin plugin in kryo.
   *
   * Collections inside the kotlin entities are loaded by another class loader.
   *
   * This is temporary solution until the kotlin plugin uses the same version of kotlin-stdlib as the intellij-platform.
   * In addition, plugin developers are not recommended to use a different version of kotlin-stdlib than the version for intellij-platform.
   */
  @Obsolete
  public fun getClassLoader(pluginId: String?, moduleId: String?): ClassLoader?
}

public sealed class SerializationResult {
  /**
   * [size] is the size in bytes
   */
  public class Success(public val size: Long) : SerializationResult()
  public class Fail(public val problem: Throwable) : SerializationResult()
}
