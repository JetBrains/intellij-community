// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.serialization

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

internal interface StorageInterner {
  fun intern(entityId: SerializableEntityId): SerializableEntityId

  fun intern(string: String): String

  fun intern(typeInfo: TypeInfo): TypeInfo
}

internal class StorageInternerImpl: StorageInterner {
  private val entityIdInterner = ConcurrentHashMap<SerializableEntityId, SerializableEntityId>()
  private val stringInterner = ConcurrentHashMap<String, String>()
  private val typeInfoInterner = ConcurrentHashMap<TypeInfo, TypeInfo>()

  override fun intern(entityId: SerializableEntityId): SerializableEntityId = entityIdInterner.computeIfAbsent(entityId, Function.identity())

  override fun intern(string: String): String = stringInterner.computeIfAbsent(string, Function.identity())

  override fun intern(typeInfo: TypeInfo): TypeInfo = typeInfoInterner.computeIfAbsent(typeInfo, Function.identity())
}