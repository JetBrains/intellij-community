// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.extractOneToOneChild
import com.intellij.platform.workspace.storage.impl.updateOneToOneChildOfParent
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.AnotherOneToManyRefEntity
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToManyRefDataClass
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToManyRefEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class OneToManyRefEntityImpl(private val dataSource: OneToManyRefEntityData) : OneToManyRefEntity,
                                                                                        WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val ANOTHERENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(
      OneToManyRefEntity::class.java, AnotherOneToManyRefEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false
    )

    private val connections = listOf<ConnectionId>(
      ANOTHERENTITY_CONNECTION_ID,
    )

  }

  override val someData: OneToManyRefDataClass
    get() {
      readField("someData")
      return dataSource.someData
    }

  override val anotherEntity: AnotherOneToManyRefEntity?
    get() = snapshot.extractOneToOneChild(ANOTHERENTITY_CONNECTION_ID, this)

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: OneToManyRefEntityData?) :
    ModifiableWorkspaceEntityBase<OneToManyRefEntity, OneToManyRefEntityData>(result), OneToManyRefEntity.Builder {
    internal constructor() : this(OneToManyRefEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity OneToManyRefEntity is already created in a different builder")
        }
      }

      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isSomeDataInitialized()) {
        error("Field OneToManyRefEntity#someData should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as OneToManyRefEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.someData != dataSource.someData) this.someData = dataSource.someData
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var someData: OneToManyRefDataClass
      get() = getEntityData().someData
      set(value) {
        checkModificationAllowed()
        getEntityData(true).someData = value
        changedProperty.add("someData")

      }

    override var anotherEntity: AnotherOneToManyRefEntity.Builder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getOneChildBuilder(
            ANOTHERENTITY_CONNECTION_ID, this
          ) as? AnotherOneToManyRefEntity.Builder)
          ?: (this.entityLinks[EntityLink(true, ANOTHERENTITY_CONNECTION_ID)] as? AnotherOneToManyRefEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(true, ANOTHERENTITY_CONNECTION_ID)] as? AnotherOneToManyRefEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, ANOTHERENTITY_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(ANOTHERENTITY_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, ANOTHERENTITY_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true, ANOTHERENTITY_CONNECTION_ID)] = value
        }
        changedProperty.add("anotherEntity")
      }

    override fun getEntityClass(): Class<OneToManyRefEntity> = OneToManyRefEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class OneToManyRefEntityData : WorkspaceEntityData<OneToManyRefEntity>() {
  lateinit var someData: OneToManyRefDataClass

  internal fun isSomeDataInitialized(): Boolean = ::someData.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<OneToManyRefEntity> {
    val modifiable = OneToManyRefEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): OneToManyRefEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = OneToManyRefEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToManyRefEntity"
    ) as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return OneToManyRefEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return OneToManyRefEntity(someData, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as OneToManyRefEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.someData != other.someData) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as OneToManyRefEntityData

    if (this.someData != other.someData) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + someData.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + someData.hashCode()
    return result
  }
}
