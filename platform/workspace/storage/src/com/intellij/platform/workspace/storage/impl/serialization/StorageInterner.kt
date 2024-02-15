// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.serialization

import com.intellij.util.containers.Interner

internal interface StorageInterner {
  fun intern(entityId: SerializableEntityId): SerializableEntityId

  fun intern(string: String): String

  fun intern(typeInfo: TypeInfo): TypeInfo
}

internal class StorageInternerImpl: StorageInterner {
  private val entityIdInterner: Interner<SerializableEntityId> = Interner.createInterner()
  private val stringInterner: Interner<String> = Interner.createInterner()
  private val typeInfoInterner: Interner<TypeInfo> = Interner.createInterner()

  override fun intern(entityId: SerializableEntityId): SerializableEntityId = entityIdInterner.intern(entityId)

  override fun intern(string: String): String = stringInterner.intern(string)

  override fun intern(typeInfo: TypeInfo): TypeInfo = typeInfoInterner.intern(typeInfo)
}