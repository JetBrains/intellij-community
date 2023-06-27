// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntityInformation
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.UsedClassesCollector
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.extractOneToManyParent
import com.intellij.platform.workspace.storage.impl.updateOneToManyParentOfChild
import org.jetbrains.annotations.NonNls

@GeneratedCodeApiVersion(2)
@GeneratedCodeImplVersion(2)
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

  override val moduleId: ModuleId
    get() = dataSource.moduleId

  override val module: ModuleEntity
    get() = snapshot.extractOneToManyParent(MODULE_CONNECTION_ID, this)!!

  override val facetType: String
    get() = dataSource.facetType

  override val configurationXmlTag: String?
    get() = dataSource.configurationXmlTag

  override val underlyingFacet: FacetEntity?
    get() = snapshot.extractOneToManyParent(UNDERLYINGFACET_CONNECTION_ID, this)

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: FacetEntityData?) : ModifiableWorkspaceEntityBase<FacetEntity, FacetEntityData>(result), FacetEntity.Builder {
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
        error("Field ModuleSettingsBase#name should be initialized")
      }
      if (!getEntityData().isModuleIdInitialized()) {
        error("Field ModuleSettingsBase#moduleId should be initialized")
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
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as FacetEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.moduleId != dataSource.moduleId) this.moduleId = dataSource.moduleId
      if (this.facetType != dataSource.facetType) this.facetType = dataSource.facetType
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

    override var name: String
      get() = getEntityData().name
      set(value) {
        checkModificationAllowed()
        getEntityData(true).name = value
        changedProperty.add("name")
      }

    override var moduleId: ModuleId
      get() = getEntityData().moduleId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).moduleId = value
        changedProperty.add("moduleId")

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
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
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

    override var facetType: String
      get() = getEntityData().facetType
      set(value) {
        checkModificationAllowed()
        getEntityData(true).facetType = value
        changedProperty.add("facetType")
      }

    override var configurationXmlTag: String?
      get() = getEntityData().configurationXmlTag
      set(value) {
        checkModificationAllowed()
        getEntityData(true).configurationXmlTag = value
        changedProperty.add("configurationXmlTag")
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
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, UNDERLYINGFACET_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, UNDERLYINGFACET_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
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

class FacetEntityData : WorkspaceEntityData.WithCalculableSymbolicId<FacetEntity>() {
  lateinit var name: String
  lateinit var moduleId: ModuleId
  lateinit var facetType: String
  var configurationXmlTag: String? = null

  fun isNameInitialized(): Boolean = ::name.isInitialized
  fun isModuleIdInitialized(): Boolean = ::moduleId.isInitialized
  fun isFacetTypeInitialized(): Boolean = ::facetType.isInitialized

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
    return FacetEntity(name, moduleId, facetType, entitySource) {
      this.configurationXmlTag = this@FacetEntityData.configurationXmlTag
      parents.filterIsInstance<ModuleEntity>().singleOrNull()?.let { this.module = it }
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
    if (this.moduleId != other.moduleId) return false
    if (this.facetType != other.facetType) return false
    if (this.configurationXmlTag != other.configurationXmlTag) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as FacetEntityData

    if (this.name != other.name) return false
    if (this.moduleId != other.moduleId) return false
    if (this.facetType != other.facetType) return false
    if (this.configurationXmlTag != other.configurationXmlTag) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + moduleId.hashCode()
    result = 31 * result + facetType.hashCode()
    result = 31 * result + configurationXmlTag.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + moduleId.hashCode()
    result = 31 * result + facetType.hashCode()
    result = 31 * result + configurationXmlTag.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.add(ModuleId::class.java)
    collector.sameForAllEntities = true
  }
}
