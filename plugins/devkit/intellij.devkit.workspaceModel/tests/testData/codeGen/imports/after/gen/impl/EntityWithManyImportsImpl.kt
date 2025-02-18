// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.test.api.impl

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
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.test.api.EntityWithManyImports
import com.intellij.workspaceModel.test.api.SimpleEntity
import com.intellij.workspaceModel.test.api.SimpleId
import java.net.URL

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class EntityWithManyImportsImpl(private val dataSource: EntityWithManyImportsData) : EntityWithManyImports,
                                                                                              WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val FILES_CONNECTION_ID: ConnectionId =
      ConnectionId.create(EntityWithManyImports::class.java, SimpleEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)

    private val connections = listOf<ConnectionId>(
      FILES_CONNECTION_ID,
    )

  }

  override val symbolicId: SimpleId = super.symbolicId

  override val version: Int
    get() {
      readField("version")
      return dataSource.version
    }
  override val name: String
    get() {
      readField("name")
      return dataSource.name
    }

  override val files: List<SimpleEntity>
    get() = snapshot.extractOneToManyChildren<SimpleEntity>(FILES_CONNECTION_ID, this)!!.toList()

  override val pointer: EntityPointer
    get() {
      readField("pointer")
      return dataSource.pointer
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: EntityWithManyImportsData?) :
    ModifiableWorkspaceEntityBase<EntityWithManyImports, EntityWithManyImportsData>(result), EntityWithManyImports.Builder {
    internal constructor() : this(EntityWithManyImportsData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity EntityWithManyImports is already created in a different builder")
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
        error("Field EntityWithManyImports#name should be initialized")
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(FILES_CONNECTION_ID, this) == null) {
          error("Field EntityWithManyImports#files should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, FILES_CONNECTION_ID)] == null) {
          error("Field EntityWithManyImports#files should be initialized")
        }
      }
      if (!getEntityData().isPointerInitialized()) {
        error("Field EntityWithManyImports#pointer should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as EntityWithManyImports
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.version != dataSource.version) this.version = dataSource.version
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.pointer != dataSource.pointer) this.pointer = dataSource.pointer
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

    override var name: String
      get() = getEntityData().name
      set(value) {
        checkModificationAllowed()
        getEntityData(true).name = value
        changedProperty.add("name")
      }

    // List of non-abstract referenced types
    var _files: List<SimpleEntity>? = emptyList()
    override var files: List<SimpleEntity.Builder>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(FILES_CONNECTION_ID, this)!!
            .toList() as List<SimpleEntity.Builder>) +
          (this.entityLinks[EntityLink(true, FILES_CONNECTION_ID)] as? List<SimpleEntity.Builder> ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, FILES_CONNECTION_ID)] as? List<SimpleEntity.Builder> ?: emptyList()
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
                item_value.entityLinks[EntityLink(false, FILES_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable

              _diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
            }
          }
          _diff.updateOneToManyChildrenOfParent(FILES_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, FILES_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, FILES_CONNECTION_ID)] = value
        }
        changedProperty.add("files")
      }

    override var pointer: EntityPointer
      get() = getEntityData().pointer
      set(value) {
        checkModificationAllowed()
        getEntityData(true).pointer = value
        changedProperty.add("pointer")

      }

    override fun getEntityClass(): Class<EntityWithManyImports> = EntityWithManyImports::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class EntityWithManyImportsData : WorkspaceEntityData<EntityWithManyImports>() {
  var version: Int = 0
  lateinit var name: String
  lateinit var pointer: EntityPointer


  internal fun isNameInitialized(): Boolean = ::name.isInitialized
  internal fun isPointerInitialized(): Boolean = ::pointer.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<EntityWithManyImports> {
    val modifiable = EntityWithManyImportsImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): EntityWithManyImports {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = EntityWithManyImportsImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.EntityWithManyImports") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return EntityWithManyImports::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return EntityWithManyImports(version, name, pointer, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as EntityWithManyImportsData

    if (this.entitySource != other.entitySource) return false
    if (this.version != other.version) return false
    if (this.name != other.name) return false
    if (this.pointer != other.pointer) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as EntityWithManyImportsData

    if (this.version != other.version) return false
    if (this.name != other.name) return false
    if (this.pointer != other.pointer) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + pointer.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + pointer.hashCode()
    return result
  }
}
