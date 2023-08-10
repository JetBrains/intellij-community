// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf

internal val calculatedCache = mutableMapOf<KProperty<*>, PropertyMetadata>()
internal data class PropertyMetadata(val returnTypeClass: Class<out WorkspaceEntity>, val isCollection: Boolean, val isNullable: Boolean)

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

  public operator fun setValue(thisRef: WorkspaceEntity, property: KProperty<*>, value: T?) {
    thisRef as ModifiableWorkspaceEntityBase<*, *>
    val entities = if (value is List<*>) value else listOf(value)
    thisRef.updateReferenceToEntity(property.returnTypeKClass.java, property.isChildProperty, entities as List<WorkspaceEntity?>)
  }

  private fun computeOrGetCachedMetadata(property: KProperty<*>): PropertyMetadata {
    val cachesMetadata = calculatedCache[property]
    if (cachesMetadata != null) return cachesMetadata
    val returnType = property.returnType
    val metadata = PropertyMetadata(property.returnTypeKClass.java, returnType.isCollection, returnType.isMarkedNullable)
    calculatedCache[property] = metadata
    return metadata
  }

  private val KProperty<*>.isChildProperty: Boolean
    get() {
      if (returnType.isCollection) return true
      return returnType.annotations.any { it.annotationClass == Child::class }
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

  private val KType.isCollection: Boolean
    get() = (classifier as KClass<*>).isSubclassOf(List::class)
}

