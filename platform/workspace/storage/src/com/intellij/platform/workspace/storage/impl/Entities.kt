// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntityPointer
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.indices.VirtualFileIndex
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.trace.ReadTrace
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.ReflectionUtil
import org.jetbrains.annotations.ApiStatus

@WorkspaceEntityInternalApi
public abstract class WorkspaceEntityBase(private var currentEntityData: WorkspaceEntityData<out WorkspaceEntity>? = null) : WorkspaceEntity {
  internal var id: EntityId = invalidEntityId

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

  @ApiStatus.Internal
  public open fun getData(): WorkspaceEntityData<out WorkspaceEntity> =
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

@WorkspaceEntityInternalApi
public abstract class ModifiableWorkspaceEntityBase<T : WorkspaceEntity, E: WorkspaceEntityData<T>>(protected var currentEntityData: E?) : WorkspaceEntity.Builder<T> {
  internal var id: EntityId = invalidEntityId
  public abstract fun connectionIdList(): List<ConnectionId>

  /**
   * In case any of two referred entities is not added to diff, the reference between entities will be stored in this field
   */
  public val entityLinks: MutableMap<EntityLink, Any?> = HashMap()

  internal lateinit var original: WorkspaceEntityData<T>
  public var diff: MutableEntityStorage? = null

  public val modifiable: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }
  public val changedProperty: MutableSet<String> = mutableSetOf()

  public fun getEntityInterface(): Class<out WorkspaceEntity> = id.clazz.findWorkspaceEntity()

  protected fun getParent(connectionId: ConnectionId): WorkspaceEntityBuilder<*>? {
    return (diff as? MutableEntityStorageInstrumentation)?.getParentBuilder(connectionId, this)
           ?: (this.entityLinks[EntityLink(false, connectionId)] as? WorkspaceEntityBuilder<*>)
  }

  protected fun getChild(connectionId: ConnectionId): WorkspaceEntityBuilder<*>? {
    return (diff as? MutableEntityStorageInstrumentation)?.getOneChildBuilder(connectionId, this)
           ?: (this.entityLinks[EntityLink(true, connectionId)] as? WorkspaceEntityBuilder<*>)
  }
  
  protected fun getChildren(connectionId: ConnectionId): List<WorkspaceEntityBuilder<*>> {
    @Suppress("UNCHECKED_CAST")
    val fromEntityLinks = this.entityLinks[EntityLink(true, connectionId)] as? List<WorkspaceEntityBuilder<*>> ?: emptyList()
    val thisDiff = diff
    if (thisDiff == null) return fromEntityLinks
    val fromDiff = (thisDiff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(connectionId, this).toList()
    return fromDiff + fromEntityLinks
  }

  // TODO: clean up
  protected fun changeParent(parent: WorkspaceEntityBuilder<*>?, connectionId: ConnectionId) {
    checkModificationAllowed()
    val thisDiff = diff
    if (parent == null) {
      if (thisDiff != null) {
        thisDiff.instrumentation.addChild(connectionId, parent, this)
      } else {
        this.entityLinks[EntityLink(false, connectionId)] = parent
      }
      return
    }
    parent as ModifiableWorkspaceEntityBase<*, *>?
    if (thisDiff == null) {
      parent.entityLinks[EntityLink(true, connectionId)] = this
      this.entityLinks[EntityLink(false, connectionId)] = parent
      return
    }
    if (parent.diff == null) {
      parent.entityLinks[EntityLink(true, connectionId)] = this
      thisDiff.addEntity(parent) // sets value.diff to thisDiff
    }
    thisDiff.instrumentation.addChild(connectionId, parent, this)
  }

  protected fun changeParentOfMany(value: WorkspaceEntityBuilder<*>?, connectionId: ConnectionId) {
    checkModificationAllowed()
    val thisDiff = diff
    if (value == null) {
      if (thisDiff != null) {
        thisDiff.instrumentation.addChild(connectionId, value, this)
      } else {
        this.entityLinks[EntityLink(false, connectionId)] = value
      }
      return
    }
    value as ModifiableWorkspaceEntityBase<*, *>
    if (thisDiff == null) {
      val data = (value.entityLinks[EntityLink(true, connectionId)] as? List<Any?> ?: emptyList()) + this
      value.entityLinks[EntityLink(true, connectionId)] = data
      this.entityLinks[EntityLink(false, connectionId)] = value
      return
    }
    if (value.diff == null) {
      val data = (value.entityLinks[EntityLink(true, connectionId)] as? List<Any?> ?: emptyList()) + this
      value.entityLinks[EntityLink(true, connectionId)] = data
      thisDiff.addEntity(value) // sets value.diff to thisDiff
    }
    thisDiff.instrumentation.addChild(connectionId, value, this)
  }

  protected open fun updateSymbolicId(parent: WorkspaceEntityBuilder<*>, connectionId: ConnectionId) {}

  protected fun changeChild(child: WorkspaceEntityBuilder<*>?, connectionId: ConnectionId) {
    checkModificationAllowed()
    child as ModifiableWorkspaceEntityBase<*, *>?
    child?.updateSymbolicId(this, connectionId)
    val thisDiff = diff
    if (thisDiff == null || child?.diff == null) {
      child?.entityLinks[EntityLink(false, connectionId)] = this
      child?.let { thisDiff?.addEntity(it) } // value.diff becomes thisDiff
    }
    if (thisDiff != null) {
      thisDiff.instrumentation.replaceChildren(connectionId, this, listOfNotNull(child))
    }
    else {
      this.entityLinks[EntityLink(true, connectionId)] = child
    }
  }

  protected fun changeChildren(children: List<WorkspaceEntityBuilder<*>>, connectionId: ConnectionId) {
    checkModificationAllowed()
    @Suppress("UNCHECKED_CAST")
    children as List<ModifiableWorkspaceEntityBase<*, *>>
    val thisDiff = this.diff
    for (child in children) {
      child.updateSymbolicId(this, connectionId)
      if (thisDiff == null || child.diff == null) {
        child.entityLinks[EntityLink(false, connectionId)] = this
        thisDiff?.addEntity(child)
      }
    }
    if (thisDiff != null) {
      thisDiff.instrumentation.replaceChildren(connectionId, this, children)
    }
    else {
      entityLinks[EntityLink(true, connectionId)] = children
    }
  }

  public fun updateChildToParentReferences(parents: Set<WorkspaceEntity>?) {
    if (diff == null) return
    val childId = getEntityData().createEntityId().asChild()
    val entityInterfaceToEntity = parents
                                    ?.associateBy { it.getEntityInterface() }
                                    ?.toMutableMap() ?: mutableMapOf()
    val idToInterface = parents?.associate { it.asBase().id to it.getEntityInterface() } ?: emptyMap()

    val diff = diff as MutableEntityStorageImpl
    diff.refs.getParentRefsOfChild(childId).forEach { (connectionId, existingParent) ->
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
      val newParent = diff.entityDataByIdOrDie(parentEntity.asBase().id).wrapAsModifiable(diff)
      updateReferenceToEntity(parentEntityClass, false, listOf(newParent))
    }
  }

  public fun updateReferenceToEntity(
    entityClass: Class<out WorkspaceEntity>,
    isThisFieldChild: Boolean,
    entities: List<WorkspaceEntity.Builder<*>?>,
  ) {
    val foundConnectionId = findConnectionId(entityClass, entities)
    if (foundConnectionId == null) return

    if (isThisFieldChild) {
      // Branch for case `this` entity is a parent
      if (foundConnectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_MANY || foundConnectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY) {
        // One-to-many connection
        val children = entities.filterNotNull()
        changeChildren(children, foundConnectionId)
      }
      else {
        // One-to-one connection
        val child = entities.single()
        changeChild(child, foundConnectionId)
      }
    }
    else {
      val parent = entities.single()
      parent?.let { updateSymbolicId(it, foundConnectionId) }
      // Branch for case `this` entity is a child
      if (foundConnectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_MANY || foundConnectionId.connectionType == ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY) {
        // One-to-many connection
       changeParentOfMany(parent, foundConnectionId)
      }
      else {
        // One-to-one connection
        changeParent(parent, foundConnectionId)
      }
    }
  }

  private fun findConnectionId(entityClass: Class<out WorkspaceEntity>, entity: List<WorkspaceEntity.Builder<out WorkspaceEntity>?>): ConnectionId? {
    val someEntity = entity.filterNotNull().firstOrNull()
    val firstClass = this.getEntityClass()
    val connectionChecker = { connectionId: ConnectionId -> isCorrectConnection(connectionId, firstClass, entityClass)
                                                            || isCorrectConnection(connectionId, entityClass, firstClass) }
    if (someEntity != null) {
      val resultingConnection = someEntity.asBase().connectionIdList().firstOrNull(connectionChecker)
      if (resultingConnection != null) return resultingConnection
      return this.connectionIdList().firstOrNull(connectionChecker) ?: error("Cannot find connection for $entityClass and ${someEntity::class.java}")
    }
    else {
      val resultingConnection = entityLinks.keys.asSequence().map { it.connectionId }.firstOrNull(connectionChecker)
      if (resultingConnection != null) return resultingConnection
      // Attempt to find connection by old entities still existing in storage
      val connectionsFromOldEntities = referrers(entityClass, true).firstOrNull()?.asBase()?.connectionIdList()
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

  public fun <R : WorkspaceEntity, M: WorkspaceEntity.Builder<R>> referrersBuilders(entityClass: Class<R>, checkReversedConnection: Boolean): Sequence<M> {
    val myDiff = diff
    val entitiesFromDiff: Sequence<M> = if (myDiff != null) {
      getBuilderReferences(myDiff as MutableEntityStorageImpl, entityClass, checkReversedConnection)
    } else emptySequence()

    val entityClassId = entityClass.toClassId()
    val thisClassId = getEntityClass().toClassId()
    val res: Any? = entityLinks.entries.singleOrNull {
      it.key.connectionId.parentClass == entityClassId && it.key.connectionId.childClass == thisClassId
        || it.key.connectionId.parentClass == thisClassId && it.key.connectionId.childClass == entityClassId
    }?.value
    val refsFromLinks: Sequence<M> = if (res == null) {
      emptySequence()
    }
    else {
      if (res is List<*>) {
        @Suppress("UNCHECKED_CAST")
        res.asSequence() as Sequence<M>
      }
      else {
        @Suppress("UNCHECKED_CAST")
        sequenceOf(res as M)
      }
    }
    return entitiesFromDiff + refsFromLinks
  }

  private fun <R : WorkspaceEntity, M : WorkspaceEntity.Builder<R>> getBuilderReferences(
    mySnapshot: MutableEntityStorageImpl,
    entityClass: Class<R>,
    checkReversedConnection: Boolean = false
  ): Sequence<M> {
    var connectionId = mySnapshot.refs.findConnectionId(getEntityInterface(), entityClass)
    if (connectionId != null) {
      val entitiesSequence = when (connectionId.connectionType) {
        ConnectionId.ConnectionType.ONE_TO_MANY, ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> mySnapshot.getManyChildrenBuilders(connectionId, this)
        ConnectionId.ConnectionType.ONE_TO_ONE, ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> mySnapshot.getOneChildBuilder(connectionId, this)
          ?.let { sequenceOf(it) }
          ?: emptySequence()
      } as Sequence<M>
      // If the resulting sequence is empty, and its connection between two entities of the same type, we should continue search
      if (!checkReversedConnection || entitiesSequence.any() || getEntityInterface() != entityClass) {
        return entitiesSequence
      }
    }
    connectionId = mySnapshot.refs.findConnectionId(entityClass, getEntityInterface())
    if (connectionId != null) {
      return mySnapshot
        .getParentBuilder(connectionId, this)
        ?.let { sequenceOf(it as M) }
        ?: emptySequence()
    }
    return emptySequence()
  }

  public fun <R : WorkspaceEntity> referrers(entityClass: Class<R>): Sequence<WorkspaceEntity.Builder<out R>> {
    return referrers(entityClass, false)
  }

  private fun <R : WorkspaceEntity> referrers(entityClass: Class<R>, checkReversedConnection: Boolean): Sequence<WorkspaceEntity.Builder<out R>> {
    val myDiff = diff
    val entitiesFromDiff: Sequence<WorkspaceEntity.Builder<out R>> = if (myDiff != null) {
      getReferences(myDiff as MutableEntityStorageImpl, entityClass, checkReversedConnection)
    } else emptySequence()

    val entityClassId = entityClass.toClassId()
    val thisClassId = getEntityClass().toClassId()
    val res = entityLinks.entries.singleOrNull {
      it.key.connectionId.parentClass == entityClassId && it.key.connectionId.childClass == thisClassId
      || it.key.connectionId.parentClass == thisClassId && it.key.connectionId.childClass == entityClassId
    }?.value
    val entitiesFromLinks: Sequence<WorkspaceEntity.Builder<out R>> = if (res == null) {
      emptySequence()
    }
    else {
      if (res is List<*>) {
        res.asSequence() as Sequence<WorkspaceEntity.Builder<out R>>
      }
      else {
        sequenceOf(res as WorkspaceEntity.Builder<out R>)
      }
    }
    return entitiesFromDiff + entitiesFromLinks
  }

  private fun <R : WorkspaceEntity, M : WorkspaceEntity.Builder<out R>> getReferences(mySnapshot: MutableEntityStorageImpl,
                                                                                      entityClass: Class<R>,
                                                                                      checkReversedConnection: Boolean = false): Sequence<M> {
    var connectionId = mySnapshot.refs.findConnectionId(getEntityInterface(), entityClass)
    if (connectionId != null) {
      val entitiesSequence = when (connectionId.connectionType) {
        ConnectionId.ConnectionType.ONE_TO_MANY -> mySnapshot.getManyChildrenBuilders(connectionId, this)
        ConnectionId.ConnectionType.ONE_TO_ONE -> mySnapshot.getOneChildBuilder(connectionId, this)
                                                    ?.let { sequenceOf(it) }
                                                  ?: emptySequence()
        ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY -> mySnapshot.getManyChildrenBuilders(connectionId, this)
        ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE -> mySnapshot.getOneChildBuilder(connectionId, this)?.let {
          sequenceOf(it)
        } ?: emptySequence()
      } as Sequence<M>
      // If the resulting sequence is empty, and its connection between two entities of the same type, we should continue search
      if (!checkReversedConnection || entitiesSequence.any() || getEntityInterface() != entityClass) {
        return entitiesSequence
      }
    }
    connectionId = mySnapshot.refs.findConnectionId(entityClass, getEntityInterface())
    if (connectionId != null) {
      return mySnapshot.getParentBuilder(connectionId, this)?.let { sequenceOf(it as M) } ?: emptySequence()
    }
    return emptySequence()
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

  protected fun checkModificationAllowed(fieldName: String) {
    if (diff != null && !modifiable.get()) {
      throw IllegalStateException("Modifications are allowed inside `modifyEntity` method only! Modified field: ${this.javaClass.simpleName}#$fieldName")
    }
  }

  public abstract fun getEntityClass(): Class<T>

  public abstract fun checkInitialization(): Unit

  internal fun applyToBuilder(builder: MutableEntityStorage) {
    if (this.diff != null) {
      if (existsInBuilder(builder)) {
        this.diff = builder
        return
      }
      else {
        error("Entity ${getEntityClass().simpleName} is already created in a different builder")
      }
    }
    this.diff = builder
    addToBuilder()
    this.id = getEntityData().createEntityId()
    // After adding entity data to the builder, we need to unbind it and move the control over entity data to the builder.
    // Builder may switch to snapshot at any moment and lock entity data to modification.
    this.currentEntityData = null
    index()
    // Process linked entities that are connected without a builder
    processLinkedEntities(builder)
    checkInitialization()
  }

  protected open fun index() {}

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

  private fun processLinkedParentEntity(entity: Any?,
                                        builder: MutableEntityStorage,
                                        entityLink: EntityLink,
                                        parentKeysToRemove: ArrayList<EntityLink>) {
    if (entity is List<*>) {
      error("Cannot have parent lists")
    }
    else if (entity is WorkspaceEntity.Builder<out WorkspaceEntity>) {
      if (entity is ModifiableWorkspaceEntityBase<*, *> && entity.diff == null) {
        builder.addEntity(entity as ModifiableWorkspaceEntityBase<T, *>)
      }
      builder.instrumentation.addChild(entityLink.connectionId, entity, this)
      parentKeysToRemove.add(entityLink)
    }
  }

  private fun processLinkedChildEntity(entity: Any?,
                                       builder: MutableEntityStorage,
                                       connectionId: ConnectionId) {
    if (entity is List<*>) {
      for (item in entity) {
        if (item is ModifiableWorkspaceEntityBase<*, *>) {
          builder.addEntity(item as ModifiableWorkspaceEntityBase<T, *>)
        }
      }
      if (connectionId.isOneToOne) error("Only one-to-many connection is supported")
      @Suppress("UNCHECKED_CAST")
      entity as List<WorkspaceEntity.Builder<out WorkspaceEntity>>
      val withBuilder_entity = entity.filter { it is ModifiableWorkspaceEntityBase<*, *> && it.diff != null }
      builder.instrumentation.replaceChildren(connectionId, this, withBuilder_entity)
    }
    else if (entity is WorkspaceEntity.Builder<out WorkspaceEntity>) {
      if (entity is ModifiableWorkspaceEntityBase<*, *> && entity.diff == null) {
        builder.addEntity(entity as ModifiableWorkspaceEntityBase<T, *>)
      }
      if (!connectionId.isOneToOne) error("Only one-to-one connection is supported")
      builder.instrumentation.replaceChildren(connectionId, this, listOfNotNull(entity))
    }
  }

  public fun getData(): WorkspaceEntityData<out WorkspaceEntity> = this.getEntityData()

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
  public fun index(entity: WorkspaceEntity.Builder<out WorkspaceEntity>, propertyName: String, virtualFileUrl: VirtualFileUrl?) {
    val builder = diff as MutableEntityStorageImpl
    builder.getMutableVirtualFileUrlIndex().index(entity, propertyName, virtualFileUrl)
  }

  // For generated entities
  public fun index(entity: WorkspaceEntity.Builder<out WorkspaceEntity>, propertyName: String, virtualFileUrls: Collection<VirtualFileUrl>) {
    val builder = diff as MutableEntityStorageImpl
    (builder.getMutableVirtualFileUrlIndex() as VirtualFileIndex.MutableVirtualFileIndex).index(entity.asBase().id,
                                                                                                propertyName, virtualFileUrls)
  }

  // For generated entities
  public fun indexJarDirectories(entity: WorkspaceEntity.Builder<out WorkspaceEntity>, virtualFileUrls: Set<VirtualFileUrl>) {
    val builder = diff as MutableEntityStorageImpl
    (builder.getMutableVirtualFileUrlIndex() as VirtualFileIndex.MutableVirtualFileIndex).indexJarDirectories(
      entity.asBase().id, virtualFileUrls)
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

public abstract class WorkspaceEntityData<E : WorkspaceEntity> : Cloneable {
  public lateinit var entitySource: EntitySource
  public var id: Int = -1

  public fun isEntitySourceInitialized(): Boolean = ::entitySource.isInitialized

  internal fun createEntityId(): EntityId = createEntityId(id, getEntityInterface().toClassId())
  
  public fun <T : WorkspaceEntityData<E>> createAndSetEntityId(modifiableEntity: ModifiableWorkspaceEntityBase<E, T>) {
    val newId = createEntityId()
    modifiableEntity.id = newId
  }

  protected abstract fun newInstance(): E
  
  public fun createEntity(snapshot: EntityStorageInstrumentation): E {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = newInstance()
      entity as WorkspaceEntityBase
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  protected abstract fun newBuilderInstance():  ModifiableWorkspaceEntityBase<E, *>

  internal fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<E> {
    val modifiable = newBuilderInstance()
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

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

  public open fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    throw NotImplementedError()
  }

  public open fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    throw NotImplementedError()
  }
}