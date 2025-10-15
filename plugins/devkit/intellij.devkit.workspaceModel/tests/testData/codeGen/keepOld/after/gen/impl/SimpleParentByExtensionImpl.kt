package com.intellij.platform.workspace.jps.entities.impl

import com.intellij.platform.workspace.jps.entities.ModifiableSimpleEntity
import com.intellij.platform.workspace.jps.entities.ModifiableSimpleParentByExtension
import com.intellij.platform.workspace.jps.entities.SimpleEntity
import com.intellij.platform.workspace.jps.entities.SimpleParentByExtension
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.annotations.Parent
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

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class SimpleParentByExtensionImpl(private val dataSource: SimpleParentByExtensionData) : SimpleParentByExtension, WorkspaceEntityBase(
  dataSource) {

  private companion object {
    internal val SIMPLECHILD_CONNECTION_ID: ConnectionId = ConnectionId.create(SimpleParentByExtension::class.java,
                                                                               SimpleEntity::class.java,
                                                                               ConnectionId.ConnectionType.ONE_TO_ONE, false)

    private val connections = listOf<ConnectionId>(
      SIMPLECHILD_CONNECTION_ID,
    )

  }

  override val simpleName: String
    get() {
      readField("simpleName")
      return dataSource.simpleName
    }

  override val simpleChild: SimpleEntity?
    get() = snapshot.extractOneToOneChild(SIMPLECHILD_CONNECTION_ID, this)

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: SimpleParentByExtensionData?) : ModifiableWorkspaceEntityBase<SimpleParentByExtension, SimpleParentByExtensionData>(
    result), SimpleParentByExtension.Builder {
    internal constructor() : this(SimpleParentByExtensionData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity SimpleParentByExtension is already created in a different builder")
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
      if (!getEntityData().isSimpleNameInitialized()) {
        error("Field SimpleParentByExtension#simpleName should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as SimpleParentByExtension
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.simpleName != dataSource.simpleName) this.simpleName = dataSource.simpleName
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var simpleName: String
      get() = getEntityData().simpleName
      set(value) {
        checkModificationAllowed()
        getEntityData(true).simpleName = value
        changedProperty.add("simpleName")
      }

    override var simpleChild: ModifiableSimpleEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getOneChildBuilder(SIMPLECHILD_CONNECTION_ID, this) as? ModifiableSimpleEntity)
          ?: (this.entityLinks[EntityLink(true, SIMPLECHILD_CONNECTION_ID)] as? ModifiableSimpleEntity)
        }
        else {
          this.entityLinks[EntityLink(true, SIMPLECHILD_CONNECTION_ID)] as? ModifiableSimpleEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, SIMPLECHILD_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(SIMPLECHILD_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, SIMPLECHILD_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true, SIMPLECHILD_CONNECTION_ID)] = value
        }
        changedProperty.add("simpleChild")
      }

    override fun getEntityClass(): Class<SimpleParentByExtension> = SimpleParentByExtension::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class SimpleParentByExtensionData : WorkspaceEntityData<SimpleParentByExtension>() {
  lateinit var simpleName: String

  internal fun isSimpleNameInitialized(): Boolean = ::simpleName.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<SimpleParentByExtension> {
    val modifiable = SimpleParentByExtensionImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): SimpleParentByExtension {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = SimpleParentByExtensionImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.jps.entities.SimpleParentByExtension") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SimpleParentByExtension::class.java
  }

  override fun createDetachedEntity(parents: List<ModifiableWorkspaceEntity<*>>): ModifiableWorkspaceEntity<*> {
    return SimpleParentByExtension(simpleName, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as SimpleParentByExtensionData

    if (this.entitySource != other.entitySource) return false
    if (this.simpleName != other.simpleName) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as SimpleParentByExtensionData

    if (this.simpleName != other.simpleName) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + simpleName.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + simpleName.hashCode()
    return result
  }
}
