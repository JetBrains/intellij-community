// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jsonDump

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KVisibility

internal fun EntityStorage.allUniqueEntities() = entitiesBySource { true }.distinctBy { it.javaClass }

internal fun Sequence<WorkspaceEntity>.rootEntitiesClasses() = mapNotNull { entity ->
  val entityMeta = (entity as WorkspaceEntityBase).getData().getMetadata()
  val hasParent = entityMeta.properties.any {
    val propertyType = it.valueType
    propertyType is ValueTypeMetadata.EntityReference && !propertyType.isChild
  }
  if (!hasParent) entity.getEntityInterface() else null
}

internal fun Any.getPropertyValue(propertyName: String): Any? {
  val propertyAccessor = this::class.members.find { it.name == propertyName }
                         ?: error("Cannot find property $propertyName in ${this::class.qualifiedName}")
  if (propertyAccessor.visibility != KVisibility.PUBLIC) return null
  return propertyAccessor.call(this)
}

@Suppress("UNCHECKED_CAST")
internal fun ValueTypeMetadata.SimpleType.PrimitiveType.serializer(): KSerializer<Any> = when (type) {
  "String" -> String.serializer()
  "Boolean" -> Boolean.serializer()
  "Int" -> Int.serializer()
  "Byte" -> Byte.serializer()
  "Short" -> Short.serializer()
  "Long" -> Long.serializer()
  "Float" -> Float.serializer()
  "Double" -> Double.serializer()
  "Char" -> Char.serializer()
  else -> error("Attempt to get primitive serializer for unsupported type $type")
} as KSerializer<Any>

@ApiStatus.Internal
fun ValueTypeMetadata.ParameterizedType.genericParameterForList(): ValueTypeMetadata.SimpleType? {
  if (generics.size != 1) return null
  val thePrimitive = primitive
  if (thePrimitive !is ValueTypeMetadata.SimpleType.PrimitiveType || thePrimitive.type != "List") return null
  val genericType = generics.single()
  if (genericType !is ValueTypeMetadata.SimpleType) return null
  return genericType
}
