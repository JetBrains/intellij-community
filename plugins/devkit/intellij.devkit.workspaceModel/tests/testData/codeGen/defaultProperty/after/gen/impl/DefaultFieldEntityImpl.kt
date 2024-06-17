package com.intellij.workspaceModel.test.api.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
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
import com.intellij.workspaceModel.test.api.DefaultFieldEntity
import com.intellij.workspaceModel.test.api.TestData

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class DefaultFieldEntityImpl(private val dataSource: DefaultFieldEntityData) : DefaultFieldEntity,
                                                                                        WorkspaceEntityBase(dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val version: Int
    get() {
      readField("version")
      return dataSource.version
    }
  override val data: TestData
    get() {
      readField("data")
      return dataSource.data
    }

  override var anotherVersion: Int = dataSource.anotherVersion

  override var description: String = dataSource.description

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: DefaultFieldEntityData?) :
    ModifiableWorkspaceEntityBase<DefaultFieldEntity, DefaultFieldEntityData>(result), DefaultFieldEntity.Builder {
    internal constructor() : this(DefaultFieldEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity DefaultFieldEntity is already created in a different builder")
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
      if (!getEntityData().isDataInitialized()) {
        error("Field DefaultFieldEntity#data should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as DefaultFieldEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.version != dataSource.version) this.version = dataSource.version
      if (this.data != dataSource.data) this.data = dataSource.data
      if (this.anotherVersion != dataSource.anotherVersion) this.anotherVersion = dataSource.anotherVersion
      if (this.description != dataSource.description) this.description = dataSource.description
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

    override var data: TestData
      get() = getEntityData().data
      set(value) {
        checkModificationAllowed()
        getEntityData(true).data = value
        changedProperty.add("data")

      }

    override var anotherVersion: Int
      get() = getEntityData().anotherVersion
      set(value) {
        checkModificationAllowed()
        getEntityData(true).anotherVersion = value
        changedProperty.add("anotherVersion")
      }

    override var description: String
      get() = getEntityData().description
      set(value) {
        checkModificationAllowed()
        getEntityData(true).description = value
        changedProperty.add("description")
      }

    override fun getEntityClass(): Class<DefaultFieldEntity> = DefaultFieldEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class DefaultFieldEntityData : WorkspaceEntityData<DefaultFieldEntity>() {
  var version: Int = 0
  lateinit var data: TestData
  var anotherVersion: Int = 0
  var description: String = "Default description"


  internal fun isDataInitialized(): Boolean = ::data.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<DefaultFieldEntity> {
    val modifiable = DefaultFieldEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): DefaultFieldEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = DefaultFieldEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.DefaultFieldEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return DefaultFieldEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return DefaultFieldEntity(version, data, entitySource) {
      this.anotherVersion = this@DefaultFieldEntityData.anotherVersion
      this.description = this@DefaultFieldEntityData.description
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as DefaultFieldEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.version != other.version) return false
    if (this.data != other.data) return false
    if (this.anotherVersion != other.anotherVersion) return false
    if (this.description != other.description) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as DefaultFieldEntityData

    if (this.version != other.version) return false
    if (this.data != other.data) return false
    if (this.anotherVersion != other.anotherVersion) return false
    if (this.description != other.description) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + data.hashCode()
    result = 31 * result + anotherVersion.hashCode()
    result = 31 * result + description.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + data.hashCode()
    result = 31 * result + anotherVersion.hashCode()
    result = 31 * result + description.hashCode()
    return result
  }
}
