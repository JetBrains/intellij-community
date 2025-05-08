// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.annotations.Default
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.DefaultValueEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class DefaultValueEntityImpl(private val dataSource: DefaultValueEntityData) : DefaultValueEntity,
                                                                                        WorkspaceEntityBase(dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val name: String
    get() {
      readField("name")
      return dataSource.name
    }

  override var isGenerated: Boolean = dataSource.isGenerated

  override var anotherName: String = dataSource.anotherName

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: DefaultValueEntityData?) :
    ModifiableWorkspaceEntityBase<DefaultValueEntity, DefaultValueEntityData>(result), DefaultValueEntity.Builder {
    internal constructor() : this(DefaultValueEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity DefaultValueEntity is already created in a different builder")
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
      if (!getEntityData().isNameInitialized()) {
        error("Field DefaultValueEntity#name should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as DefaultValueEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.isGenerated != dataSource.isGenerated) this.isGenerated = dataSource.isGenerated
      if (this.anotherName != dataSource.anotherName) this.anotherName = dataSource.anotherName
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var name: String
      get() = getEntityData().name
      set(value) {
        checkModificationAllowed()
        getEntityData(true).name = value
        changedProperty.add("name")
      }

    override var isGenerated: Boolean
      get() = getEntityData().isGenerated
      set(value) {
        checkModificationAllowed()
        getEntityData(true).isGenerated = value
        changedProperty.add("isGenerated")
      }

    override var anotherName: String
      get() = getEntityData().anotherName
      set(value) {
        checkModificationAllowed()
        getEntityData(true).anotherName = value
        changedProperty.add("anotherName")
      }

    override fun getEntityClass(): Class<DefaultValueEntity> = DefaultValueEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class DefaultValueEntityData : WorkspaceEntityData<DefaultValueEntity>() {
  lateinit var name: String
  var isGenerated: Boolean = true
  var anotherName: String = "Another Text"

  internal fun isNameInitialized(): Boolean = ::name.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<DefaultValueEntity> {
    val modifiable = DefaultValueEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): DefaultValueEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = DefaultValueEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.DefaultValueEntity"
    ) as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return DefaultValueEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return DefaultValueEntity(name, entitySource) {
      this.isGenerated = this@DefaultValueEntityData.isGenerated
      this.anotherName = this@DefaultValueEntityData.anotherName
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as DefaultValueEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.name != other.name) return false
    if (this.isGenerated != other.isGenerated) return false
    if (this.anotherName != other.anotherName) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as DefaultValueEntityData

    if (this.name != other.name) return false
    if (this.isGenerated != other.isGenerated) return false
    if (this.anotherName != other.anotherName) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + isGenerated.hashCode()
    result = 31 * result + anotherName.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + isGenerated.hashCode()
    result = 31 * result + anotherName.hashCode()
    return result
  }
}
