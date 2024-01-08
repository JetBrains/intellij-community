// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.indices.VirtualFileIndex
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.trace.ReadTrace
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.ReflectionUtil

public abstract class WorkspaceEntityBase(private var currentEntityData: WorkspaceEntityData<out WorkspaceEntity>? = null) : WorkspaceEntity {
  public var id: EntityId = invalidEntityId

  public lateinit var snapshot: EntityStorage
  internal var onRead: ((ReadTrace) -> Unit)? = null

  public abstract fun connectionIdList(): List<ConnectionId>

  /**
   * Record information that some field was read. This function is used only for fields with primitive values.
   *   Reading of references to other entities doesn't use this function.
   *
   * [name] is passed for future use
   */
  @Suppress("UNUSED_PARAMETER")
  protected fun readField(name: String) {
    onRead?.invoke(ReadTrace.SomeFieldAccess(id))
  }

  public open fun <R : WorkspaceEntity> referrers(entityClass: Class<R>): Sequence<R> {
    val mySnapshot = snapshot as AbstractEntityStorage
    return getReferences(mySnapshot, entityClass)
  }

  internal fun <R : WorkspaceEntity> getReferences(mySnapshot: AbstractEntityStorage, entityClass: Class<R>,
                                                   checkReversedConnection: Boolean = false): Sequence<R> {
    var connectionId = mySnapshot.refs.findConnectionId(getEntityInterface(), entityClass)
    if (connectionId != null) {
      val entitiesSequence = when (connectionId.connectionType) {
        ConnectionId.ConnectionType.ONE_TO_MANY -> mySnapshot.getManyChildren(connectionId, this)
        ConnectionId.ConnectionType.ONE_TO_ONE -> mySnapshot.getOneChild(connectionId, this)
          ?.let { sequenceOf(it) }
          ?: emptySequence()
        ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> mySnapshot.getManyChildren(connectionId, this)
        ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> mySnapshot.getOneChild(connectionId, this)?.let {
          sequenceOf(it)
        } ?: emptySequence()
      } as Sequence<R>
      // If the resulting sequence is empty, and its connection between two entities of the same type, we should continue search
      if (!checkReversedConnection || entitiesSequence.any() || getEntityInterface() != entityClass) {
        return entitiesSequence
      }
    }
    connectionId = mySnapshot.refs.findConnectionId(entityClass, getEntityInterface())
    if (connectionId != null) {
      return when (connectionId.connectionType) {
        ConnectionId.ConnectionType.ONE_TO_MANY -> mySnapshot.getParent(connectionId, this)?.let { sequenceOf(it) } ?: emptySequence()
        ConnectionId.ConnectionType.ONE_TO_ONE -> mySnapshot.getParent(connectionId, this)
          ?.let { sequenceOf(it) }
          ?: emptySequence()
        ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> mySnapshot.getParent(connectionId, this)
          ?.let { sequenceOf(it) }
          ?: emptySequence()
        ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> mySnapshot.getParent(connectionId, this)?.let {
          sequenceOf(it)
        } ?: emptySequence()
      } as Sequence<R>
    }
    return emptySequence()
  }

  override fun <E : WorkspaceEntity> createPointer(): EntityPointer<E> {
    return EntityPointerImpl(this.id)
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> = id.clazz.findWorkspaceEntity()


  internal open fun getData(): WorkspaceEntityData<out WorkspaceEntity> =
    currentEntityData ?: throw IllegalStateException("Entity data is not initialized")

  internal fun getMetadata(): EntityMetadata = getData().getMetadata()


  override fun toString(): String = id.asString()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is WorkspaceEntityBase) return false

    if (id != other.id) return false
    @Suppress("RedundantIf")
    if ((this.snapshot as AbstractEntityStorage).entityDataById(id) !==
      (other.snapshot as AbstractEntityStorage).entityDataById(other.id)
    ) return false

    return true
  }

  override fun hashCode(): Int = id.hashCode()
}

public data class EntityLink(
  val isThisFieldChild: Boolean,
  val connectionId: ConnectionId,
)

internal val EntityLink.remote: EntityLink
  get() = EntityLink(!this.isThisFieldChild, connectionId)

public abstract class ModifiableWorkspaceEntityBase<T : WorkspaceEntity, E: WorkspaceEntityData<T>>(protected var currentEntityData: E?) : WorkspaceEntityBase(currentEntityData), WorkspaceEntity.Builder<T> {
  /**
   * In case any of two referred entities is not added to diff, the reference between entities will be stored in this field
   */
  public val entityLinks: MutableMap<EntityLink, Any?> = HashMap()

  internal lateinit var original: WorkspaceEntityData<T>
  public var diff: MutableEntityStorage? = null

  public val modifiable: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }
  public val changedProperty: MutableSet<String> = mutableSetOf()

  public fun updateChildToParentReferences(parents: Set<WorkspaceEntity>?) {
    if (diff == null) return
    val childId = getEntityData().createEntityId().asChild()
    val entityInterfaceToEntity = parents
                                    ?.associateBy { it.getEntityInterface() }
                                    ?.toMutableMap() ?: mutableMapOf()
    val idToInterface = parents?.associate { it.asBase().id to it.getEntityInterface() } ?: emptyMap()

    (diff as MutableEntityStorageImpl).refs.getParentRefsOfChild(childId).forEach { (connectionId, existingParent) ->
      val interfaceOfParent = idToInterface[existingParent.id]
      if (interfaceOfParent != null) {
        // We're trying to add parent that already exists. Skip it
        entityInterfaceToEntity.remove(interfaceOfParent)
        return@forEach
      }
      val parentEntityClass = connectionId.parentClass.findWorkspaceEntity()
      // Remove outdated references
      if (!entityInterfaceToEntity.contains(parentEntityClass)) {
        updateReferenceToEntity(parentEntityClass, false, listOf(null))
      }
    }
    // Update existing references
    entityInterfaceToEntity.forEach { (parentEntityClass, parentEntity) ->
      updateReferenceToEntity(parentEntityClass, false, listOf(parentEntity))
    }
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  public fun updateReferenceToEntity(entityClass: Class<out WorkspaceEntity>, isThisFieldChild: Boolean, entities: List<WorkspaceEntity?>) {
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
            if (item != null && item is ModifiableWorkspaceEntityBase<*, *> && item.diff == null) {
              @Suppress("KotlinConstantConditions")
              item.entityLinks[EntityLink(!isThisFieldChild, foundConnectionId)] =  this
              myDiff.addEntity(item)
            }
          }
          if (foundConnectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY) {
            myDiff.instrumentation.replaceChildren(foundConnectionId, this, entities.filterNotNull())
          } else {
            myDiff.instrumentation.replaceChildren(foundConnectionId, this, entities.filterNotNull())
          }
        } else {
          // One - to -one connection
          val item = entities.single()
          if (item != null && item is ModifiableWorkspaceEntityBase<*, *> && item.diff == null) {
            @Suppress("KotlinConstantConditions")
            item.entityLinks[EntityLink(!isThisFieldChild, foundConnectionId)] = this
            myDiff.addEntity(item)
          }
          if (foundConnectionId.connectionType == ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE) {
            myDiff.instrumentation.replaceChildren(foundConnectionId, this, listOfNotNull(item))
          } else {
            myDiff.instrumentation.replaceChildren(foundConnectionId, this, listOfNotNull(item))
          }
        }
      }
      else {
        // Branch for case `this` entity is a child
        if (foundConnectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_MANY || foundConnectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY) {
          // One - to - many connection
          val item = entities.single()
          if (item != null && item is ModifiableWorkspaceEntityBase<*, *> && item.diff == null) {
            @Suppress("KotlinConstantConditions", "UNCHECKED_CAST")
            item.entityLinks[EntityLink(!isThisFieldChild, foundConnectionId)] = (item.entityLinks[EntityLink(!isThisFieldChild, foundConnectionId)] as? List<Any> ?: emptyList()) + this
            myDiff.addEntity(item)
          }
          if (foundConnectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY) {
            myDiff.instrumentation.addChild(foundConnectionId, item, this)
          }
          else {
            myDiff.instrumentation.addChild(foundConnectionId, item, this)
          }
        }
        else {
          // One - to -one connection
          val item = entities.single()
          if (item != null && item is ModifiableWorkspaceEntityBase<*, *> && item.diff == null) {
            @Suppress("KotlinConstantConditions")
            item.entityLinks[EntityLink(!isThisFieldChild, foundConnectionId)] = this
            myDiff.addEntity(item)
          }
          if (foundConnectionId.connectionType == ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE) {
            myDiff.instrumentation.addChild(foundConnectionId, item, this)
          }
          else {
            myDiff.instrumentation.addChild(foundConnectionId, item, this)
          }
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
            if (item != null && item is ModifiableWorkspaceEntityBase<*, *>) {
              @Suppress("KotlinConstantConditions")
              item.entityLinks[EntityLink(!isThisFieldChild, foundConnectionId)] = this
            }
          }
        } else {
          // One - to -one connection
          val item = entities.single()
          @Suppress("KotlinConstantConditions")
          this.entityLinks[EntityLink(isThisFieldChild, foundConnectionId)] = item
          if (item != null && item is ModifiableWorkspaceEntityBase<*, *>) {
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
          if (item != null && item is ModifiableWorkspaceEntityBase<*, *>) {
            @Suppress("KotlinConstantConditions", "UNCHECKED_CAST")
            item.entityLinks[EntityLink(!isThisFieldChild, foundConnectionId)] = (item.entityLinks[EntityLink(!isThisFieldChild, foundConnectionId)] as? List<Any> ?: emptyList()) + this
          }
        } else {
          // One - to -one connection
          val item = entities.single()

          @Suppress("KotlinConstantConditions")
          this.entityLinks[EntityLink(isThisFieldChild, foundConnectionId)] = item
          if (item != null && item is ModifiableWorkspaceEntityBase<*, *>) {
            @Suppress("KotlinConstantConditions")
            item.entityLinks[EntityLink(!isThisFieldChild, foundConnectionId)] = this
          }
        }
      }
    }
  }

  private fun findConnectionId(entityClass: Class<out WorkspaceEntity>, entity: List<WorkspaceEntity?>): ConnectionId? {
    val someEntity = entity.filterNotNull().firstOrNull()
    val firstClass = this.getEntityClass()
    val connectionChecker = { connectionId: ConnectionId -> isCorrectConnection(connectionId, firstClass, entityClass)
                                                            || isCorrectConnection(connectionId, entityClass, firstClass) }
    if (someEntity != null) {
      someEntity as WorkspaceEntityBase
      val resultingConnection = someEntity.connectionIdList().firstOrNull(connectionChecker)
      if (resultingConnection != null) return resultingConnection
      return this.connectionIdList().first(connectionChecker)
    }
    else {
      val resultingConnection = entityLinks.keys.asSequence().map { it.connectionId }.firstOrNull(connectionChecker)
      if (resultingConnection != null) return resultingConnection
      // Attempt to find connection by old entities still existing in storage
      val connectionsFromOldEntities = (referrers(entityClass, true).firstOrNull() as? WorkspaceEntityBase)?.connectionIdList()
                                       ?: emptyList()
      // It's okay to have two identical connections e.g. if entity linked to themselves as parent and child
      return connectionsFromOldEntities.firstOrNull(connectionChecker)
    }
  }

  private fun isCorrectConnection(it: ConnectionId, parentClass: Class<out WorkspaceEntity>, childClass: Class<out WorkspaceEntity>): Boolean {
    return it.parentClass == parentClass.toClassId() && it.childClass == childClass.toClassId() ||
           it.parentClass.findWorkspaceEntity().isAssignableFrom(parentClass) &&
           it.childClass.findWorkspaceEntity().isAssignableFrom(childClass)
  }

  override fun <R : WorkspaceEntity> referrers(entityClass: Class<R>): Sequence<R> {
    return referrers(entityClass, false)
  }

  private fun <R : WorkspaceEntity> referrers(entityClass: Class<R>, checkReversedConnection: Boolean): Sequence<R> {
    val myDiff = diff
    val entitiesFromDiff = if (myDiff != null) {
      getReferences(myDiff as AbstractEntityStorage, entityClass, checkReversedConnection)
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

  internal inline fun allowModifications(action: () -> Unit) {
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

  public abstract fun getEntityClass(): Class<T>

  public open fun applyToBuilder(builder: MutableEntityStorage) {
    throw NotImplementedError()
  }

  public open fun afterModification() { }

  public fun processLinkedEntities(builder: MutableEntityStorage) {
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
        else if (data is ModifiableWorkspaceEntityBase<*, *>) {
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

  @OptIn(EntityStorageInstrumentationApi::class)
  private fun processLinkedParentEntity(entity: Any?,
                                        builder: MutableEntityStorage,
                                        entityLink: EntityLink,
                                        parentKeysToRemove: ArrayList<EntityLink>) {
    if (entity is List<*>) {
      error("Cannot have parent lists")
    }
    else if (entity is WorkspaceEntity) {
      if (entity is ModifiableWorkspaceEntityBase<*, *> && entity.diff == null) {
        builder.addEntity(entity)
      }
      builder.instrumentation.addChild(entityLink.connectionId, entity, this)
      parentKeysToRemove.add(entityLink)
    }
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  private fun processLinkedChildEntity(entity: Any?,
                                       builder: MutableEntityStorage,
                                       connectionId: ConnectionId) {
    if (entity is List<*>) {
      for (item in entity) {
        if (item is ModifiableWorkspaceEntityBase<*, *>) {
          builder.addEntity(item)
        }
      }
      if (connectionId.isOneToOne) error("Only one-to-many connection is supported")
      @Suppress("UNCHECKED_CAST")
      entity as List<WorkspaceEntity>
      val withBuilder_entity = entity.filter { it is ModifiableWorkspaceEntityBase<*, *> && it.diff != null }
      builder.instrumentation.replaceChildren(connectionId, this, withBuilder_entity)
    }
    else if (entity is WorkspaceEntity) {
      if (entity is ModifiableWorkspaceEntityBase<*, *> && entity.diff == null) {
        builder.addEntity(entity)
      }
      if (!connectionId.isOneToOne) error("Only one-to-one connection is supported")
      builder.instrumentation.replaceChildren(connectionId, this, listOfNotNull(entity))
    }
  }

  override fun getData(): WorkspaceEntityData<out WorkspaceEntity> = this.getEntityData()

  public fun getEntityData(supposedModification: Boolean = false): E {
    if (currentEntityData != null) return currentEntityData!!
    val actualEntityData = if (supposedModification) {
      (diff as MutableEntityStorageImpl).entitiesByType.getEntityDataForModificationOrNull(id)
    } else {
      (diff as MutableEntityStorageImpl).entitiesByType[id.clazz]?.get(id.arrayId)
    } ?: error("Cannot find the data. Must probably this entity was already remove from builder.")

    @Suppress("UNCHECKED_CAST")
    return actualEntityData as E
  }

  // For generated entities
  public fun addToBuilder() {
    val builder = diff as MutableEntityStorageImpl
    builder.putEntity(this)
  }

  public fun existsInBuilder(builder: MutableEntityStorage): Boolean {
    builder as MutableEntityStorageImpl
    val entityData = getEntityData()
    return builder.entityDataById(entityData.createEntityId()) != null
  }

  // For generated entities
  public fun index(entity: WorkspaceEntity, propertyName: String, virtualFileUrl: VirtualFileUrl?) {
    val builder = diff as MutableEntityStorageImpl
    builder.getMutableVirtualFileUrlIndex().index(entity, propertyName, virtualFileUrl)
  }

  // For generated entities
  public fun index(entity: WorkspaceEntity, propertyName: String, virtualFileUrls: Collection<VirtualFileUrl>) {
    val builder = diff as MutableEntityStorageImpl
    (builder.getMutableVirtualFileUrlIndex() as VirtualFileIndex.MutableVirtualFileIndex).index((entity as WorkspaceEntityBase).id,
                                                                                                propertyName, virtualFileUrls)
  }

  // For generated entities
  public fun indexJarDirectories(entity: WorkspaceEntity, virtualFileUrls: Set<VirtualFileUrl>) {
    val builder = diff as MutableEntityStorageImpl
    (builder.getMutableVirtualFileUrlIndex() as VirtualFileIndex.MutableVirtualFileIndex).indexJarDirectories(
      (entity as WorkspaceEntityBase).id, virtualFileUrls)
  }

  /**
   * For generated entities
   * Pull information from [dataSource] and puts into the current builder.
   * Only non-reference fields are moved from [dataSource]
   */
  public open fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
    throw NotImplementedError()
  }
}

public interface SoftLinkable {
  public fun getLinks(): Set<SymbolicEntityId<*>>
  public fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>)
  public fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>)
  public fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean
}

public abstract class WorkspaceEntityData<E : WorkspaceEntity> : Cloneable, SerializableEntityData {
  public lateinit var entitySource: EntitySource
  public var id: Int = -1

  public fun isEntitySourceInitialized(): Boolean = ::entitySource.isInitialized

  public fun createEntityId(): EntityId = createEntityId(id, getEntityInterface().toClassId())

  @OptIn(EntityStorageInstrumentationApi::class)
  public abstract fun createEntity(snapshot: EntityStorageInstrumentation): E

  public abstract fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<E>

  public abstract fun getEntityInterface(): Class<out WorkspaceEntity>

  public abstract fun getMetadata(): EntityMetadata

  @Suppress("UNCHECKED_CAST")
  public override fun clone(): WorkspaceEntityData<E> = super.clone() as WorkspaceEntityData<E>

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    return ReflectionUtil.collectFields(this.javaClass).filterNot { it.name == WorkspaceEntityData<*>::id.name }
      .onEach { it.isAccessible = true }
      .all { it.get(this) == it.get(other) }
  }

  public open fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    return ReflectionUtil.collectFields(this.javaClass)
      .filterNot { it.name == WorkspaceEntityData<*>::id.name }
      .filterNot { it.name == WorkspaceEntityData<*>::entitySource.name }
      .onEach { it.isAccessible = true }
      .all { it.get(this) == it.get(other) }
  }

  public open fun equalsByKey(other: Any?): Boolean {
    return equalsIgnoringEntitySource(other)
  }

  public open fun hashCodeByKey(): Int {
    return hashCodeIgnoringEntitySource()
  }

  override fun hashCode(): Int {
    return ReflectionUtil.collectFields(this.javaClass).filterNot { it.name == WorkspaceEntityData<*>::id.name }
      .onEach { it.isAccessible = true }
      .mapNotNull { it.get(this)?.hashCode() }
      .fold(31) { acc, i -> acc * 17 + i }
  }

  public open fun hashCodeIgnoringEntitySource(): Int {
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

  public open fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    throw NotImplementedError()
  }

  public open fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    throw NotImplementedError()
  }

  /**
   * Temporally solution.
   * Get symbolic Id without creating of TypedEntity. Should be in sync with TypedEntityWithSymbolicId.
   * But it doesn't everywhere. E.g. FacetEntity where we should resolve module before creating symbolic id.
   */
  public abstract class WithCalculableSymbolicId<E : WorkspaceEntity> : WorkspaceEntityData<E>() {
    public abstract fun symbolicId(): SymbolicEntityId<*>
  }
}

internal fun WorkspaceEntityData<*>.symbolicId(): SymbolicEntityId<*>? = when (this) {
  is WorkspaceEntityData.WithCalculableSymbolicId -> this.symbolicId()
  else -> null
}
