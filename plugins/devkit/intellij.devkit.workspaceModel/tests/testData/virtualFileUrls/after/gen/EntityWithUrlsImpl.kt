package com.intellij.workspaceModel.test.api

import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.UsedClassesCollector
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.containers.MutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class EntityWithUrlsImpl : EntityWithUrls, WorkspaceEntityBase() {

  companion object {


    val connections = listOf<ConnectionId>(
    )

  }

  @JvmField
  var _simpleUrl: VirtualFileUrl? = null
  override val simpleUrl: VirtualFileUrl
    get() = _simpleUrl!!

  @JvmField
  var _nullableUrl: VirtualFileUrl? = null
  override val nullableUrl: VirtualFileUrl?
    get() = _nullableUrl

  @JvmField
  var _listOfUrls: List<VirtualFileUrl>? = null
  override val listOfUrls: List<VirtualFileUrl>
    get() = _listOfUrls!!

  @JvmField
  var _dataClassWithUrl: DataClassWithUrl? = null
  override val dataClassWithUrl: DataClassWithUrl
    get() = _dataClassWithUrl!!

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(val result: EntityWithUrlsData?) : ModifiableWorkspaceEntityBase<EntityWithUrls>(), EntityWithUrls.Builder {
    constructor() : this(EntityWithUrlsData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity EntityWithUrls is already created in a different builder")
        }
      }

      this.diff = builder
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()

      index(this, "simpleUrl", this.simpleUrl)
      index(this, "nullableUrl", this.nullableUrl)
      index(this, "listOfUrls", this.listOfUrls.toHashSet())
      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isSimpleUrlInitialized()) {
        error("Field EntityWithUrls#simpleUrl should be initialized")
      }
      if (!getEntityData().isListOfUrlsInitialized()) {
        error("Field EntityWithUrls#listOfUrls should be initialized")
      }
      if (!getEntityData().isDataClassWithUrlInitialized()) {
        error("Field EntityWithUrls#dataClassWithUrl should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as EntityWithUrls
      this.entitySource = dataSource.entitySource
      this.simpleUrl = dataSource.simpleUrl
      this.nullableUrl = dataSource.nullableUrl
      this.listOfUrls = dataSource.listOfUrls.toMutableList()
      this.dataClassWithUrl = dataSource.dataClassWithUrl
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

    override var simpleUrl: VirtualFileUrl
      get() = getEntityData().simpleUrl
      set(value) {
        checkModificationAllowed()
        getEntityData().simpleUrl = value
        changedProperty.add("simpleUrl")
        val _diff = diff
        if (_diff != null) index(this, "simpleUrl", value)
      }

    override var nullableUrl: VirtualFileUrl?
      get() = getEntityData().nullableUrl
      set(value) {
        checkModificationAllowed()
        getEntityData().nullableUrl = value
        changedProperty.add("nullableUrl")
        val _diff = diff
        if (_diff != null) index(this, "nullableUrl", value)
      }

    private val listOfUrlsUpdater: (value: List<VirtualFileUrl>) -> Unit = { value ->
      val _diff = diff
      if (_diff != null) index(this, "listOfUrls", value.toHashSet())
      changedProperty.add("listOfUrls")
    }
    override var listOfUrls: MutableList<VirtualFileUrl>
      get() {
        val collection_listOfUrls = getEntityData().listOfUrls
        if (collection_listOfUrls !is MutableWorkspaceList) return collection_listOfUrls
        collection_listOfUrls.setModificationUpdateAction(listOfUrlsUpdater)
        return collection_listOfUrls
      }
      set(value) {
        checkModificationAllowed()
        getEntityData().listOfUrls = value
        listOfUrlsUpdater.invoke(value)
      }

    override var dataClassWithUrl: DataClassWithUrl
      get() = getEntityData().dataClassWithUrl
      set(value) {
        checkModificationAllowed()
        getEntityData().dataClassWithUrl = value
        changedProperty.add("dataClassWithUrl")

      }

    override fun getEntityData(): EntityWithUrlsData = result ?: super.getEntityData() as EntityWithUrlsData
    override fun getEntityClass(): Class<EntityWithUrls> = EntityWithUrls::class.java
  }
}

class EntityWithUrlsData : WorkspaceEntityData<EntityWithUrls>() {
  lateinit var simpleUrl: VirtualFileUrl
  var nullableUrl: VirtualFileUrl? = null
  lateinit var listOfUrls: MutableList<VirtualFileUrl>
  lateinit var dataClassWithUrl: DataClassWithUrl

  fun isSimpleUrlInitialized(): Boolean = ::simpleUrl.isInitialized
  fun isListOfUrlsInitialized(): Boolean = ::listOfUrls.isInitialized
  fun isDataClassWithUrlInitialized(): Boolean = ::dataClassWithUrl.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<EntityWithUrls> {
    val modifiable = EntityWithUrlsImpl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
      modifiable.entitySource = this.entitySource
    }
    modifiable.changedProperty.clear()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): EntityWithUrls {
    val entity = EntityWithUrlsImpl()
    entity._simpleUrl = simpleUrl
    entity._nullableUrl = nullableUrl
    entity._listOfUrls = listOfUrls.toList()
    entity._dataClassWithUrl = dataClassWithUrl
    entity.entitySource = entitySource
    entity.snapshot = snapshot
    entity.id = createEntityId()
    return entity
  }

  override fun clone(): EntityWithUrlsData {
    val clonedEntity = super.clone()
    clonedEntity as EntityWithUrlsData
    clonedEntity.listOfUrls = clonedEntity.listOfUrls.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return EntityWithUrls::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return EntityWithUrls(simpleUrl, listOfUrls, dataClassWithUrl, entitySource) {
      this.nullableUrl = this@EntityWithUrlsData.nullableUrl
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as EntityWithUrlsData

    if (this.entitySource != other.entitySource) return false
    if (this.simpleUrl != other.simpleUrl) return false
    if (this.nullableUrl != other.nullableUrl) return false
    if (this.listOfUrls != other.listOfUrls) return false
    if (this.dataClassWithUrl != other.dataClassWithUrl) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as EntityWithUrlsData

    if (this.simpleUrl != other.simpleUrl) return false
    if (this.nullableUrl != other.nullableUrl) return false
    if (this.listOfUrls != other.listOfUrls) return false
    if (this.dataClassWithUrl != other.dataClassWithUrl) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + simpleUrl.hashCode()
    result = 31 * result + nullableUrl.hashCode()
    result = 31 * result + listOfUrls.hashCode()
    result = 31 * result + dataClassWithUrl.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + simpleUrl.hashCode()
    result = 31 * result + nullableUrl.hashCode()
    result = 31 * result + listOfUrls.hashCode()
    result = 31 * result + dataClassWithUrl.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.add(DataClassWithUrl::class.java)
    this.listOfUrls?.let { collector.add(it::class.java) }
    this.dataClassWithUrl?.let { collector.add(it::class.java) }
    this.nullableUrl?.let { collector.add(it::class.java) }
    this.simpleUrl?.let { collector.add(it::class.java) }
    collector.sameForAllEntities = false
  }
}
