// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.serialization

import com.intellij.platform.workspace.storage.EntityTypesResolver


internal typealias PluginId = String?

internal data class TypeInfo(val fqName: String, val pluginId: PluginId)

internal data class SerializableEntityId(val arrayId: Int, val type: TypeInfo)

internal fun getTypeInfo(clazz: Class<*>, interner: StorageInterner, typesResolver: EntityTypesResolver): TypeInfo =
  interner.intern(TypeInfo(clazz.name, typesResolver.getPluginId(clazz)))