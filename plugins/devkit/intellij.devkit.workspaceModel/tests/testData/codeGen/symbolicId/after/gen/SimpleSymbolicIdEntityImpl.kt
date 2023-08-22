package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntityInformation
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.PersistentEntityId
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Default
import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.UsedClassesCollector
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData

@GeneratedCodeApiVersion(2)
@GeneratedCodeImplVersion(2)
open class SimpleSymbolicIdEntityImpl(val dataSource: SimpleSymbolicIdEntityData) : SimpleSymbolicIdEntity, WorkspaceEntityBase() {

  companion object {


    val connections = listOf<ConnectionId>(
    )

  }

  override val version: Int get() = dataSource.version
  override val name: String
    get() = dataSource.name

  override val related: SimpleId
    get() = dataSource.related

  override val sealedClassWithLinks: SealedClassWithLinks
    get() = dataSource.sealedClassWithLinks

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: SimpleSymbolicIdEntityData?) : ModifiableWorkspaceEntityBase<SimpleSymbolicIdEntity, SimpleSymbolicIdEntityData>(
    result), SimpleSymbolicIdEntity.Builder {
    constructor() : this(SimpleSymbolicIdEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity SimpleSymbolicIdEntity is already created in a different builder")
        }
      }

      this.diff = builder
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

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
        error("Field SimpleSymbolicIdEntity#name should be initialized")
      }
      if (!getEntityData().isRelatedInitialized()) {
        error("Field SimpleSymbolicIdEntity#related should be initialized")
      }
      if (!getEntityData().isSealedClassWithLinksInitialized()) {
        error("Field SimpleSymbolicIdEntity#sealedClassWithLinks should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as SimpleSymbolicIdEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.version != dataSource.version) this.version = dataSource.version
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.related != dataSource.related) this.related = dataSource.related
      if (this.sealedClassWithLinks != dataSource.sealedClassWithLinks) this.sealedClassWithLinks = dataSource.sealedClassWithLinks
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

    override var related: SimpleId
      get() = getEntityData().related
      set(value) {
        checkModificationAllowed()
        getEntityData(true).related = value
        changedProperty.add("related")

      }

    override var sealedClassWithLinks: SealedClassWithLinks
      get() = getEntityData().sealedClassWithLinks
      set(value) {
        checkModificationAllowed()
        getEntityData(true).sealedClassWithLinks = value
        changedProperty.add("sealedClassWithLinks")

      }

    override fun getEntityClass(): Class<SimpleSymbolicIdEntity> = SimpleSymbolicIdEntity::class.java
  }
}

class SimpleSymbolicIdEntityData : WorkspaceEntityData.WithCalculableSymbolicId<SimpleSymbolicIdEntity>() {
  var version: Int = 0
  lateinit var name: String
  lateinit var related: SimpleId
  lateinit var sealedClassWithLinks: SealedClassWithLinks


  fun isNameInitialized(): Boolean = ::name.isInitialized
  fun isRelatedInitialized(): Boolean = ::related.isInitialized
  fun isSealedClassWithLinksInitialized(): Boolean = ::sealedClassWithLinks.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<SimpleSymbolicIdEntity> {
    val modifiable = SimpleSymbolicIdEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): SimpleSymbolicIdEntity {
    return getCached(snapshot) {
      val entity = SimpleSymbolicIdEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun symbolicId(): SymbolicEntityId<*> {
    return SimpleId(name)
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SimpleSymbolicIdEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return SimpleSymbolicIdEntity(version, name, related, sealedClassWithLinks, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as SimpleSymbolicIdEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.version != other.version) return false
    if (this.name != other.name) return false
    if (this.related != other.related) return false
    if (this.sealedClassWithLinks != other.sealedClassWithLinks) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as SimpleSymbolicIdEntityData

    if (this.version != other.version) return false
    if (this.name != other.name) return false
    if (this.related != other.related) return false
    if (this.sealedClassWithLinks != other.sealedClassWithLinks) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + related.hashCode()
    result = 31 * result + sealedClassWithLinks.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + related.hashCode()
    result = 31 * result + sealedClassWithLinks.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.add(SimpleId::class.java)
    collector.add(SealedClassWithLinks.Many.Unordered::class.java)
    collector.add(SealedClassWithLinks.Many::class.java)
    collector.add(SealedClassWithLinks.Single::class.java)
    collector.add(SealedClassWithLinks::class.java)
    collector.add(SealedClassWithLinks.Many.Ordered::class.java)
    collector.addObject(SealedClassWithLinks.Nothing::class.java)
    this.sealedClassWithLinks?.let { collector.add(it::class.java) }
    collector.sameForAllEntities = true
  }
}
