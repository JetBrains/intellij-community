// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities.impl

import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.FacetEntityTypeId
import com.intellij.platform.workspace.jps.entities.FacetId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.extractOneToManyParent
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.impl.updateOneToManyParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import org.jetbrains.annotations.NonNls

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class FacetEntityImpl(private val dataSource: FacetEntityData) : FacetEntity, WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ModuleEntity::class.java, FacetEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val UNDERLYINGFACET_CONNECTION_ID: ConnectionId =
      ConnectionId.create(FacetEntity::class.java, FacetEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)

    private val connections = listOf<ConnectionId>(
      MODULE_CONNECTION_ID,
      UNDERLYINGFACET_CONNECTION_ID,
    )

  }

  override val symbolicId: FacetId = super.symbolicId

  override val moduleId: ModuleId
    get() {
      readField("moduleId")
      return dataSource.moduleId
    }

  override val name: String
    get() {
      readField("name")
      return dataSource.name
    }

  override val typeId: FacetEntityTypeId
    get() {
      readField("typeId")
      return dataSource.typeId
    }

  override val configurationXmlTag: String?
    get() {
      readField("configurationXmlTag")
      return dataSource.configurationXmlTag
    }

  override val module: ModuleEntity
    get() = snapshot.extractOneToManyParent(MODULE_CONNECTION_ID, this)!!

  override val underlyingFacet: FacetEntity?
    get() = snapshot.extractOneToManyParent(UNDERLYINGFACET_CONNECTION_ID, this)

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: FacetEntityData?) : ModifiableWorkspaceEntityBase<FacetEntity, FacetEntityData>(result),
                                                     FacetEntity.Builder {
    internal constructor() : this(FacetEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity FacetEntity is already created in a different builder")
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
      if (!getEntityData().isModuleIdInitialized()) {
        error("Field ModuleSettingsFacetBridgeEntity#moduleId should be initialized")
      }
      if (!getEntityData().isNameInitialized()) {
        error("Field ModuleSettingsFacetBridgeEntity#name should be initialized")
      }
      if (!getEntityData().isTypeIdInitialized()) {
        error("Field FacetEntity#typeId should be initialized")
      }
      if (_diff != null) {
        if (_diff.extractOneToManyParent<WorkspaceEntityBase>(MODULE_CONNECTION_ID, this) == null) {
          error("Field FacetEntity#module should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null) {
          error("Field FacetEntity#module should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as FacetEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.moduleId != dataSource.moduleId) this.moduleId = dataSource.moduleId
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.typeId != dataSource.typeId) this.typeId = dataSource.typeId
      if (this.configurationXmlTag != dataSource?.configurationXmlTag) this.configurationXmlTag = dataSource.configurationXmlTag
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var moduleId: ModuleId
      get() = getEntityData().moduleId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).moduleId = value
        changedProperty.add("moduleId")

      }

    override var name: String
      get() = getEntityData().name
      set(value) {
        checkModificationAllowed()
        getEntityData(true).name = value
        changedProperty.add("name")
      }

    override var typeId: FacetEntityTypeId
      get() = getEntityData().typeId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).typeId = value
        changedProperty.add("typeId")

      }

    override var configurationXmlTag: String?
      get() = getEntityData().configurationXmlTag
      set(value) {
        checkModificationAllowed()
        getEntityData(true).configurationXmlTag = value
        changedProperty.add("configurationXmlTag")
      }

    override var module: ModuleEntity.Builder
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(MODULE_CONNECTION_ID, this) as? ModuleEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(MODULE_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] = value
        }
        changedProperty.add("module")
      }

    override var underlyingFacet: FacetEntity.Builder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(UNDERLYINGFACET_CONNECTION_ID, this) as? FacetEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, UNDERLYINGFACET_CONNECTION_ID)] as? FacetEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, UNDERLYINGFACET_CONNECTION_ID)] as? FacetEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, UNDERLYINGFACET_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, UNDERLYINGFACET_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(UNDERLYINGFACET_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, UNDERLYINGFACET_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, UNDERLYINGFACET_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, UNDERLYINGFACET_CONNECTION_ID)] = value
        }
        changedProperty.add("underlyingFacet")
      }

    override fun getEntityClass(): Class<FacetEntity> = FacetEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class FacetEntityData : WorkspaceEntityData<FacetEntity>(), SoftLinkable {
  lateinit var moduleId: ModuleId
  lateinit var name: String
  lateinit var typeId: FacetEntityTypeId
  var configurationXmlTag: String? = null

  internal fun isModuleIdInitialized(): Boolean = ::moduleId.isInitialized
  internal fun isNameInitialized(): Boolean = ::name.isInitialized
  internal fun isTypeIdInitialized(): Boolean = ::typeId.isInitialized

  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    result.add(moduleId)
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    index.index(this, moduleId)
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    // TODO verify logic
    val mutablePreviousSet = HashSet(prev)
    val removedItem_moduleId = mutablePreviousSet.remove(moduleId)
    if (!removedItem_moduleId) {
      index.index(this, moduleId)
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
    }
  }

  override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
    var changed = false
    val moduleId_data = if (moduleId == oldLink) {
      changed = true
      newLink as ModuleId
    }
    else {
      null
    }
    if (moduleId_data != null) {
      moduleId = moduleId_data
    }
    return changed
  }

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<FacetEntity> {
    val modifiable = FacetEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): FacetEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = FacetEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.jps.entities.FacetEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return FacetEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return FacetEntity(moduleId, name, typeId, entitySource) {
      this.configurationXmlTag = this@FacetEntityData.configurationXmlTag
      parents.filterIsInstance<ModuleEntity.Builder>().singleOrNull()?.let { this.module = it }
      this.underlyingFacet = parents.filterIsInstance<FacetEntity.Builder>().singleOrNull()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ModuleEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as FacetEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.moduleId != other.moduleId) return false
    if (this.name != other.name) return false
    if (this.typeId != other.typeId) return false
    if (this.configurationXmlTag != other.configurationXmlTag) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as FacetEntityData

    if (this.moduleId != other.moduleId) return false
    if (this.name != other.name) return false
    if (this.typeId != other.typeId) return false
    if (this.configurationXmlTag != other.configurationXmlTag) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + moduleId.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + typeId.hashCode()
    result = 31 * result + configurationXmlTag.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + moduleId.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + typeId.hashCode()
    result = 31 * result + configurationXmlTag.hashCode()
    return result
  }
}
