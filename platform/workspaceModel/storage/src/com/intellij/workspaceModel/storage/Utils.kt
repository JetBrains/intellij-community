// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.impl.EntityId
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import kotlin.reflect.KClass

internal object ClassConversion {

  private val modifiableToEntityCache = HashMap<KClass<*>, KClass<*>>()
  private val entityToEntityDataCache = HashMap<KClass<*>, KClass<*>>()
  private val entityDataToEntityCache = HashMap<Class<*>, Class<*>>()
  private val entityDataToModifiableEntityCache = HashMap<KClass<*>, KClass<*>>()
  private val packageCache = HashMap<KClass<*>, String>()

  fun <M : ModifiableWorkspaceEntity<T>, T : WorkspaceEntity> modifiableEntityToEntity(clazz: KClass<out M>): KClass<T> {
    @Suppress("UNCHECKED_CAST")
    return modifiableToEntityCache.getOrPut(clazz) {
      try {
        Class.forName(getPackage(clazz) + clazz.java.simpleName.drop(10), true, clazz.java.classLoader).kotlin
      }
      catch (e: ClassNotFoundException) {
        error("Cannot get modifiable class for $clazz")
      }
    } as KClass<T>
  }

  @Suppress("UNCHECKED_CAST")
  fun <T : WorkspaceEntity> entityToEntityData(clazz: KClass<out T>): KClass<WorkspaceEntityData<T>> {
    return entityToEntityDataCache.getOrPut(clazz) {
      (Class.forName(clazz.java.name + "Data", true, clazz.java.classLoader) as Class<WorkspaceEntityData<T>>).kotlin
    } as KClass<WorkspaceEntityData<T>>
  }

  @Suppress("UNCHECKED_CAST")
  fun <M : WorkspaceEntityData<out T>, T : WorkspaceEntity> entityDataToEntity(clazz: Class<out M>): Class<T> {
    return entityDataToEntityCache.getOrPut(clazz) {
      (Class.forName(clazz.name.dropLast(4), true, clazz.classLoader) as Class<T>)
    } as Class<T>
  }

  @Suppress("UNCHECKED_CAST")
  fun <D : WorkspaceEntityData<T>, T : WorkspaceEntity> entityDataToModifiableEntity(clazz: KClass<out D>): KClass<ModifiableWorkspaceEntity<T>> {
    return entityDataToModifiableEntityCache.getOrPut(clazz) {
      Class.forName(getPackage(clazz) + "Modifiable" + clazz.java.simpleName.dropLast(4), true,
                    clazz.java.classLoader).kotlin as KClass<ModifiableWorkspaceEntity<T>>
    } as KClass<ModifiableWorkspaceEntity<T>>
  }

  private fun getPackage(clazz: KClass<*>): String = packageCache.getOrPut(clazz) { clazz.java.name.dropLastWhile { it != '.' } }
}

// TODO: 28.05.2021 Make this value class since kt 1.5
// Just a wrapper for entity id in THIS store
internal data class ThisEntityId(val id: EntityId)

// Just a wrapper for entity id in some other store
internal data class NotThisEntityId(val id: EntityId)

internal fun EntityId.asThis(): ThisEntityId = ThisEntityId(this)
internal fun EntityId.notThis(): NotThisEntityId = NotThisEntityId(this)

internal fun currentStackTrace(depth: Int): String = Throwable().stackTrace.take(depth).joinToString(separator = "\n") { it.toString() }