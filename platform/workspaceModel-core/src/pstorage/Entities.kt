// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.EntitySource
import com.intellij.workspace.api.ModifiableTypedEntity
import com.intellij.workspace.api.TypedEntity
import java.lang.reflect.ParameterizedType
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.*
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

internal abstract class PTypedEntity<E : TypedEntity> : TypedEntity, Any() {
  internal lateinit var entitySourceImpl: EntitySource
  override val entitySource: EntitySource by lazy { entitySourceImpl }

  lateinit var idImpl: PId<E>
  open val id: PId<E> by lazy { idImpl }

  lateinit var snapshotImpl: AbstractPEntityStorage
  open val snapshot: AbstractPEntityStorage by lazy { snapshotImpl }

  override fun hasEqualProperties(e: TypedEntity): Boolean {
    if (this.javaClass != e.javaClass) return false

    this::class.memberProperties.forEach {
      if (it.name == PTypedEntity<*>::id.name) return@forEach
      if (it.name == PTypedEntity<*>::idImpl.name) return@forEach
      if (it.getter.call(this) != it.getter.call(e)) return false
    }
    return true
  }

  override fun toString(): String = "${javaClass.simpleName}-:-$id"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PTypedEntity<*>

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int = id.hashCode()
}

internal abstract class PModifiableTypedEntity<T : PTypedEntity<T>>
  : PTypedEntity<T>(), ModifiableTypedEntity<T> {

  internal lateinit var original: PEntityData<T>
  internal lateinit var diff: PEntityStorageBuilder

  override val id: PId<T> by lazy { PId(original.id, getEntityClass()) }

  internal fun getEntityClass(): KClass<T> = getEntityClass(this.javaClass.kotlin).kotlin

  override val entitySource by lazy { original.entitySource }

  companion object {
    fun <M : ModifiableTypedEntity<T>, T : TypedEntity> getEntityClass(clazz: KClass<M>): Class<T> {
      return (clazz.supertypes.find {
        PModifiableTypedEntity::class.java.isAssignableFrom((it.classifier as KClass<*>).java)
      }!!.javaType as ParameterizedType).actualTypeArguments.first() as Class<T>
    }
  }
}

internal data class PId<E : TypedEntity>(val arrayId: Int, val clazz: KClass<E>) {
  init {
    if (arrayId < 0) error("ArrayId cannot be negative: $arrayId")
  }

  override fun toString(): String = arrayId.toString()
}

internal abstract class PEntityData<E : TypedEntity> {
  lateinit var entitySource: EntitySource
  var id: Int = -1

  fun createEntity(snapshot: AbstractPEntityStorage): E {
    val returnClass = immutableClass()

    val params = returnClass.primaryConstructor!!.parameters
      .filterNot { it.name == "snapshot" }
      .associateWith { param ->
        val value = this::class.memberProperties.first { it.name == param.name }.getter.call(this)
        if (param.type.isList()) ArrayList(value as List<*>) else value
      }.toMutableMap()
    val res = returnClass.primaryConstructor!!.callBy(params)
    (res as PTypedEntity<E>).entitySourceImpl = entitySource
    (res as PTypedEntity<E>).idImpl = PId(this::class.memberProperties.first { it.name == "id" }.getter.call(this) as Int, returnClass)
    (res as PTypedEntity<E>).snapshotImpl = snapshot
    return res
  }

  fun immutableClass() = this::class.memberFunctions.first { it.name == this::createEntity.name }.returnType.classifier as KClass<*> as KClass<E>

  fun wrapAsModifiable(diff: PEntityStorageBuilder): ModifiableTypedEntity<E> {
    val returnClass = Class.forName(this::class.qualifiedName!!.dropLast(10) + "ModifiableEntity").kotlin
    val res = returnClass.primaryConstructor!!.call() as ModifiableTypedEntity<E>
    res as PModifiableTypedEntity
    res.original = this
    res.diff = diff
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

  private fun KType.isList() = List::class.java.isAssignableFrom(jvmErasure.java)

  companion object {
    fun <T : TypedEntity> fromImmutableClass(imm: Class<T>): Class<PEntityData<T>> {
      return Class.forName(imm.name + "Data") as Class<PEntityData<T>>
    }
  }
}

internal class EntityData<A : PModifiableTypedEntity<*>, B> : ReadWriteProperty<A, B> {
  override fun getValue(thisRef: A, property: KProperty<*>): B {
    return ((thisRef.original::class.memberProperties.first { it.name == property.name }) as KProperty1<Any, *>).get(thisRef.original) as B
  }

  override fun setValue(thisRef: A, property: KProperty<*>, value: B) {
    ((thisRef.original::class.memberProperties.first { it.name == property.name }) as KMutableProperty<*>).setter.call(thisRef.original,
                                                                                                                       value)
  }
}

