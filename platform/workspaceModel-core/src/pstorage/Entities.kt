// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.EntitySource
import com.intellij.workspace.api.ModifiableTypedEntity
import com.intellij.workspace.api.ReferableTypedEntity
import com.intellij.workspace.api.TypedEntity
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.*
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

abstract class PTypedEntity : ReferableTypedEntity, Any() {
  override lateinit var entitySource: EntitySource
    internal set

  internal open lateinit var id: PId<TypedEntity>

  internal open lateinit var snapshot: AbstractPEntityStorage

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
    }
  }

  override fun toString(): String = "${javaClass.simpleName}-:-$id"

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

  override fun toString(): String = arrayId.toString()
}

abstract class PEntityData<E : TypedEntity> {
  lateinit var entitySource: EntitySource
  var id: Int = -1

  internal fun createPid(): PId<E> = PId(id, ClassConversion.entityDataToEntity(this::class))

  internal fun createEntity(snapshot: AbstractPEntityStorage): E {
    val returnClass = ClassConversion.entityDataToEntity(this::class)

    val params = returnClass.primaryConstructor!!.parameters
      .associateWith { param ->
        val value = this::class.memberProperties.first { it.name == param.name }.getter.call(this)
        if (param.type.isList()) ArrayList(value as List<*>) else value
      }.toMutableMap()
    val res = returnClass.primaryConstructor!!.callBy(params)
    (res as PTypedEntity).entitySource = entitySource
    (res as PTypedEntity).id = createPid() as PId<TypedEntity>
    (res as PTypedEntity).snapshot = snapshot
    return res
  }

  internal fun wrapAsModifiable(diff: PEntityStorageBuilder): ModifiableTypedEntity<E> {
    val returnClass = ClassConversion.entityDataToModifiableEntity(this::class)
    val primaryConstructor = returnClass.primaryConstructor!!
    primaryConstructor.isAccessible = true
    val res = primaryConstructor.call() as ModifiableTypedEntity<E>
    res as PModifiableTypedEntity
    res.original = this
    res.diff = diff
    res.id = PId(this.id, res.getEntityClass() as KClass<TypedEntity>)
    res.entitySource = this.entitySource
    return res
  }

  fun clone(): PEntityData<E> {
    val copied = this::class.primaryConstructor!!.call()
    this::class.memberProperties.filterIsInstance<KMutableProperty<*>>().forEach {
      if (it.returnType.isList()) {
        it.setter.call(copied, ArrayList(it.getter.call(this) as List<*>))
      }
      else {
        it.setter.call(copied, it.getter.call(this))
      }
    }
    return copied
  }

  private fun KType.isList(): Boolean = this.classifier == List::class
}

internal class EntityDataDelegation<A : PModifiableTypedEntity<*>, B> : ReadWriteProperty<A, B> {
  override fun getValue(thisRef: A, property: KProperty<*>): B {
    return ((thisRef.original::class.memberProperties.first { it.name == property.name }) as KProperty1<Any, *>).get(thisRef.original) as B
  }

  override fun setValue(thisRef: A, property: KProperty<*>, value: B) {
    if (!thisRef.modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside 'addEntity' and 'modifyEntity' methods only!")
    }
    ((thisRef.original::class.memberProperties.first { it.name == property.name }) as KMutableProperty<*>).setter.call(thisRef.original,
                                                                                                                       value)
  }
}

