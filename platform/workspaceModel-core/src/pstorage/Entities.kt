// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties

abstract class PTypedEntity : ReferableTypedEntity, Any() {
  override lateinit var entitySource: EntitySource
    internal set

  internal lateinit var id: PId<TypedEntity>

  internal lateinit var snapshot: AbstractPEntityStorage

  override fun hasEqualProperties(e: TypedEntity): Boolean {
    if (this.javaClass != e.javaClass) return false

    this::class.memberProperties.forEach {
      if (it.name == PTypedEntity::id.name) return@forEach
      if (it.getter.call(this) != it.getter.call(e)) return false
    }
    return true
  }

  override fun <R : TypedEntity> referrers(entityClass: Class<R>, propertyName: String): Sequence<R> {
    val connectionId = snapshot.refs.findConnectionId(this::class.java, entityClass) as ConnectionId<PTypedEntity, R>?
    if (connectionId == null) return emptySequence()
    return when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_MANY -> snapshot.extractOneToManyChildren(connectionId, id as PId<PTypedEntity>)
      ConnectionId.ConnectionType.ONE_TO_ONE -> snapshot.extractOneToOneChild(connectionId, id as PId<PTypedEntity>)?.let { sequenceOf(it) } ?: emptySequence()
      ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> snapshot.extractOneToAbstractManyChildren(connectionId, id as PId<PTypedEntity>)
      ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> snapshot.extractAbstractOneToOneChildren(connectionId, id as PId<PTypedEntity>)
    }
  }

  override fun toString(): String = "$id"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PTypedEntity

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int = id.hashCode()
}

abstract class PModifiableTypedEntity<T : PTypedEntity> : PTypedEntity(), ModifiableTypedEntity<T> {

  internal lateinit var original: PEntityData<T>
  internal lateinit var diff: PEntityStorageBuilder

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

internal data class PId<E : TypedEntity>(val arrayId: Int, val clazz: KClass<E>) {
  init {
    if (arrayId < 0) error("ArrayId cannot be negative: $arrayId")
  }

  override fun toString(): String = clazz.simpleName + "-:-"+ arrayId.toString()
}

interface PSoftLinkable {
  fun getLinks(): List<PersistentEntityId<*>>
  fun updateLink(oldLink: PersistentEntityId<*>,
                 newLink: PersistentEntityId<*>,
                 affectedIds: MutableList<Pair<PersistentEntityId<*>, PersistentEntityId<*>>>): Boolean
}

abstract class PEntityData<E : TypedEntity>: Cloneable {
  lateinit var entitySource: EntitySource
  var id: Int = -1

  internal fun createPid(): PId<E> = PId(id, ClassConversion.entityDataToEntity(this::class))

  abstract fun createEntity(snapshot: TypedEntityStorage): E

  fun addMetaData(res: E, snapshot: TypedEntityStorage) {
    (res as PTypedEntity).entitySource = entitySource
    (res as PTypedEntity).id = createPid() as PId<TypedEntity>
    (res as PTypedEntity).snapshot = snapshot as AbstractPEntityStorage
  }

  internal fun wrapAsModifiable(diff: PEntityStorageBuilder): ModifiableTypedEntity<E> {
    val returnClass = ClassConversion.entityDataToModifiableEntity(this::class)
    val res = returnClass.java.newInstance()
    res as PModifiableTypedEntity
    res.original = this
    res.diff = diff
    res.id = createPid() as PId<TypedEntity>
    res.entitySource = this.entitySource
    return res
  }

  public override fun clone(): PEntityData<E> = super.clone() as PEntityData<E>

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    return this::class.memberProperties
      .filter { it.name != PEntityData<*>::id.name }
      .map { it.getter }
      .all { it.call(this) == it.call(other) }
  }

  override fun hashCode(): Int {
    return this::class.memberProperties
      .filter { it.name != PEntityData<*>::id.name }
      .map { it.getter.call(this).hashCode() }
      .fold(31) { acc, i -> acc * 17 + i }
  }
}

class EntityDataDelegation<A : PModifiableTypedEntity<*>, B> : ReadWriteProperty<A, B> {
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

