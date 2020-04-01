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

  lateinit var snapshotImpl: PEntityStorage
  open val snapshot: PEntityStorage by lazy { snapshotImpl }

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

internal abstract class PModifiableTypedEntity<T : PTypedEntity<T>>(val original: PEntityData<T>, val diff: PEntityStorageBuilder)
  : PTypedEntity<T>(), ModifiableTypedEntity<T> {

  override val id: PId<T> = PId(original.id, getEntityClass())

  internal fun getEntityClass(): KClass<T> = getEntityClass(this.javaClass.kotlin).kotlin

  override val entitySource = original.entitySource

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

  fun createEntity(snapshot: PEntityStorage): E {
    val returnClass = (this::class.memberFunctions.first { it.name == this::createEntity.name }.returnType.classifier as KClass<*>) as KClass<E>

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

  fun wrapAsModifiable(diff: PEntityStorageBuilder): ModifiableTypedEntity<E> {
    val returnClass = Class.forName(this::class.qualifiedName!!.dropLast(10) + "ModifiableEntity").kotlin
    val params = returnClass.primaryConstructor!!.parameters.associateWith {
      when (it.name) {
        "original" -> this
        "diff" -> diff
        else -> error("")
      }
    }
    return returnClass.primaryConstructor!!.callBy(params) as ModifiableTypedEntity<E>
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

internal class PFolderEntityData : PEntityData<PFolderEntity>() {
  lateinit var data: String
}

internal class PSoftSubFolderEntityData : PEntityData<PSoftSubFolderEntity>()

internal class PSubFolderEntityData : PEntityData<PSubFolderEntity>() {
  lateinit var data: String
}

internal class PFolderEntity(
  val data: String
) : PTypedEntity<PFolderEntity>() {
  val children: Sequence<PSubFolderEntity> by OneToMany.HardRef(PSubFolderEntity::class)
  val softChildren: Sequence<PSoftSubFolderEntity> by OneToMany.SoftRef(PSoftSubFolderEntity::class)
}

internal class PSoftSubFolderEntity : PTypedEntity<PSoftSubFolderEntity>() {
  val parent: PFolderEntity? by ManyToOne.SoftRef(PFolderEntity::class)
}

internal class PSubFolderEntity(
  val data: String
) : PTypedEntity<PSubFolderEntity>() {
  val parent: PFolderEntity? by ManyToOne.HardRef(PFolderEntity::class)
}

internal class PFolderModifiableEntity(original: PFolderEntityData,
                                       diff: PEntityStorageBuilder) : PModifiableTypedEntity<PFolderEntity>(original, diff) {
  var data: String by Another(original)

  var children: Sequence<PSubFolderEntity> by MutableOneToMany.HardRef(PFolderEntity::class, PSubFolderEntity::class)
  var softChildren: Sequence<PSoftSubFolderEntity> by MutableOneToMany.SoftRef(PFolderEntity::class, PSoftSubFolderEntity::class)
}

internal class PSubFolderModifiableEntity(original: PSubFolderEntityData,
                                          diff: PEntityStorageBuilder) : PModifiableTypedEntity<PSubFolderEntity>(original, diff) {
  var data: String by Another(original)

  var parent: PFolderEntity? by MutableManyToOne.HardRef(PSubFolderEntity::class, PFolderEntity::class)
}

internal class PSoftSubFolderModifiableEntity(
  original: PSoftSubFolderEntityData,
  diff: PEntityStorageBuilder
) : PModifiableTypedEntity<PSoftSubFolderEntity>(original, diff) {

  var parent: PFolderEntity? by MutableManyToOne.SoftRef(PSoftSubFolderEntity::class, PFolderEntity::class)
}

internal class Another<A, B>(val original: Any) : ReadWriteProperty<A, B> {
  override fun getValue(thisRef: A, property: KProperty<*>): B {
    return ((original::class.memberProperties.first { it.name == property.name }) as KProperty1<Any, *>).get(original) as B
  }

  override fun setValue(thisRef: A, property: KProperty<*>, value: B) {
    ((original::class.memberProperties.first { it.name == property.name }) as KMutableProperty<*>).setter.call(original, value)
  }
}

internal object MySource : EntitySource
