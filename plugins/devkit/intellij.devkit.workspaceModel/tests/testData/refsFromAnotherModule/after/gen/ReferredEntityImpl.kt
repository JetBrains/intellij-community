package com.intellij.workspaceModel.test.api

import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ContentRootEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.EntityLink
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.UsedClassesCollector
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToOneChild
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class ReferredEntityImpl : ReferredEntity, WorkspaceEntityBase() {

  companion object {
    internal val CONTENTROOT_CONNECTION_ID: ConnectionId = ConnectionId.create(ReferredEntity::class.java, ContentRootEntity::class.java,
                                                                               ConnectionId.ConnectionType.ONE_TO_ONE, false)

    val connections = listOf<ConnectionId>(
      CONTENTROOT_CONNECTION_ID,
    )

  }

  override var version: Int = 0
  @JvmField
  var _name: String? = null
  override val name: String
    get() = _name!!

  override val contentRoot: ContentRootEntity?
    get() = snapshot.extractOneToOneChild(CONTENTROOT_CONNECTION_ID, this)

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(val result: ReferredEntityData?) : ModifiableWorkspaceEntityBase<ReferredEntity>(), ReferredEntity.Builder {
    constructor() : this(ReferredEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ReferredEntity is already created in a different builder")
        }
      }

      this.diff = builder
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()

      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isNameInitialized()) {
        error("Field ReferredEntity#name should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ReferredEntity
      this.entitySource = dataSource.entitySource
      this.version = dataSource.version
      this.name = dataSource.name
      if (parents != null) {
      }
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData().entitySource = value
        changedProperty.add("entitySource")

      }

    override var version: Int
      get() = getEntityData().version
      set(value) {
        checkModificationAllowed()
        getEntityData().version = value
        changedProperty.add("version")
      }

    override var name: String
      get() = getEntityData().name
      set(value) {
        checkModificationAllowed()
        getEntityData().name = value
        changedProperty.add("name")
      }

    override var contentRoot: ContentRootEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToOneChild(CONTENTROOT_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true,
                                                                                                     CONTENTROOT_CONNECTION_ID)] as? ContentRootEntity
        }
        else {
          this.entityLinks[EntityLink(true, CONTENTROOT_CONNECTION_ID)] as? ContentRootEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*>) {
            value.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(CONTENTROOT_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*>) {
            value.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true, CONTENTROOT_CONNECTION_ID)] = value
        }
        changedProperty.add("contentRoot")
      }

    override fun getEntityData(): ReferredEntityData = result ?: super.getEntityData() as ReferredEntityData
    override fun getEntityClass(): Class<ReferredEntity> = ReferredEntity::class.java
  }
}

class ReferredEntityData : WorkspaceEntityData<ReferredEntity>() {
  var version: Int = 0
  lateinit var name: String


  fun isNameInitialized(): Boolean = ::name.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<ReferredEntity> {
    val modifiable = ReferredEntityImpl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
      modifiable.entitySource = this.entitySource
    }
    modifiable.changedProperty.clear()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): ReferredEntity {
    val entity = ReferredEntityImpl()
    entity.version = version
    entity._name = name
    entity.entitySource = entitySource
    entity.snapshot = snapshot
    entity.id = createEntityId()
    return entity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ReferredEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return ReferredEntity(version, name, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as ReferredEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.version != other.version) return false
    if (this.name != other.name) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as ReferredEntityData

    if (this.version != other.version) return false
    if (this.name != other.name) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + name.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + name.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.sameForAllEntities = true
  }
}
