// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.util.ReflectionUtil
import com.intellij.workspaceModel.storage.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties


/**
 * For creating a new entity, you should perform the following steps:
 *
 * - Choose the name of the entity, e.g. MyModuleEntity
 * - Create [WorkspaceEntity] representation:
 *   - The entity should inherit [WorkspaceEntityBase]
 *   - Properties (not references to other entities) should be listed in a primary constructor as val's
 *   - If the entity has PersistentId, the entity should extend [WorkspaceEntityWithPersistentId]
 *   - If the entity has references to other entities, they should be implement using property delegation objects listed in [com.intellij.workspaceModel.storage.impl.references] package.
 *       E.g. [OneToMany] or [ManyToOne.NotNull]
 *
 *   Example:
 *
 *   ```kotlin
 *   class MyModuleEntity(val name: String) : WorkspaceEntityBase(), WorkspaceEntityWithPersistentId {
 *
 *      val childModule: MyModuleEntity? by OneToOneParent.Nullable(MyModuleEntity::class.java, true)
 *
 *      fun persistentId() = NameId(name)
 *   }
 *   ```
 *
 *   The above entity describes an entity with `name` property, persistent id and the reference to "ChildModule"
 *
 *   This object will be used by users and it's returned by methods like `resolve`, `entities` and so on.
 *
 *   -------------------------------------------------------------------------------------------------------------------------------
 *
 * - Create EntityData representation:
 *   - Entity data should have the name ${entityName}Data. E.g. MyModuleEntityData.
 *   - Entity data should inherit [WorkspaceEntityData]
 *   - Properties (not references to other entities) should be listed in the body as lateinit var's or with default value (null, 0, false).
 *   - If the entity has PersistentId, the Entity data should extend [WorkspaceEntityData.WithCalculablePersistentId]
 *   - References to other entities should not be listed in entity data.
 *
 *   - If the entity contains soft references to other entities (persistent id to other entities), entity data should extend SoftLinkable
 *        interface and implement the required methods. Check out the [FacetEntityData] implementation, but keep in mind the this might
 *        be more complicated like in [ModuleEntityData].
 *   - Entity data should implement [WorkspaceEntityData.createEntity] method. This method should return an instance of
 *        [WorkspaceEntity]. This instance should be passed to [addMetaData] after creation!
 *        E.g.:
 *
 *        override fun createEntity(snapshot: WorkspaceEntityStorage): ModuleEntity = ModuleEntity(name, type, dependencies).also {
 *            addMetaData(it, snapshot)
 *        }
 *
 *   Example:
 *
 *   ```kotlin
 *   class MyModuleEntityData : WorkspaceEntityData.WithCalculablePersistentId<MyModuleEntity>() {
 *       lateinit var name: String
 *
 *       override fun persistentId(): NameId = NameId(name)
 *
*        override fun createEntity(snapshot: WorkspaceEntityStorage): MyModuleEntity = MyModuleEntity(name).also {
*            addMetaData(it, snapshot)
*        }
 *   }
 *   ```
 *
 *   This is an internal representation of WorkspaceEntity. It's not passed to users.
 *
 *   -------------------------------------------------------------------------------------------------------------------------------
 *
 *  - Create [ModifiableWorkspaceEntity] representation:
 *   - The name should be: Modifiable${entityName}. E.g. ModifiableMyModuleEntity
 *   - This should be inherited from [ModifiableWorkspaceEntityBase]
 *   - Properties (not references to other entities) should be listed in the body as delegation to [EntityDataDelegation()]
 *   - References to other entities should be listed as in [WorkspaceEntity], but with corresponding modifiable delegates
 *
 *   Example:
 *
 *   ```kotlin
 *   class ModifiableMyModuleEntity : ModifiableWorkspaceEntityBase<MyModuleEntity>() {
 *      var name: String by EntityDataDelegation()
 *
 *      var childModule: MyModuleEntity? by MutableOneToOneParent.NotNull(MyModuleEntity::class.java, MyModuleEntity::class.java, true)
 *   }
 *   ```
 */


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

  override fun <E : WorkspaceEntity> createReference(): EntityReference<E> {
    return EntityReferenceImpl(this.id)
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
}

interface SoftLinkable {
  fun getLinks(): Set<PersistentEntityId<*>>
  fun updateLink(oldLink: PersistentEntityId<*>, newLink: PersistentEntityId<*>): Boolean
}

abstract class WorkspaceEntityData<E : WorkspaceEntity> : Cloneable {
  lateinit var entitySource: EntitySource
  var id: Int = -1

  internal fun createEntityId(): EntityId = EntityId(id, ClassConversion.entityDataToEntity(this.javaClass).toClassId())

  abstract fun createEntity(snapshot: WorkspaceEntityStorage): E

  fun addMetaData(res: E, snapshot: WorkspaceEntityStorage) {
    (res as WorkspaceEntityBase).entitySource = entitySource
    (res as WorkspaceEntityBase).id = createEntityId()
    (res as WorkspaceEntityBase).snapshot = snapshot as AbstractEntityStorage
  }

  internal fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilderImpl): ModifiableWorkspaceEntity<E> {
    val returnClass = ClassConversion.entityDataToModifiableEntity(this::class)
    val res = returnClass.java.newInstance()
    res as ModifiableWorkspaceEntityBase
    res.original = this
    res.diff = diff
    res.id = createEntityId()
    res.entitySource = this.entitySource
    return res
  }

  public override fun clone(): WorkspaceEntityData<E> = super.clone() as WorkspaceEntityData<E>

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    return ReflectionUtil.collectFields(this.javaClass).filterNot { it.name == WorkspaceEntityData<*>::id.name }
      .onEach { it.isAccessible = true }
      .all { it.get(this) == it.get(other) }
  }

  open fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    return ReflectionUtil.collectFields(this.javaClass)
      .filterNot { it.name == WorkspaceEntityData<*>::id.name }
      .filterNot { it.name == WorkspaceEntityData<*>::entitySource.name }
      .onEach { it.isAccessible = true }
      .all { it.get(this) == it.get(other) }
  }

  override fun hashCode(): Int {
    return ReflectionUtil.collectFields(this.javaClass).filterNot { it.name == WorkspaceEntityData<*>::id.name }
      .onEach { it.isAccessible = true }
      .mapNotNull { it.get(this)?.hashCode() }
      .fold(31) { acc, i -> acc * 17 + i }
  }

  override fun toString(): String {
    val fields = ReflectionUtil.collectFields(this.javaClass).toList().onEach { it.isAccessible = true }
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

/**
 * This interface is a solution for checking consistency of some entities that can't be checked automatically
 *
 * For example, we can mark LibraryPropertiesEntityData with this interface and check that entity source of properties is the same as
 *  entity source of the library itself.
 *
 * Interface should be applied to *entity data*.
 *
 * [assertConsistency] method is called during [WorkspaceEntityStorageBuilderImpl.assertConsistency].
 */
interface WithAssertableConsistency {
  fun assertConsistency(storage: WorkspaceEntityStorage)
}