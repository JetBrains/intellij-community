// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.impl

import com.intellij.util.ReflectionUtil
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileIndex
import com.intellij.workspaceModel.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import kotlin.reflect.KClass


/**
 *
 * THIS INFORMATION IS COMPLETELY OUTDATED. Please, CONTACT #ij-workspace-model IF YOU NEED TO CREATE A NEW ENTITY.
 *
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
 *      fun persistentId = NameId(name)
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


abstract class WorkspaceEntityBase : WorkspaceEntity, Any() {
  override lateinit var entitySource: EntitySource
    internal set

  var id: EntityId = invalidEntityId

  lateinit var snapshot: EntityStorage

  abstract fun connectionIdList(): List<ConnectionId>

  open fun <R : WorkspaceEntity> referrers(entityClass: Class<R>): Sequence<R> {
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

  override fun getEntityInterface(): Class<out WorkspaceEntity> = id.clazz.findWorkspaceEntity()

  override fun toString(): String = id.asString()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is WorkspaceEntityBase) return false

    if (id != other.id) return false
    if ((this.snapshot as AbstractEntityStorage).entityDataById(id) !==
      (other.snapshot as AbstractEntityStorage).entityDataById(other.id)
    ) return false

    return true
  }

  override fun hashCode(): Int = id.hashCode()
}

data class EntityLink(
  val isThisFieldChild: Boolean,
  val connectionId: ConnectionId,
)

val EntityLink.remote: EntityLink
  get() = EntityLink(!this.isThisFieldChild, connectionId)

abstract class ModifiableWorkspaceEntityBase<T : WorkspaceEntity> : WorkspaceEntityBase(), ModifiableWorkspaceEntity<T> {
  /**
   * In case any of two referred entities is not added to diff, the reference between entities will be stored in this field
   */
  val entityLinks: MutableMap<EntityLink, Any?> = HashMap()

  internal lateinit var original: WorkspaceEntityData<T>
  var diff: MutableEntityStorage? = null

  val modifiable: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }
  val changedProperty: MutableSet<String> = mutableSetOf()

  fun linkExternalEntity(entityClass: KClass<out WorkspaceEntity>, isThisFieldChild: Boolean, entities: List<WorkspaceEntity?>) {
    val foundConnectionId = findConnectionId(entityClass, entities)
    if (foundConnectionId == null) return

    // If the diff is empty, we should link entities using the internal map
    // If it's not, we should add entity to store and update indexes
    val myDiff = diff
    if (myDiff != null) {
      //if (foundConnectionId.parentClass == getEntityClass().toClassId()) {
      if (isThisFieldChild) {
        // Branch for case `this` entity is a parent
        if (foundConnectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_MANY || foundConnectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY) {
          // One - to - many connection
          for (item in entities) {
            if (item != null && item is ModifiableWorkspaceEntityBase<*> && item.diff == null) {
              @Suppress("KotlinConstantConditions")
              item.entityLinks[EntityLink(!isThisFieldChild, foundConnectionId)] =  this
              myDiff.addEntity(item)
            }
          }
          myDiff.updateOneToManyChildrenOfParent(foundConnectionId, this, entities.filterNotNull())
        } else {
          // One - to -one connection
          val item = entities.single()
          if (item != null && item is ModifiableWorkspaceEntityBase<*> && item.diff == null) {
            @Suppress("KotlinConstantConditions")
            item.entityLinks[EntityLink(!isThisFieldChild, foundConnectionId)] = this
            myDiff.addEntity(item)
          }
          myDiff.updateOneToOneChildOfParent(foundConnectionId, this, item)
        }
      }
      else {
        // Branch for case `this` entity is a child
        if (foundConnectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_MANY || foundConnectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY) {
          // One - to - many connection
          val item = entities.single()
          if (item != null && item is ModifiableWorkspaceEntityBase<*> && item.diff == null) {
            @Suppress("KotlinConstantConditions", "UNCHECKED_CAST")
            item.entityLinks[EntityLink(!isThisFieldChild, foundConnectionId)] = (item.entityLinks[EntityLink(!isThisFieldChild, foundConnectionId)] as? List<Any> ?: emptyList()) + this
            myDiff.addEntity(item)
          }
          myDiff.updateOneToManyParentOfChild(foundConnectionId, this, item)
        }
        else {
          // One - to -one connection
          val item = entities.single()
          if (item != null && item is ModifiableWorkspaceEntityBase<*> && item.diff == null) {
            @Suppress("KotlinConstantConditions")
            item.entityLinks[EntityLink(!isThisFieldChild, foundConnectionId)] = this
            myDiff.addEntity(item)
          }
          myDiff.updateOneToOneParentOfChild(foundConnectionId, this, item)
        }
      }
    }
    else {
      //if (foundConnectionId.parentClass == getEntityClass().toClassId()) {
      if (isThisFieldChild) {
        // Branch for case `this` entity is a parent
        if (foundConnectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_MANY || foundConnectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY) {
          // One - to - many connection
          @Suppress("KotlinConstantConditions")
          this.entityLinks[EntityLink(isThisFieldChild, foundConnectionId)] = entities
          for (item in entities) {
            if (item != null && item is ModifiableWorkspaceEntityBase<*>) {
              @Suppress("KotlinConstantConditions")
              item.entityLinks[EntityLink(!isThisFieldChild, foundConnectionId)] = this
            }
          }
        } else {
          // One - to -one connection
          val item = entities.single()
          @Suppress("KotlinConstantConditions")
          this.entityLinks[EntityLink(isThisFieldChild, foundConnectionId)] = item
          if (item != null && item is ModifiableWorkspaceEntityBase<*>) {
            @Suppress("KotlinConstantConditions")
            item.entityLinks[EntityLink(!isThisFieldChild, foundConnectionId)] = this
          }
        }
      }
      else {
        // Branch for case `this` entity is a child
        if (foundConnectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_MANY || foundConnectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY) {
          // One - to - many connection
          val item = entities.single()

          @Suppress("KotlinConstantConditions")
          this.entityLinks[EntityLink(isThisFieldChild, foundConnectionId)] = item
          if (item != null && item is ModifiableWorkspaceEntityBase<*>) {
            @Suppress("KotlinConstantConditions", "UNCHECKED_CAST")
            item.entityLinks[EntityLink(!isThisFieldChild, foundConnectionId)] = (item.entityLinks[EntityLink(!isThisFieldChild, foundConnectionId)] as? List<Any> ?: emptyList()) + this
          }
        } else {
          // One - to -one connection
          val item = entities.single()

          @Suppress("KotlinConstantConditions")
          this.entityLinks[EntityLink(isThisFieldChild, foundConnectionId)] = item
          if (item != null && item is ModifiableWorkspaceEntityBase<*>) {
            @Suppress("KotlinConstantConditions")
            item.entityLinks[EntityLink(!isThisFieldChild, foundConnectionId)] = this
          }
        }
      }
    }
  }

  private fun findConnectionId(entityClass: KClass<out WorkspaceEntity>, entity: List<WorkspaceEntity?>): ConnectionId? {
    val someEntity = entity.filterNotNull().firstOrNull()
    return if (someEntity != null) {
      val firstClass = this.getEntityClass()
      (someEntity as WorkspaceEntityBase).connectionIdList().single { it.parentClass == firstClass.toClassId() && it.childClass == entityClass.java.toClassId() || it.childClass == firstClass.toClassId() && it.parentClass == entityClass.java.toClassId() }
    }
    else {
      val firstClass = this.getEntityClass()
      entityLinks.keys.asSequence().map { it.connectionId }.singleOrNull { it.parentClass == firstClass.toClassId() && it.childClass == entityClass.java.toClassId() || it.childClass == firstClass.toClassId() && it.parentClass == entityClass.java.toClassId() }
    }
  }

  override fun <R : WorkspaceEntity> referrers(entityClass: Class<R>): Sequence<R> {
    val myDiff = diff
    val entitiesFromDiff = if (myDiff != null) {
      getReferences(myDiff as AbstractEntityStorage, entityClass)
    } else emptySequence()

    val entityClassId = entityClass.toClassId()
    val thisClassId = getEntityClass().toClassId()
    val res = entityLinks.entries.singleOrNull {
      it.key.connectionId.parentClass == entityClassId && it.key.connectionId.childClass == thisClassId
      || it.key.connectionId.parentClass == thisClassId && it.key.connectionId.childClass == entityClassId
    }?.value
    return entitiesFromDiff + if (res == null) {
      emptySequence()
    } else {
      if (res is List<*>) {
        @Suppress("UNCHECKED_CAST")
        res.asSequence() as Sequence<R>
      } else {
        @Suppress("UNCHECKED_CAST")
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

  open fun applyToBuilder(builder: MutableEntityStorage) {
    throw NotImplementedError()
  }

  fun processLinkedEntities(builder: MutableEntityStorage) {
    val parentKeysToRemove = ArrayList<EntityLink>()
    for ((key, entity) in HashMap(entityLinks)) {
      if (key.isThisFieldChild) {
        processLinkedChildEntity(entity, builder, key.connectionId)
      }
      else {
        processLinkedParentEntity(entity, builder, key, parentKeysToRemove)
      }
    }
    for (key in parentKeysToRemove) {
      val data = entityLinks[key]
      if (data != null) {
        if (data is List<*>) {
          error("Cannot have parent lists")
        }
        else if (data is ModifiableWorkspaceEntityBase<*>) {
          val remoteData = data.entityLinks[key.remote]
          if (remoteData != null) {
            if (remoteData is List<*>) {
              data.entityLinks[key.remote] = remoteData.filterNot { it === this }
            }
            else {
              data.entityLinks.remove(key.remote)
            }
          }
          this.entityLinks.remove(key)
        }
      }
    }
  }

  private fun processLinkedParentEntity(entity: Any?,
                                        builder: MutableEntityStorage,
                                        entityLink: EntityLink,
                                        parentKeysToRemove: ArrayList<EntityLink>) {
    if (entity is List<*>) {
      error("Cannot have parent lists")
    }
    else if (entity is WorkspaceEntity) {
      if (entity is ModifiableWorkspaceEntityBase<*> && entity.diff == null) {
        builder.addEntity(entity)
      }
      applyParentRef(entityLink.connectionId, entity)
      parentKeysToRemove.add(entityLink)
    }
  }

  private fun processLinkedChildEntity(entity: Any?,
                                       builder: MutableEntityStorage,
                                       connectionId: ConnectionId) {
    if (entity is List<*>) {
      for (item in entity) {
        if (item is ModifiableWorkspaceEntityBase<*>) {
          builder.addEntity(item)
        }
        @Suppress("UNCHECKED_CAST")
        entity as List<WorkspaceEntity>
        val withBuilder_entity = entity.filter { it is ModifiableWorkspaceEntityBase<*> && it.diff != null }
        applyRef(connectionId, withBuilder_entity)
      }
    }
    else if (entity is WorkspaceEntity) {
      builder.addEntity(entity)
      applyRef(connectionId, entity)
    }
  }

  open fun getEntityData(): WorkspaceEntityData<T> {
    val actualEntityData = (diff as MutableEntityStorageImpl).entityDataById(id)
      ?: error("Requested entity data doesn't exist at entity family")
    @Suppress("UNCHECKED_CAST")
    return actualEntityData as WorkspaceEntityData<T>
  }

  // For generated entities
  fun addToBuilder() {
    val builder = diff as MutableEntityStorageImpl
    builder.putEntity(this)
  }

  // For generated entities
  private fun applyRef(connectionId: ConnectionId, child: WorkspaceEntity) {
    val builder = diff as MutableEntityStorageImpl
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_ONE -> builder.updateOneToOneChildOfParent(connectionId, this, child)
      ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> builder.updateOneToAbstractOneChildOfParent(connectionId, this, child)
      else -> error("Unexpected branch")
    }
  }

  private fun applyRef(connectionId: ConnectionId, children: List<WorkspaceEntity>) {
    val builder = diff as MutableEntityStorageImpl
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_MANY -> builder.updateOneToManyChildrenOfParent(connectionId, this, children)
      ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> builder.updateOneToAbstractManyChildrenOfParent(connectionId, this, children.asSequence())
      else -> error("Unexpected branch")
    }
  }

  private fun applyParentRef(connectionId: ConnectionId, parent: WorkspaceEntity) {
    val builder = diff as MutableEntityStorageImpl
    when (connectionId.connectionType) {
      ConnectionId.ConnectionType.ONE_TO_ONE -> builder.updateOneToOneParentOfChild(connectionId, this, parent)
      ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> builder.updateOneToAbstractOneParentOfChild(connectionId, this, parent)
      ConnectionId.ConnectionType.ONE_TO_MANY -> builder.updateOneToManyParentOfChild(connectionId, this, parent)
      ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> builder.updateOneToAbstractManyParentOfChild(connectionId, this, parent)
    }
  }

  fun existsInBuilder(builder: MutableEntityStorage): Boolean {
    builder as MutableEntityStorageImpl
    val entityData = getEntityData()
    return builder.entityDataById(entityData.createEntityId()) != null
  }

  // For generated entities
  fun index(entity: WorkspaceEntity, propertyName: String, virtualFileUrl: VirtualFileUrl?) {
    val builder = diff as MutableEntityStorageImpl
    builder.getMutableVirtualFileUrlIndex().index(entity, propertyName, virtualFileUrl)
  }

  // For generated entities
  fun index(entity: WorkspaceEntity, propertyName: String, virtualFileUrls: Set<VirtualFileUrl>) {
    val builder = diff as MutableEntityStorageImpl
    (builder.getMutableVirtualFileUrlIndex() as VirtualFileIndex.MutableVirtualFileIndex).index((entity as WorkspaceEntityBase).id,
                                                                                                propertyName, virtualFileUrls)
  }

  // For generated entities
  fun indexJarDirectories(entity: WorkspaceEntity, virtualFileUrls: Set<VirtualFileUrl>) {
    val builder = diff as MutableEntityStorageImpl
    (builder.getMutableVirtualFileUrlIndex() as VirtualFileIndex.MutableVirtualFileIndex).indexJarDirectories(
      (entity as WorkspaceEntityBase).id, virtualFileUrls)
  }

  /**
   * For generated entities
   * Pull information from [dataSource] and puts into the current builder.
   * Only non-reference fields are moved from [dataSource]
   */
  open fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
    throw NotImplementedError()
  }
}

interface SoftLinkable {
  fun getLinks(): Set<PersistentEntityId<*>>
  fun index(index: WorkspaceMutableIndex<PersistentEntityId<*>>)
  fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: WorkspaceMutableIndex<PersistentEntityId<*>>)
  fun updateLink(oldLink: PersistentEntityId<*>, newLink: PersistentEntityId<*>): Boolean
}

abstract class WorkspaceEntityData<E : WorkspaceEntity> : Cloneable, SerializableEntityData {
  lateinit var entitySource: EntitySource
  var id: Int = -1

  fun isEntitySourceInitialized(): Boolean = ::entitySource.isInitialized

  fun createEntityId(): EntityId = createEntityId(id, getEntityInterface().toClassId())

  abstract fun createEntity(snapshot: EntityStorage): E

  abstract fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<E>

  abstract fun getEntityInterface(): Class<out WorkspaceEntity>

  @Suppress("UNCHECKED_CAST")
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

  open fun equalsByKey(other: Any?): Boolean {
    return equalsIgnoringEntitySource(other)
  }

  open fun hashCodeByKey(): Int {
    return hashCodeIgnoringEntitySource()
  }

  override fun hashCode(): Int {
    return ReflectionUtil.collectFields(this.javaClass).filterNot { it.name == WorkspaceEntityData<*>::id.name }
      .onEach { it.isAccessible = true }
      .mapNotNull { it.get(this)?.hashCode() }
      .fold(31) { acc, i -> acc * 17 + i }
  }

  open fun hashCodeIgnoringEntitySource(): Int {
    return ReflectionUtil.collectFields(this.javaClass)
      .filterNot { it.name == WorkspaceEntityData<*>::id.name }
      .filterNot { it.name == WorkspaceEntityData<*>::entitySource.name }
      .onEach { it.isAccessible = true }
      .mapNotNull { it.get(this)?.hashCode() }
      .fold(31) { acc, i -> acc * 17 + i }
  }

  override fun toString(): String {
    val fields = ReflectionUtil.collectFields(this.javaClass).toList().onEach { it.isAccessible = true }
      .joinToString(separator = ", ") { f -> "${f.name}=${f.get(this)}" }
    return "${this::class.simpleName}($fields, id=${this.id})"
  }

  open fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    throw NotImplementedError()
  }

  open fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    throw NotImplementedError()
  }

  open fun collectClassUsagesData(collector: UsedClassesCollector) {
    throw NotGeneratedMethodRuntimeException("collectClassUsagesData")
  }

  /**
   * Temporally solution.
   * Get persistent Id without creating of TypedEntity. Should be in sync with TypedEntityWithPersistentId.
   * But it doesn't everywhere. E.g. FacetEntity where we should resolve module before creating persistent id.
   */
  abstract class WithCalculablePersistentId<E : WorkspaceEntity> : WorkspaceEntityData<E>() {
    abstract fun persistentId(): PersistentEntityId<*>
  }

  protected fun <T: WorkspaceEntity> getCached(storage: EntityStorage, init: () -> T): T {
    if (storage is EntityStorageSnapshotImpl) {
      return storage.getCachedEntityById(createEntityId(), init) as T
    }
    else {
      return init()
    }
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
 * [assertConsistency] method is called during [MutableEntityStorageImpl.assertConsistency].
 */
interface WithAssertableConsistency {
  fun assertConsistency(storage: EntityStorage)
}

class UsedClassesCollector(
  var sameForAllEntities: Boolean = false,
  var collection: MutableSet<Class<out Any>> = HashSet(),
  var collectionObjects: MutableSet<Class<out Any>> = HashSet(),
  var collectionToInspection: MutableSet<Any> = HashSet(),
) {
  fun add(clazz: Class<out Any>) {
    collection.add(clazz)
  }

  fun addObject(clazz: Class<out Any>) {
    collectionObjects.add(clazz)
  }

  fun addDataToInspect(data: Any) {
    collectionToInspection.add(data)
  }
}
