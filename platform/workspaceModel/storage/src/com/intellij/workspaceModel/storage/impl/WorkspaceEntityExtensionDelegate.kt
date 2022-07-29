// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.impl

import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.annotations.Child
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.extensionReceiverParameter
import kotlin.reflect.full.isSubclassOf

class WorkspaceEntityExtensionDelegate<T> {
  operator fun getValue(thisRef: WorkspaceEntity, property: KProperty<*>): T {
    thisRef as WorkspaceEntityBase
    val workspaceEntitySequence = thisRef.referrers(property.returnTypeKClass.java, property.findTargetField(thisRef).name)

    val returnType = property.returnType
    val result: Any? = if (returnType.isCollection) {
      workspaceEntitySequence.toList()
    }
    else {
      if (returnType.isMarkedNullable) {
        workspaceEntitySequence.singleOrNull()
      }
      else {
        workspaceEntitySequence.single()
      }
    }
    return result as T
  }

  operator fun setValue(thisRef: WorkspaceEntity, property: KProperty<*>, value: T?) {
    thisRef as ModifiableWorkspaceEntityBase<*>
    val entities = if (value is List<*>) value else listOf(value)
    thisRef.linkExternalEntity(property.returnTypeKClass, property.isChildProperty, entities as List<WorkspaceEntity?>)
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

  private fun KProperty<*>.findTargetField(entity: WorkspaceEntity): KCallable<*> {
    val expectedFieldType = if (entity is ModifiableWorkspaceEntityBase<*>) {
      entity.getEntityClass()
    } else {
      (extensionReceiverParameter?.type?.classifier as? KClass<*>)?.java ?: error("Unexpected behaviour at detecting extension field's receiver type")
    }
    return returnTypeKClass.declaredMemberProperties.find { member ->
      return@find expectedFieldType == member.returnTypeKClass.java
    } ?: error("Unexpected behaviour in detecting link to $expectedFieldType at $returnTypeKClass")
  }
}

