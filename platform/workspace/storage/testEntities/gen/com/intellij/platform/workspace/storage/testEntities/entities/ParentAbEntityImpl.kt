// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntityInformation
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.UsedClassesCollector
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.extractOneToAbstractManyChildren
import com.intellij.platform.workspace.storage.impl.extractOneToManyChildren
import com.intellij.platform.workspace.storage.impl.updateOneToAbstractManyChildrenOfParent
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

@GeneratedCodeApiVersion(2)
@GeneratedCodeImplVersion(2)
open class ParentAbEntityImpl(val dataSource: ParentAbEntityData) : ParentAbEntity, WorkspaceEntityBase() {

  companion object {
    internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentAbEntity::class.java, ChildAbstractBaseEntity::class.java,
                                                                            ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, false)

    val connections = listOf<ConnectionId>(
      CHILDREN_CONNECTION_ID,
    )

  }

  override val children: List<ChildAbstractBaseEntity>
    get() = snapshot.extractOneToAbstractManyChildren<ChildAbstractBaseEntity>(CHILDREN_CONNECTION_ID, this)!!.toList()

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: ParentAbEntityData?) : ModifiableWorkspaceEntityBase<ParentAbEntity, ParentAbEntityData>(
    result), ParentAbEntity.Builder {
    constructor() : this(ParentAbEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ParentAbEntity is already created in a different builder")
        }
      }

      this.diff = builder
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null) {
          error("Field ParentAbEntity#children should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
          error("Field ParentAbEntity#children should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ParentAbEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var children: List<ChildAbstractBaseEntity>
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToAbstractManyChildren<ChildAbstractBaseEntity>(CHILDREN_CONNECTION_ID,
                                                                          this)!!.toList() + (this.entityLinks[EntityLink(true,
                                                                                                                          CHILDREN_CONNECTION_ID)] as? List<ChildAbstractBaseEntity>
                                                                                              ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as List<ChildAbstractBaseEntity> ?: emptyList()
        }
      }
      set(value) {
        // Set list of ref types for abstract entities
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null) {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *> && (item_value as? ModifiableWorkspaceEntityBase<*, *>)?.diff == null) {
              // Backref setup before adding to store an abstract entity
              if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
                item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable
              _diff.addEntity(item_value)
            }
          }
          _diff.updateOneToAbstractManyChildrenOfParent(CHILDREN_CONNECTION_ID, this, value.asSequence())
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] = value
        }
        changedProperty.add("children")
      }

    override fun getEntityClass(): Class<ParentAbEntity> = ParentAbEntity::class.java
  }
}

class ParentAbEntityData : WorkspaceEntityData<ParentAbEntity>() {


  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ParentAbEntity> {
    val modifiable = ParentAbEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): ParentAbEntity {
    return getCached(snapshot) {
      val entity = ParentAbEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ParentAbEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return ParentAbEntity(entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ParentAbEntityData

    if (this.entitySource != other.entitySource) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ParentAbEntityData

    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.sameForAllEntities = true
  }
}
