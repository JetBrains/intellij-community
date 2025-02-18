package com.intellij.workspaceModel.test.api.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
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
import com.intellij.workspaceModel.test.api.ChildrenCollectionFieldEntity
import com.intellij.workspaceModel.test.api.SimpleEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ChildrenCollectionFieldEntityImpl(private val dataSource: ChildrenCollectionFieldEntityData) : ChildrenCollectionFieldEntity,
                                                                                                              WorkspaceEntityBase(
                                                                                                                dataSource
                                                                                                              ) {

  private companion object {
    internal val CHILDRENENTITIESCOLLECTION_CONNECTION_ID: ConnectionId = ConnectionId.create(
      ChildrenCollectionFieldEntity::class.java, SimpleEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false
    )

    private val connections = listOf<ConnectionId>(
      CHILDRENENTITIESCOLLECTION_CONNECTION_ID,
    )

  }

  override val name: String
    get() {
      readField("name")
      return dataSource.name
    }

  override val childrenEntitiesCollection: List<SimpleEntity>
    get() = snapshot.extractOneToManyChildren<SimpleEntity>(CHILDRENENTITIESCOLLECTION_CONNECTION_ID, this)!!.toList()

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: ChildrenCollectionFieldEntityData?) :
    ModifiableWorkspaceEntityBase<ChildrenCollectionFieldEntity, ChildrenCollectionFieldEntityData>(result),
    ChildrenCollectionFieldEntity.Builder {
    internal constructor() : this(ChildrenCollectionFieldEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ChildrenCollectionFieldEntity is already created in a different builder")
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
        error("Field ChildrenCollectionFieldEntity#name should be initialized")
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDRENENTITIESCOLLECTION_CONNECTION_ID, this) == null) {
          error("Field ChildrenCollectionFieldEntity#childrenEntitiesCollection should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDRENENTITIESCOLLECTION_CONNECTION_ID)] == null) {
          error("Field ChildrenCollectionFieldEntity#childrenEntitiesCollection should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ChildrenCollectionFieldEntity
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
    var _childrenEntitiesCollection: List<SimpleEntity>? = emptyList()
    override var childrenEntitiesCollection: List<SimpleEntity.Builder>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(CHILDRENENTITIESCOLLECTION_CONNECTION_ID, this)!!
            .toList() as List<SimpleEntity.Builder>) +
          (this.entityLinks[EntityLink(true, CHILDRENENTITIESCOLLECTION_CONNECTION_ID)] as? List<SimpleEntity.Builder> ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, CHILDRENENTITIESCOLLECTION_CONNECTION_ID)] as? List<SimpleEntity.Builder> ?: emptyList()
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
                item_value.entityLinks[EntityLink(false, CHILDRENENTITIESCOLLECTION_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable

              _diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
            }
          }
          _diff.updateOneToManyChildrenOfParent(CHILDRENENTITIESCOLLECTION_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, CHILDRENENTITIESCOLLECTION_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, CHILDRENENTITIESCOLLECTION_CONNECTION_ID)] = value
        }
        changedProperty.add("childrenEntitiesCollection")
      }

    override fun getEntityClass(): Class<ChildrenCollectionFieldEntity> = ChildrenCollectionFieldEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ChildrenCollectionFieldEntityData : WorkspaceEntityData<ChildrenCollectionFieldEntity>() {
  lateinit var name: String

  internal fun isNameInitialized(): Boolean = ::name.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ChildrenCollectionFieldEntity> {
    val modifiable = ChildrenCollectionFieldEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): ChildrenCollectionFieldEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = ChildrenCollectionFieldEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.ChildrenCollectionFieldEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ChildrenCollectionFieldEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return ChildrenCollectionFieldEntity(name, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ChildrenCollectionFieldEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.name != other.name) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ChildrenCollectionFieldEntityData

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
