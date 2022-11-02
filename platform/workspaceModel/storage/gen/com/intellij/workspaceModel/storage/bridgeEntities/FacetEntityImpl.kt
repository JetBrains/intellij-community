// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.SymbolicEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.EntityLink
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.SoftLinkable
import com.intellij.workspaceModel.storage.impl.UsedClassesCollector
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToManyParent
import com.intellij.workspaceModel.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.workspaceModel.storage.impl.updateOneToManyParentOfChild
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class FacetEntityImpl(val dataSource: FacetEntityData) : FacetEntity, WorkspaceEntityBase() {

  companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, FacetEntity::class.java,
                                                                          ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val UNDERLYINGFACET_CONNECTION_ID: ConnectionId = ConnectionId.create(FacetEntity::class.java, FacetEntity::class.java,
                                                                                   ConnectionId.ConnectionType.ONE_TO_MANY, true)

    val connections = listOf<ConnectionId>(
      MODULE_CONNECTION_ID,
      UNDERLYINGFACET_CONNECTION_ID,
    )

  }

  override val name: String
    get() = dataSource.name

  override val module: ModuleEntity
    get() = snapshot.extractOneToManyParent(MODULE_CONNECTION_ID, this)!!

  override val facetType: String
    get() = dataSource.facetType

  override val configurationXmlTag: String?
    get() = dataSource.configurationXmlTag

  override val moduleId: ModuleId
    get() = dataSource.moduleId

  override val underlyingFacet: FacetEntity?
    get() = snapshot.extractOneToManyParent(UNDERLYINGFACET_CONNECTION_ID, this)

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(var result: FacetEntityData?) : ModifiableWorkspaceEntityBase<FacetEntity>(), FacetEntity.Builder {
    constructor() : this(FacetEntityData())

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
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.result = null

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
        error("Field FacetEntity#name should be initialized")
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
      if (!getEntityData().isFacetTypeInitialized()) {
        error("Field FacetEntity#facetType should be initialized")
      }
      if (!getEntityData().isModuleIdInitialized()) {
        error("Field FacetEntity#moduleId should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as FacetEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.facetType != dataSource.facetType) this.facetType = dataSource.facetType
      if (this.configurationXmlTag != dataSource?.configurationXmlTag) this.configurationXmlTag = dataSource.configurationXmlTag
      if (this.moduleId != dataSource.moduleId) this.moduleId = dataSource.moduleId
      if (parents != null) {
        val moduleNew = parents.filterIsInstance<ModuleEntity>().single()
        if ((this.module as WorkspaceEntityBase).id != (moduleNew as WorkspaceEntityBase).id) {
          this.module = moduleNew
        }
        val underlyingFacetNew = parents.filterIsInstance<FacetEntity?>().singleOrNull()
        if ((underlyingFacetNew == null && this.underlyingFacet != null) || (underlyingFacetNew != null && this.underlyingFacet == null) || (underlyingFacetNew != null && this.underlyingFacet != null && (this.underlyingFacet as WorkspaceEntityBase).id != (underlyingFacetNew as WorkspaceEntityBase).id)) {
          this.underlyingFacet = underlyingFacetNew
        }
      }
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData().entitySource = value
        changedProperty.add("entitySource")

      }

    override var name: String
      get() = getEntityData().name
      set(value) {
        checkModificationAllowed()
        getEntityData().name = value
        changedProperty.add("name")
      }

    override var module: ModuleEntity
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyParent(MODULE_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                  MODULE_CONNECTION_ID)]!! as ModuleEntity
        }
        else {
          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*>) {
            val data = (value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(MODULE_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*>) {
            val data = (value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] = value
        }
        changedProperty.add("module")
      }

    override var facetType: String
      get() = getEntityData().facetType
      set(value) {
        checkModificationAllowed()
        getEntityData().facetType = value
        changedProperty.add("facetType")
      }

    override var configurationXmlTag: String?
      get() = getEntityData().configurationXmlTag
      set(value) {
        checkModificationAllowed()
        getEntityData().configurationXmlTag = value
        changedProperty.add("configurationXmlTag")
      }

    override var moduleId: ModuleId
      get() = getEntityData().moduleId
      set(value) {
        checkModificationAllowed()
        getEntityData().moduleId = value
        changedProperty.add("moduleId")

      }

    override var underlyingFacet: FacetEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyParent(UNDERLYINGFACET_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                           UNDERLYINGFACET_CONNECTION_ID)] as? FacetEntity
        }
        else {
          this.entityLinks[EntityLink(false, UNDERLYINGFACET_CONNECTION_ID)] as? FacetEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*>) {
            val data = (value.entityLinks[EntityLink(true, UNDERLYINGFACET_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, UNDERLYINGFACET_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(UNDERLYINGFACET_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*>) {
            val data = (value.entityLinks[EntityLink(true, UNDERLYINGFACET_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, UNDERLYINGFACET_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, UNDERLYINGFACET_CONNECTION_ID)] = value
        }
        changedProperty.add("underlyingFacet")
      }

    override fun getEntityData(): FacetEntityData = result ?: super.getEntityData() as FacetEntityData
    override fun getEntityClass(): Class<FacetEntity> = FacetEntity::class.java
  }
}

class FacetEntityData : WorkspaceEntityData.WithCalculableSymbolicId<FacetEntity>(), SoftLinkable {
  lateinit var name: String
  lateinit var facetType: String
  var configurationXmlTag: String? = null
  lateinit var moduleId: ModuleId

  fun isNameInitialized(): Boolean = ::name.isInitialized
  fun isFacetTypeInitialized(): Boolean = ::facetType.isInitialized
  fun isModuleIdInitialized(): Boolean = ::moduleId.isInitialized

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
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): FacetEntity {
    return getCached(snapshot) {
      val entity = FacetEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun symbolicId(): SymbolicEntityId<*> {
    return FacetId(name, facetType, moduleId)
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return FacetEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return FacetEntity(name, facetType, moduleId, entitySource) {
      this.configurationXmlTag = this@FacetEntityData.configurationXmlTag
      this.module = parents.filterIsInstance<ModuleEntity>().single()
      this.underlyingFacet = parents.filterIsInstance<FacetEntity>().singleOrNull()
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
    if (this.name != other.name) return false
    if (this.facetType != other.facetType) return false
    if (this.configurationXmlTag != other.configurationXmlTag) return false
    if (this.moduleId != other.moduleId) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as FacetEntityData

    if (this.name != other.name) return false
    if (this.facetType != other.facetType) return false
    if (this.configurationXmlTag != other.configurationXmlTag) return false
    if (this.moduleId != other.moduleId) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + facetType.hashCode()
    result = 31 * result + configurationXmlTag.hashCode()
    result = 31 * result + moduleId.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + facetType.hashCode()
    result = 31 * result + configurationXmlTag.hashCode()
    result = 31 * result + moduleId.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.add(ModuleId::class.java)
    collector.sameForAllEntities = true
  }
}
