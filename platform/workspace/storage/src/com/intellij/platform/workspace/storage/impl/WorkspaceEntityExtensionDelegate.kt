// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf

internal val calculatedCache = mutableMapOf<KProperty<*>, PropertyMetadata>()
internal data class PropertyMetadata(val returnTypeClass: Class<out WorkspaceEntity>, val isCollection: Boolean, val isNullable: Boolean)

public class WorkspaceEntityExtensionDelegateMutable<T, W: WorkspaceEntity>(
  private val immutableClass: Class<W>,
) {
  public operator fun getValue(thisRef: WorkspaceEntity.Builder<*>, property: KProperty<*>): T {
    thisRef as ModifiableWorkspaceEntityBase<W, *>
    val builderSeq = thisRef.referrersBuilders(immutableClass, false)

    val res = if (property.returnType.isCollection) {
      builderSeq.toList()
    } else {
      if (property.returnType.isMarkedNullable) {
        builderSeq.singleOrNull()
      } else {
        builderSeq.single()
      }
    }
    return res as T
  }

  public operator fun setValue(thisRef: WorkspaceEntity.Builder<*>, property: KProperty<*>, value: T?) {
    thisRef as ModifiableWorkspaceEntityBase<*, *>
    val entities = if (value is List<*>) value else listOf(value)
    thisRef.updateReferenceToEntity(immutableClass, property.isChildProperty, entities as List<WorkspaceEntity.Builder<*>?>)
  }
}

public class WorkspaceEntityExtensionDelegate<T> {
  public operator fun getValue(thisRef: WorkspaceEntity, property: KProperty<*>): T {
    thisRef as WorkspaceEntityBase
    val propertyMetadata = computeOrGetCachedMetadata(property)
    val workspaceEntitySequence = thisRef.referrers(propertyMetadata.returnTypeClass)

    val result: Any? = if (propertyMetadata.isCollection) {
      workspaceEntitySequence.toList()
    }
    else {
      if (propertyMetadata.isNullable) {
        workspaceEntitySequence.singleOrNull()
      }
      else {
        workspaceEntitySequence.single()
      }
    }
    return result as T
  }

  private fun computeOrGetCachedMetadata(property: KProperty<*>): PropertyMetadata {
    val cachesMetadata = calculatedCache[property]
    if (cachesMetadata != null) return cachesMetadata
    val returnType = property.returnType
    val metadata = PropertyMetadata(property.returnTypeKClass.java, returnType.isCollection, returnType.isMarkedNullable)
    calculatedCache[property] = metadata
    return metadata
  }

  @Suppress("UNCHECKED_CAST")
  private val KProperty<*>.returnTypeKClass: KClass<out WorkspaceEntity>
    get() {
      return if (returnType.isCollection) {
        returnType.arguments.first().type?.classifier as KClass<out WorkspaceEntity>
      }
      else {
        returnType.classifier as KClass<out WorkspaceEntity>
      }
    }
}

private val KType.isCollection: Boolean
  get() = (classifier as KClass<*>).isSubclassOf(List::class)


private val KProperty<*>.isChildProperty: Boolean
  get() {
    if (returnType.isCollection) return true
    return annotations.none { it.annotationClass == Parent::class }
  }
