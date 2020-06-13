// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.workspaceModel.storage.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

abstract class WorkspaceEntityBase : ReferableWorkspaceEntity, Any() {
  override lateinit var entitySource: EntitySource
    internal set

  internal lateinit var id: EntityId

  internal lateinit var snapshot: AbstractEntityStorage

  override fun hasEqualProperties(e: WorkspaceEntity): Boolean {
    if (this.javaClass != e.javaClass) return false

    this::class.memberProperties.forEach {
      if (it.name == WorkspaceEntityBase::id.name) return@forEach
      if (it.name == WorkspaceEntityBase::snapshot.name) return@forEach
      if (it.getter.call(this) != it.getter.call(e)) return false
    }
    return true
  }

  override fun <R : WorkspaceEntity> referrers(entityClass: Class<R>, propertyName: String): Sequence<R> {
    val connectionId = snapshot.refs.findConnectionId(this::class.java, entityClass)
    if (connectionId == null) return emptySequence()
    return when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_MANY -> snapshot.extractOneToManyChildren(connectionId, id)
      ConnectionId.ConnectionType.ONE_TO_ONE -> snapshot.extractOneToOneChild<R>(connectionId, id)?.let { sequenceOf(it) }
                                                ?: emptySequence()
      ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> snapshot.extractOneToAbstractManyChildren(connectionId, id)
      ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> snapshot.extractAbstractOneToOneChildren(connectionId, id)
    }
  }

  override fun toString(): String = "$id"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as WorkspaceEntityBase

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int = id.hashCode()
}

abstract class ModifiableWorkspaceEntityBase<T : WorkspaceEntityBase> : WorkspaceEntityBase(), ModifiableWorkspaceEntity<T> {

  internal lateinit var original: WorkspaceEntityData<T>
  internal lateinit var diff: WorkspaceEntityStorageBuilderImpl

  internal val modifiable = ThreadLocal.withInitial { false }

  internal inline fun allowModifications(action: () -> Unit) {
    modifiable.set(true)
    try {
      action()
    }
    finally {
      modifiable.remove()
    }
  }

  internal fun getEntityClass(): KClass<T> = ClassConversion.modifiableEntityToEntity(this::class)
}

internal data class EntityId(val arrayId: Int, val clazz: Int) {
  init {
    if (arrayId < 0) error("ArrayId cannot be negative: $arrayId")
  }

  override fun toString(): String = clazz.findEntityClass<WorkspaceEntity>().simpleName + "-:-" + arrayId.toString()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as EntityId

    if (arrayId != other.arrayId) return false
    if (clazz != other.clazz) return false

    return true
  }

  override fun hashCode(): Int {
    var result = arrayId
    result = 31 * result + clazz.hashCode()
    return result
  }
}

interface SoftLinkable {
  fun getLinks(): Set<PersistentEntityId<*>>
  fun updateLink(oldLink: PersistentEntityId<*>, newLink: PersistentEntityId<*>): Boolean
}

abstract class WorkspaceEntityData<E : WorkspaceEntity> : Cloneable {
  lateinit var entitySource: EntitySource
  var id: Int = -1

  internal fun createPid(): EntityId = EntityId(id, ClassConversion.entityDataToEntity(this.javaClass).toClassId())

  abstract fun createEntity(snapshot: WorkspaceEntityStorage): E

  fun addMetaData(res: E, snapshot: WorkspaceEntityStorage) {
    (res as WorkspaceEntityBase).entitySource = entitySource
    (res as WorkspaceEntityBase).id = createPid()
    (res as WorkspaceEntityBase).snapshot = snapshot as AbstractEntityStorage
  }

  internal fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilderImpl): ModifiableWorkspaceEntity<E> {
    val returnClass = ClassConversion.entityDataToModifiableEntity(this::class)
    val res = returnClass.java.newInstance()
    res as ModifiableWorkspaceEntityBase
    res.original = this
    res.diff = diff
    res.id = createPid()
    res.entitySource = this.entitySource
    return res
  }

  public override fun clone(): WorkspaceEntityData<E> = super.clone() as WorkspaceEntityData<E>

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    return this::class.memberProperties
      .filter { it.name != WorkspaceEntityData<*>::id.name }
      .map { it.getter }
      .all { it.call(this) == it.call(other) }
  }

  override fun hashCode(): Int {
    return this.javaClass.declaredFields.filterNot { it.name == WorkspaceEntityData<*>::id.name }
      .onEach { it.isAccessible = true }
      .mapNotNull { it.get(this)?.hashCode() }
      .fold(31) { acc, i -> acc * 17 + i }
  }

  override fun toString(): String {
    val fields = this.javaClass.declaredFields.toList().onEach { it.isAccessible = true }
      .joinToString(separator = ", ") { f -> "${f.name}=${f.get(this)}" }
    return "${this::class.simpleName}($fields, id=${this.id})"
  }

  /**
   * Temporally solution.
   * Get persistent Id without creating of TypedEntity. Should be in sync with TypedEntityWithPersistentId.
   * But it doesn't everywhere. E.g. FacetEntity where we should resolve module before creating persistent id.
   */
  abstract class WithCalculablePersistentId<E : WorkspaceEntity> : WorkspaceEntityData<E>() {
    abstract fun persistentId(): PersistentEntityId<*>
  }

  abstract class WithPersistentId<E : WorkspaceEntity> : WorkspaceEntityData<E>()
}

fun WorkspaceEntityData<*>.persistentId(snapshot: WorkspaceEntityStorage): PersistentEntityId<*>? = when (this) {
  is WorkspaceEntityData.WithCalculablePersistentId -> this.persistentId()
  is WorkspaceEntityData.WithPersistentId -> (this.createEntity(snapshot) as WorkspaceEntityWithPersistentId).persistentId()
  else -> null
}

class EntityDataDelegation<A : ModifiableWorkspaceEntityBase<*>, B> : ReadWriteProperty<A, B> {
  override fun getValue(thisRef: A, property: KProperty<*>): B {
    return ((thisRef.original::class.memberProperties.first { it.name == property.name }) as KProperty1<Any, *>).get(thisRef.original) as B
  }

  override fun setValue(thisRef: A, property: KProperty<*>, value: B) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    val field = thisRef.original.javaClass.getDeclaredField(property.name)
    field.isAccessible = true
    field.set(thisRef.original, value)
  }
}

