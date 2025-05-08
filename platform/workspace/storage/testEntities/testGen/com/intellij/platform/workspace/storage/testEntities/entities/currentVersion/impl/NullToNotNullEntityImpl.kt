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
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.NullToNotNullEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class NullToNotNullEntityImpl(private val dataSource: NullToNotNullEntityData) : NullToNotNullEntity,
                                                                                          WorkspaceEntityBase(dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val nullString: String
    get() {
      readField("nullString")
      return dataSource.nullString
    }

  override val notNullBoolean: Boolean
    get() {
      readField("notNullBoolean")
      return dataSource.notNullBoolean
    }
  override val notNullInt: Int
    get() {
      readField("notNullInt")
      return dataSource.notNullInt
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: NullToNotNullEntityData?) :
    ModifiableWorkspaceEntityBase<NullToNotNullEntity, NullToNotNullEntityData>(result), NullToNotNullEntity.Builder {
    internal constructor() : this(NullToNotNullEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity NullToNotNullEntity is already created in a different builder")
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
      if (!getEntityData().isNullStringInitialized()) {
        error("Field NullToNotNullEntity#nullString should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as NullToNotNullEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.nullString != dataSource.nullString) this.nullString = dataSource.nullString
      if (this.notNullBoolean != dataSource.notNullBoolean) this.notNullBoolean = dataSource.notNullBoolean
      if (this.notNullInt != dataSource.notNullInt) this.notNullInt = dataSource.notNullInt
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var nullString: String
      get() = getEntityData().nullString
      set(value) {
        checkModificationAllowed()
        getEntityData(true).nullString = value
        changedProperty.add("nullString")
      }

    override var notNullBoolean: Boolean
      get() = getEntityData().notNullBoolean
      set(value) {
        checkModificationAllowed()
        getEntityData(true).notNullBoolean = value
        changedProperty.add("notNullBoolean")
      }

    override var notNullInt: Int
      get() = getEntityData().notNullInt
      set(value) {
        checkModificationAllowed()
        getEntityData(true).notNullInt = value
        changedProperty.add("notNullInt")
      }

    override fun getEntityClass(): Class<NullToNotNullEntity> = NullToNotNullEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class NullToNotNullEntityData : WorkspaceEntityData<NullToNotNullEntity>() {
  lateinit var nullString: String
  var notNullBoolean: Boolean = false
  var notNullInt: Int = 0

  internal fun isNullStringInitialized(): Boolean = ::nullString.isInitialized


  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<NullToNotNullEntity> {
    val modifiable = NullToNotNullEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): NullToNotNullEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = NullToNotNullEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.NullToNotNullEntity"
    ) as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return NullToNotNullEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return NullToNotNullEntity(nullString, notNullBoolean, notNullInt, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as NullToNotNullEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.nullString != other.nullString) return false
    if (this.notNullBoolean != other.notNullBoolean) return false
    if (this.notNullInt != other.notNullInt) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as NullToNotNullEntityData

    if (this.nullString != other.nullString) return false
    if (this.notNullBoolean != other.notNullBoolean) return false
    if (this.notNullInt != other.notNullInt) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + nullString.hashCode()
    result = 31 * result + notNullBoolean.hashCode()
    result = 31 * result + notNullInt.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + nullString.hashCode()
    result = 31 * result + notNullBoolean.hashCode()
    result = 31 * result + notNullInt.hashCode()
    return result
  }
}
