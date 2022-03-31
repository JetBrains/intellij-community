// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.workspaceModel.codegen.storage.impl.AbstractEntityStorage
import com.intellij.workspaceModel.codegen.storage.impl.EntityReferenceImpl
import com.intellij.workspaceModel.codegen.storage.impl.WorkspaceEntityStorageBuilderImpl
import com.intellij.workspaceModel.codegen.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileIndex
import com.intellij.workspaceModel.storage.impl.indices.WorkspaceMutableIndex
import org.jetbrains.deft.Obj


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

  var id: EntityId = 0

  lateinit var snapshot: WorkspaceEntityStorage

  override val name: String? get() = TODO()
  override val parent: Obj? get() = TODO()

  override fun <R : WorkspaceEntity> referrers(entityClass: Class<R>, propertyName: String): Sequence<R> {
    val mySnapshot = snapshot as AbstractEntityStorage
    return getReferences(mySnapshot, entityClass)
  }

  internal fun <R : WorkspaceEntity> getReferences(
    mySnapshot: AbstractEntityStorage,
    entityClass: Class<R>
  ): Sequence<R> {
    var connectionId =
      mySnapshot.refs.findConnectionId(getEntityInterface(), entityClass)
    if (connectionId != null) {
      return when (connectionId.connectionType) {
        ConnectionId.ConnectionType.ONE_TO_MANY -> mySnapshot.extractOneToManyChildren(connectionId, id)
        ConnectionId.ConnectionType.ONE_TO_ONE -> mySnapshot.extractOneToOneChild<R>(connectionId, id)
          ?.let { sequenceOf(it) }
          ?: emptySequence()
        ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> mySnapshot.extractOneToAbstractManyChildren(
          connectionId,
          id.asParent()
        )
        ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> /*mySnapshot.extractAbstractOneToOneChild<R>(connectionId, id.asParent())?.let {
          sequenceOf(it)
        } ?: */emptySequence()
      }
    }
    connectionId =
      mySnapshot.refs.findConnectionId(entityClass, getEntityInterface())
    if (connectionId != null) {
      return when (connectionId.connectionType) {
        ConnectionId.ConnectionType.ONE_TO_MANY -> mySnapshot.extractOneToManyParent<R>(connectionId, id)?.let { sequenceOf(it) } ?: emptySequence()
        ConnectionId.ConnectionType.ONE_TO_ONE -> mySnapshot.extractOneToOneParent<R>(connectionId, id)
          ?.let { sequenceOf(it) }
          ?: emptySequence()
        ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> mySnapshot.extractOneToAbstractManyParent<R>(
          connectionId,
          id.asChild()
        )
          ?.let { sequenceOf(it) }
          ?: emptySequence()
        ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> /*mySnapshot.extractAbstractOneToOneChild<R>(connectionId, id.asParent())?.let {
          sequenceOf(it)
        } ?: */emptySequence()
      }
    }
    return emptySequence()
  }

  override fun <E : WorkspaceEntity> createReference(): EntityReference<E> {
    return EntityReferenceImpl(this.id)
  }

  fun getEntityInterface(): Class<out WorkspaceEntity> = id.clazz.findWorkspaceEntity()

  override fun toString(): String = id.asString()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true

    other as WorkspaceEntityBase

    if (id != other.id) return false
    if ((this.snapshot as AbstractEntityStorage).entityDataById(id) !==
      (other.snapshot as AbstractEntityStorage).entityDataById(other.id)
    ) return false

    return true
  }

  override fun hashCode(): Int = id.hashCode()
}

data class ExtRefKey(
  val className: String,
  val fieldName: String,
) {

  constructor(className: String, fieldName: String, isChild: Boolean, connectionId: ConnectionId) : this(className, fieldName) {
    this.isThisFieldChild = isChild
    this.connectionId = connectionId
  }

  private var isThisFieldChild: Boolean? = null
  private var connectionId: ConnectionId? = null

  fun getConnectionId(): ConnectionId {
    return connectionId ?: error("")
  }

  fun isChild(): Boolean {
    if (isThisFieldChild == null) error("")
    return isThisFieldChild == true
  }

  fun setChild(value: Boolean) {
    isThisFieldChild = value
  }
}

abstract class ModifiableWorkspaceEntityBase<T : WorkspaceEntity> : WorkspaceEntityBase(), ModifiableWorkspaceEntity<T> {
  var diff: WorkspaceEntityStorageBuilder? = null

  val modifiable = ThreadLocal.withInitial { false }
  val changedProperty: MutableSet<String> = mutableSetOf()

  /**
   * For the state
   *
   * ```
   * interface RiderEntity {
   *   val moduleRef: ModuleEntity
   * }
   * val ModuleEntity.riderRef: RiderEntity
   * ```
   * Here in `ModuleEntity` we store the key in form `RiderEntity::moduleRef`
   */
  val extReferences: MutableMap<ExtRefKey, Any?> = HashMap()

  override fun <R : WorkspaceEntity> referrers(entityClass: Class<R>, propertyName: String): Sequence<R> {
    val myDiff = diff
    val entitiesFromDiff = if (myDiff != null) {
      getReferences(myDiff as AbstractEntityStorage, entityClass)
    } else emptySequence()

    val res = extReferences[ExtRefKey(entityClass.simpleName, propertyName)]
    return entitiesFromDiff + if (res == null) {
      emptySequence()
    } else {
      if (res is List<*>) {
        res.asSequence() as Sequence<R>
      } else {
        sequenceOf(res as R)
      }
    }
  }

  inline fun allowModifications(action: () -> Unit) {
    modifiable.set(true)
    try {
      action()
    }
    finally {
      modifiable.remove()
    }
  }

  protected fun checkModificationAllowed() {
    if (diff != null && !modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside `modifyEntity` method only!")
    }
  }

  abstract fun getEntityClass(): Class<T>

  open fun applyToBuilder(builder: WorkspaceEntityStorageBuilder) {
    throw NotImplementedError()
  }

  open fun getEntityData(): WorkspaceEntityData<T> {
    val actualEntityData = (diff as WorkspaceEntityStorageBuilderImpl).entityDataById(id)
      ?: error("Requested entity data doesn't exist at entity family")
    return actualEntityData as WorkspaceEntityData<T>
  }

  // For generated entities
  @Suppress("unused")
  fun addToBuilder() {
    val builder = diff as WorkspaceEntityStorageBuilderImpl
    builder.putEntity(this)
  }

  // For generated entities
  // TODO:: Can be replaced to the direct calls
  fun applyRef(connectionId: ConnectionId, child: WorkspaceEntity) {
    val builder = diff as WorkspaceEntityStorageBuilderImpl
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_ONE -> builder.updateOneToOneChildOfParent(connectionId, this, child)
      ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> builder.updateOneToAbstractOneChildOfParent(connectionId, this, child)
      else -> error("Unexpected branch")
    }
  }

  // TODO:: Can be replaced to the direct calls
  fun applyRef(connectionId: ConnectionId, children: List<WorkspaceEntity>) {
    val builder = diff as WorkspaceEntityStorageBuilderImpl
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_MANY -> builder.updateOneToManyChildrenOfParent(connectionId, this, children)
      ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> builder.updateOneToAbstractManyChildrenOfParent(connectionId, this, children.asSequence())
      else -> error("Unexpected branch")
    }
  }

  // TODO:: Can be replaced to the direct calls
  fun applyParentRef(connectionId: ConnectionId, parent: WorkspaceEntity) {
    val builder = diff as WorkspaceEntityStorageBuilderImpl
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_ONE -> builder.updateOneToOneParentOfChild(connectionId, this, parent)
      ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> builder.updateOneToAbstractOneParentOfChild(connectionId, this, parent)
      ConnectionId.ConnectionType.ONE_TO_MANY -> builder.updateOneToManyParentOfChild(connectionId, this, parent)
      ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> builder.updateOneToAbstractManyParentOfChild(connectionId, this, parent)
    }
  }

  fun existsInBuilder(builder: WorkspaceEntityStorageBuilder): Boolean {
    builder as WorkspaceEntityStorageBuilderImpl
    val entityData = getEntityData()
    return builder.entityDataById(entityData.createEntityId()) != null
  }

  // For generated entities
  @Suppress("unused")
  fun index(entity: WorkspaceEntity, propertyName: String, virtualFileUrl: VirtualFileUrl?) {
    val builder = diff as WorkspaceEntityStorageBuilderImpl
    builder.getMutableVirtualFileUrlIndex().index(entity, propertyName, virtualFileUrl)
  }

  // For generated entities
  @Suppress("unused")
  fun index(entity: WorkspaceEntity, propertyName: String, virtualFileUrls: Set<VirtualFileUrl>) {
    val builder = diff as WorkspaceEntityStorageBuilderImpl
    (builder.getMutableVirtualFileUrlIndex() as VirtualFileIndex.MutableVirtualFileIndex).index((entity as WorkspaceEntityBase).id,
                                                                                                propertyName, virtualFileUrls)
  }

  // For generated entities
  @Suppress("unused")
  fun indexJarDirectories(entity: WorkspaceEntity, virtualFileUrls: Set<VirtualFileUrl>) {
    val builder = diff as WorkspaceEntityStorageBuilderImpl
    (builder.getMutableVirtualFileUrlIndex() as VirtualFileIndex.MutableVirtualFileIndex).indexJarDirectories(
      (entity as WorkspaceEntityBase).id, virtualFileUrls)
  }
}

interface SoftLinkable {
  fun getLinks(): Set<PersistentEntityId<*>>
  fun index(index: WorkspaceMutableIndex<PersistentEntityId<*>>)
  fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: WorkspaceMutableIndex<PersistentEntityId<*>>)
  fun updateLink(oldLink: PersistentEntityId<*>, newLink: PersistentEntityId<*>): Boolean
}

abstract class WorkspaceEntityData<E : WorkspaceEntity> : Cloneable {
  lateinit var entitySource: EntitySource
  var id: Int = -1

  fun isEntitySourceInitialized(): Boolean = ::entitySource.isInitialized

  fun createEntityId(): EntityId = createEntityId(id, ClassConversion.entityDataToEntity(javaClass).toClassId())

  abstract fun createEntity(snapshot: WorkspaceEntityStorage): E

  open fun wrapAsModifiable(diff: WorkspaceEntityStorageBuilder): ModifiableWorkspaceEntity<E> {
    val returnClass = ClassConversion.entityDataToModifiableEntity(this::class)
    val res = returnClass.java.getDeclaredConstructor().newInstance()
    res as ModifiableWorkspaceEntityBase
    res.diff = diff
    res.id = createEntityId()
    res.entitySource = this.entitySource
    return res
  }

  @Suppress("UNCHECKED_CAST")
  public override fun clone(): WorkspaceEntityData<E> = super.clone() as WorkspaceEntityData<E>

/*
  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    return ReflectionUtil.collectFields(this.javaClass).filterNot { it.name == WorkspaceEntityData<*>::id.name }
      .onEach { it.isAccessible = true }
      .all { it.get(this) == it.get(other) }
  }
*/

  open fun equalsIgnoringEntitySource(other: Any?): Boolean {
    return this == other
  }


  override fun toString(): String {
    return "${this::class.simpleName}($ id=${this.id})"
  }

  /**
   * Temporally solution.
   * Get persistent Id without creating of TypedEntity. Should be in sync with TypedEntityWithPersistentId.
   * But it doesn't everywhere. E.g. FacetEntity where we should resolve module before creating persistent id.
   */
  abstract class WithCalculablePersistentId<E : WorkspaceEntity> : WorkspaceEntityData<E>() {
    abstract fun persistentId(): PersistentEntityId<*>
  }
}

fun WorkspaceEntityData<*>.persistentId(): PersistentEntityId<*>? = when (this) {
  is WorkspaceEntityData.WithCalculablePersistentId -> this.persistentId()
  else -> null
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