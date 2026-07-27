// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jsonDump

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KVisibility

internal fun EntityStorage.rootEntitiesClassesList() = allUniqueClassEntities().rootEntitiesClasses().toList().sortedBy { it.simpleName }

private fun EntityStorage.allUniqueClassEntities() = entitiesBySource { true }.distinctBy { it.javaClass }

private fun Sequence<WorkspaceEntity>.rootEntitiesClasses() = mapNotNull { entity ->
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

@ApiStatus.Internal
fun ValueTypeMetadata.ParameterizedType.genericParameterForList(): ValueTypeMetadata? {
  if (generics.size != 1) return null
  val thePrimitive = primitive
  if (thePrimitive !is ValueTypeMetadata.SimpleType.PrimitiveType || (thePrimitive.type != "List" && thePrimitive.type != "Set")) return null
  val genericType = generics.single()
  return genericType
}

@ApiStatus.Internal
fun entityChildReferenceJsonName(entityChildClassName: String, multiple: Boolean = false): String {
  return if (multiple) "Children_$entityChildClassName" else "Child_$entityChildClassName"
}
