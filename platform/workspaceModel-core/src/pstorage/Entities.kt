// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.EntitySource
import com.intellij.workspace.api.ModifiableTypedEntity
import com.intellij.workspace.api.TypedEntity
import java.lang.reflect.ParameterizedType
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

internal abstract class PTypedEntity<E : TypedEntity>(arrayId: Int) : TypedEntity, Any() {
  open val id: PId<E> = PId(arrayId, this.javaClass.kotlin as KClass<E>)

  override fun hasEqualProperties(e: TypedEntity): Boolean {
    if (this.javaClass != e.javaClass) return false

    this::class.memberProperties.forEach {
      if (it.name == PTypedEntity<*>::id.name) return@forEach
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
  : PTypedEntity<T>(original.id), ModifiableTypedEntity<T> {

  override val id: PId<T> = PId(original.id, getEntityClass())

  internal fun getEntityClass(): KClass<T> = ((this::class.supertypes.find {
    PModifiableTypedEntity::class.java.isAssignableFrom((it.classifier as KClass<*>).java)
  }!!.javaType as ParameterizedType).actualTypeArguments.first() as Class<T>).kotlin

  override val entitySource = original.entitySource
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
    val returnClass = (this::class.memberFunctions.first { it.name == this::createEntity.name }.returnType.classifier as KClass<*>)
    val params = this::class.memberProperties.associateWith { it.getter.call(this) }
      .mapKeys { (k, _) ->
        returnClass.primaryConstructor!!.parameters.first { if (k.name != "id") it.name == k.name else it.name == "arrayId" }
      }
      .mapValues { (_, v) -> if (List::class.java.isAssignableFrom(v!!.javaClass)) ArrayList(v as List<*>) else v }.toMutableMap()
    val snapshotParameter = returnClass.primaryConstructor!!.parameters.find { it.name == "snapshot" }
    if (snapshotParameter != null) {
      params[snapshotParameter] = snapshot
    }
    return returnClass.primaryConstructor!!.callBy(params) as E
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
      if (it.isList()) {
        it.setter.call(copied, ArrayList(it.getter.call(this) as List<*>))
      }
      else {
        it.setter.call(copied, it.getter.call(this))
      }
    }
    return copied
  }

  private fun KMutableProperty<*>.isList() = List::class.java.isAssignableFrom(returnType.jvmErasure.java)
}

internal class PFolderEntityData : PEntityData<PFolderEntity>() {
  lateinit var data: String
}

internal class PSoftSubFolderEntityData : PEntityData<PSoftSubFolder>()

internal class PSubFolderEntityData : PEntityData<PSubFolderEntity>() {
  lateinit var data: String
}

internal class PFolderEntity(
  override val entitySource: EntitySource,
  arrayId: Int,
  val snapshot: PEntityStorage,
  val data: String
) : PTypedEntity<PFolderEntity>(arrayId) {

  val children: Sequence<PSubFolderEntity> by OneToMany.HardRef(snapshot, PSubFolderEntity::class)
  val softChildren: Sequence<PSoftSubFolder> by OneToMany.SoftRef(snapshot, PSoftSubFolder::class)
}

internal class PSoftSubFolder(
  override val entitySource: EntitySource,
  arrayId: Int,
  val snapshot: PEntityStorage
) : PTypedEntity<PSoftSubFolder>(arrayId) {

  val parent: PFolderEntity? by ManyToOne.SoftRef(snapshot, PFolderEntity::class)
}

internal class PSubFolderEntity(
  override val entitySource: EntitySource,
  arrayId: Int,
  val snapshot: PEntityStorage,
  val data: String
) : PTypedEntity<PSubFolderEntity>(arrayId) {

  val parent: PFolderEntity? by ManyToOne.HardRef(snapshot, PFolderEntity::class)
}

@PEntityDataClass(PFolderEntityData::class)
@PEntityClass(PFolderEntity::class)
internal class PFolderModifiableEntity(original: PFolderEntityData,
                                       diff: PEntityStorageBuilder) : PModifiableTypedEntity<PFolderEntity>(original, diff) {
  var data: String by Another(original)

  var children: Sequence<PSubFolderEntity> by MutableOneToMany.HardRef(diff, PFolderEntity::class, PSubFolderEntity::class)
  var softChildren: Sequence<PSoftSubFolder> by MutableOneToMany.SoftRef(diff, PFolderEntity::class, PSoftSubFolder::class)
}

@PEntityDataClass(PSubFolderEntityData::class)
@PEntityClass(PSubFolderEntity::class)
internal class PSubFolderModifiableEntity(original: PSubFolderEntityData,
                                          diff: PEntityStorageBuilder) : PModifiableTypedEntity<PSubFolderEntity>(original, diff) {
  var data: String by Another(original)

  var parent: PFolderEntity? by MutableManyToOne.HardRef(diff, PSubFolderEntity::class, PFolderEntity::class)
}

internal class Another<A, B>(val original: Any) : ReadWriteProperty<A, B> {
  override fun getValue(thisRef: A, property: KProperty<*>): B {
    return ((original::class.memberProperties.first { it.name == property.name }) as KProperty1<Any, *>).get(original) as B
  }

  override fun setValue(thisRef: A, property: KProperty<*>, value: B) {
    ((original::class.memberProperties.first { it.name == property.name }) as KMutableProperty<*>).setter.call(original, value)
  }
}

@PEntityDataClass(PSoftSubFolderEntityData::class)
@PEntityClass(PSoftSubFolder::class)
internal class PSoftSubFolderModifiableEntity(
  original: PSoftSubFolderEntityData,
  diff: PEntityStorageBuilder
) : PModifiableTypedEntity<PSoftSubFolder>(original, diff) {

  var parent: PFolderEntity? by MutableManyToOne.SoftRef(diff, PSoftSubFolder::class, PFolderEntity::class)
}

internal object MySource : EntitySource

internal annotation class PEntityDataClass(val clazz: KClass<out PEntityData<*>>)
internal annotation class PEntityClass(val clazz: KClass<out PTypedEntity<*>>)
