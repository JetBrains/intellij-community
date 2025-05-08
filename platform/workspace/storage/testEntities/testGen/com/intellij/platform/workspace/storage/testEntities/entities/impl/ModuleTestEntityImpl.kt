// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.impl

import com.intellij.platform.workspace.storage.*
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
import com.intellij.platform.workspace.storage.testEntities.entities.ContentRootTestEntity
import com.intellij.platform.workspace.storage.testEntities.entities.FacetTestEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ModuleTestEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ModuleTestEntitySymbolicId

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ModuleTestEntityImpl(private val dataSource: ModuleTestEntityData) : ModuleTestEntity, WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val CONTENTROOTS_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ModuleTestEntity::class.java, ContentRootTestEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val FACETS_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ModuleTestEntity::class.java, FacetTestEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)

    private val connections = listOf<ConnectionId>(
      CONTENTROOTS_CONNECTION_ID,
      FACETS_CONNECTION_ID,
    )

  }

  override val symbolicId: ModuleTestEntitySymbolicId = super.symbolicId

  override val name: String
    get() {
      readField("name")
      return dataSource.name
    }

  override val contentRoots: List<ContentRootTestEntity>
    get() = snapshot.extractOneToManyChildren<ContentRootTestEntity>(CONTENTROOTS_CONNECTION_ID, this)!!.toList()

  override val facets: List<FacetTestEntity>
    get() = snapshot.extractOneToManyChildren<FacetTestEntity>(FACETS_CONNECTION_ID, this)!!.toList()

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: ModuleTestEntityData?) : ModifiableWorkspaceEntityBase<ModuleTestEntity, ModuleTestEntityData>(result),
                                                          ModuleTestEntity.Builder {
    internal constructor() : this(ModuleTestEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ModuleTestEntity is already created in a different builder")
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
        error("Field ModuleTestEntity#name should be initialized")
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CONTENTROOTS_CONNECTION_ID, this) == null) {
          error("Field ModuleTestEntity#contentRoots should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CONTENTROOTS_CONNECTION_ID)] == null) {
          error("Field ModuleTestEntity#contentRoots should be initialized")
        }
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(FACETS_CONNECTION_ID, this) == null) {
          error("Field ModuleTestEntity#facets should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, FACETS_CONNECTION_ID)] == null) {
          error("Field ModuleTestEntity#facets should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ModuleTestEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.name != dataSource.name) this.name = dataSource.name
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

    // List of non-abstract referenced types
    var _contentRoots: List<ContentRootTestEntity>? = emptyList()
    override var contentRoots: List<ContentRootTestEntity.Builder>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(CONTENTROOTS_CONNECTION_ID, this)!!
            .toList() as List<ContentRootTestEntity.Builder>) +
          (this.entityLinks[EntityLink(true, CONTENTROOTS_CONNECTION_ID)] as? List<ContentRootTestEntity.Builder> ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, CONTENTROOTS_CONNECTION_ID)] as? List<ContentRootTestEntity.Builder> ?: emptyList()
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
                item_value.entityLinks[EntityLink(false, CONTENTROOTS_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable

              _diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
            }
          }
          _diff.updateOneToManyChildrenOfParent(CONTENTROOTS_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, CONTENTROOTS_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, CONTENTROOTS_CONNECTION_ID)] = value
        }
        changedProperty.add("contentRoots")
      }

    // List of non-abstract referenced types
    var _facets: List<FacetTestEntity>? = emptyList()
    override var facets: List<FacetTestEntity.Builder>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(FACETS_CONNECTION_ID, this)!!
            .toList() as List<FacetTestEntity.Builder>) +
          (this.entityLinks[EntityLink(true, FACETS_CONNECTION_ID)] as? List<FacetTestEntity.Builder> ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, FACETS_CONNECTION_ID)] as? List<FacetTestEntity.Builder> ?: emptyList()
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
                item_value.entityLinks[EntityLink(false, FACETS_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable

              _diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
            }
          }
          _diff.updateOneToManyChildrenOfParent(FACETS_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, FACETS_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, FACETS_CONNECTION_ID)] = value
        }
        changedProperty.add("facets")
      }

    override fun getEntityClass(): Class<ModuleTestEntity> = ModuleTestEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ModuleTestEntityData : WorkspaceEntityData<ModuleTestEntity>() {
  lateinit var name: String

  internal fun isNameInitialized(): Boolean = ::name.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ModuleTestEntity> {
    val modifiable = ModuleTestEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): ModuleTestEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = ModuleTestEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.ModuleTestEntity"
    ) as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ModuleTestEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return ModuleTestEntity(name, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ModuleTestEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.name != other.name) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ModuleTestEntityData

    if (this.name != other.name) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + name.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + name.hashCode()
    return result
  }
}
