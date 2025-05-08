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
import com.intellij.platform.workspace.storage.impl.extractOneToOneParent
import com.intellij.platform.workspace.storage.impl.updateOneToOneParentOfChild
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
internal class AnotherOneToManyRefEntityImpl(private val dataSource: AnotherOneToManyRefEntityData) : AnotherOneToManyRefEntity,
                                                                                                      WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(
      OneToManyRefEntity::class.java, AnotherOneToManyRefEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false
    )

    private val connections = listOf<ConnectionId>(
      PARENTENTITY_CONNECTION_ID,
    )

  }

  override val parentEntity: OneToManyRefEntity
    get() = snapshot.extractOneToOneParent(PARENTENTITY_CONNECTION_ID, this)!!

  override val version: Int
    get() {
      readField("version")
      return dataSource.version
    }
  override val someData: OneToManyRefDataClass
    get() {
      readField("someData")
      return dataSource.someData
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: AnotherOneToManyRefEntityData?) :
    ModifiableWorkspaceEntityBase<AnotherOneToManyRefEntity, AnotherOneToManyRefEntityData>(result), AnotherOneToManyRefEntity.Builder {
    internal constructor() : this(AnotherOneToManyRefEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity AnotherOneToManyRefEntity is already created in a different builder")
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
      if (_diff != null) {
        if (_diff.extractOneToOneParent<WorkspaceEntityBase>(PARENTENTITY_CONNECTION_ID, this) == null) {
          error("Field AnotherOneToManyRefEntity#parentEntity should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] == null) {
          error("Field AnotherOneToManyRefEntity#parentEntity should be initialized")
        }
      }
      if (!getEntityData().isSomeDataInitialized()) {
        error("Field AnotherOneToManyRefEntity#someData should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as AnotherOneToManyRefEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.version != dataSource.version) this.version = dataSource.version
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

    override var parentEntity: OneToManyRefEntity.Builder
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(PARENTENTITY_CONNECTION_ID, this) as? OneToManyRefEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)]!! as OneToManyRefEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)]!! as OneToManyRefEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneParentOfChild(PARENTENTITY_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] = value
        }
        changedProperty.add("parentEntity")
      }

    override var version: Int
      get() = getEntityData().version
      set(value) {
        checkModificationAllowed()
        getEntityData(true).version = value
        changedProperty.add("version")
      }

    override var someData: OneToManyRefDataClass
      get() = getEntityData().someData
      set(value) {
        checkModificationAllowed()
        getEntityData(true).someData = value
        changedProperty.add("someData")

      }

    override fun getEntityClass(): Class<AnotherOneToManyRefEntity> = AnotherOneToManyRefEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class AnotherOneToManyRefEntityData : WorkspaceEntityData<AnotherOneToManyRefEntity>() {
  var version: Int = 0
  lateinit var someData: OneToManyRefDataClass


  internal fun isSomeDataInitialized(): Boolean = ::someData.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<AnotherOneToManyRefEntity> {
    val modifiable = AnotherOneToManyRefEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): AnotherOneToManyRefEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = AnotherOneToManyRefEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.AnotherOneToManyRefEntity"
    ) as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return AnotherOneToManyRefEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return AnotherOneToManyRefEntity(version, someData, entitySource) {
      parents.filterIsInstance<OneToManyRefEntity.Builder>().singleOrNull()?.let { this.parentEntity = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(OneToManyRefEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as AnotherOneToManyRefEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.version != other.version) return false
    if (this.someData != other.someData) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as AnotherOneToManyRefEntityData

    if (this.version != other.version) return false
    if (this.someData != other.someData) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + someData.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + someData.hashCode()
    return result
  }
}
