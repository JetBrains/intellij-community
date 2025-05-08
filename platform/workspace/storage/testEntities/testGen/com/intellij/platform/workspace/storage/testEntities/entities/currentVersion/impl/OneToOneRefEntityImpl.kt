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
import com.intellij.platform.workspace.storage.impl.extractOneToManyChildren
import com.intellij.platform.workspace.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.AnotherOneToOneRefEntity
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToOneRefEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class OneToOneRefEntityImpl(private val dataSource: OneToOneRefEntityData) : OneToOneRefEntity, WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val ANOTHERENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(
      OneToOneRefEntity::class.java, AnotherOneToOneRefEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false
    )

    private val connections = listOf<ConnectionId>(
      ANOTHERENTITY_CONNECTION_ID,
    )

  }

  override val version: Int
    get() {
      readField("version")
      return dataSource.version
    }
  override val text: String
    get() {
      readField("text")
      return dataSource.text
    }

  override val anotherEntity: List<AnotherOneToOneRefEntity>
    get() = snapshot.extractOneToManyChildren<AnotherOneToOneRefEntity>(ANOTHERENTITY_CONNECTION_ID, this)!!.toList()

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: OneToOneRefEntityData?) : ModifiableWorkspaceEntityBase<OneToOneRefEntity, OneToOneRefEntityData>(result),
                                                           OneToOneRefEntity.Builder {
    internal constructor() : this(OneToOneRefEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity OneToOneRefEntity is already created in a different builder")
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
      if (!getEntityData().isTextInitialized()) {
        error("Field OneToOneRefEntity#text should be initialized")
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(ANOTHERENTITY_CONNECTION_ID, this) == null) {
          error("Field OneToOneRefEntity#anotherEntity should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, ANOTHERENTITY_CONNECTION_ID)] == null) {
          error("Field OneToOneRefEntity#anotherEntity should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as OneToOneRefEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.version != dataSource.version) this.version = dataSource.version
      if (this.text != dataSource.text) this.text = dataSource.text
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var version: Int
      get() = getEntityData().version
      set(value) {
        checkModificationAllowed()
        getEntityData(true).version = value
        changedProperty.add("version")
      }

    override var text: String
      get() = getEntityData().text
      set(value) {
        checkModificationAllowed()
        getEntityData(true).text = value
        changedProperty.add("text")
      }

    // List of non-abstract referenced types
    var _anotherEntity: List<AnotherOneToOneRefEntity>? = emptyList()
    override var anotherEntity: List<AnotherOneToOneRefEntity.Builder>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(ANOTHERENTITY_CONNECTION_ID, this)!!
            .toList() as List<AnotherOneToOneRefEntity.Builder>) +
          (this.entityLinks[EntityLink(true, ANOTHERENTITY_CONNECTION_ID)] as? List<AnotherOneToOneRefEntity.Builder> ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, ANOTHERENTITY_CONNECTION_ID)] as? List<AnotherOneToOneRefEntity.Builder> ?: emptyList()
        }
      }
      set(value) {
        // Setter of the list of non-abstract referenced types
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null) {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *> && (item_value as? ModifiableWorkspaceEntityBase<*, *>)?.diff == null) {
              // Backref setup before adding to store
              if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
                item_value.entityLinks[EntityLink(false, ANOTHERENTITY_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable

              _diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
            }
          }
          _diff.updateOneToManyChildrenOfParent(ANOTHERENTITY_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, ANOTHERENTITY_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, ANOTHERENTITY_CONNECTION_ID)] = value
        }
        changedProperty.add("anotherEntity")
      }

    override fun getEntityClass(): Class<OneToOneRefEntity> = OneToOneRefEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class OneToOneRefEntityData : WorkspaceEntityData<OneToOneRefEntity>() {
  var version: Int = 0
  lateinit var text: String


  internal fun isTextInitialized(): Boolean = ::text.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<OneToOneRefEntity> {
    val modifiable = OneToOneRefEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): OneToOneRefEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = OneToOneRefEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToOneRefEntity"
    ) as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return OneToOneRefEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return OneToOneRefEntity(version, text, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as OneToOneRefEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.version != other.version) return false
    if (this.text != other.text) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as OneToOneRefEntityData

    if (this.version != other.version) return false
    if (this.text != other.text) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + text.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + text.hashCode()
    return result
  }
}
