// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.test.api.impl

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.workspaceModel.test.api.BaseDataClass
import com.intellij.workspaceModel.test.api.ChildEntity
import com.intellij.workspaceModel.test.api.DerivedDataClass
import com.intellij.workspaceModel.test.api.DerivedDerivedDataClass
import com.intellij.workspaceModel.test.api.SimpleId

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ChildEntityImpl(private val dataSource: ChildEntityData) : ChildEntity, WorkspaceEntityBase(dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val name: String
    get() {
      readField("name")
      return dataSource.name
    }

  override val moduleId: SimpleId
    get() {
      readField("moduleId")
      return dataSource.moduleId
    }

  override val aBaseEntityProperty: String
    get() {
      readField("aBaseEntityProperty")
      return dataSource.aBaseEntityProperty
    }

  override val dBaseEntityProperty: String
    get() {
      readField("dBaseEntityProperty")
      return dataSource.dBaseEntityProperty
    }

  override val bBaseEntityProperty: String
    get() {
      readField("bBaseEntityProperty")
      return dataSource.bBaseEntityProperty
    }

  override val sealedDataClassProperty: BaseDataClass
    get() {
      readField("sealedDataClassProperty")
      return dataSource.sealedDataClassProperty
    }

  override val cChildEntityProperty: String
    get() {
      readField("cChildEntityProperty")
      return dataSource.cChildEntityProperty
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: ChildEntityData?) : ModifiableWorkspaceEntityBase<ChildEntity, ChildEntityData>(result),
                                                     ChildEntity.Builder {
    internal constructor() : this(ChildEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ChildEntity is already created in a different builder")
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
        error("Field BaseEntity#name should be initialized")
      }
      if (!getEntityData().isModuleIdInitialized()) {
        error("Field BaseEntity#moduleId should be initialized")
      }
      if (!getEntityData().isABaseEntityPropertyInitialized()) {
        error("Field BaseEntity#aBaseEntityProperty should be initialized")
      }
      if (!getEntityData().isDBaseEntityPropertyInitialized()) {
        error("Field BaseEntity#dBaseEntityProperty should be initialized")
      }
      if (!getEntityData().isBBaseEntityPropertyInitialized()) {
        error("Field BaseEntity#bBaseEntityProperty should be initialized")
      }
      if (!getEntityData().isSealedDataClassPropertyInitialized()) {
        error("Field BaseEntity#sealedDataClassProperty should be initialized")
      }
      if (!getEntityData().isCChildEntityPropertyInitialized()) {
        error("Field ChildEntity#cChildEntityProperty should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ChildEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.moduleId != dataSource.moduleId) this.moduleId = dataSource.moduleId
      if (this.aBaseEntityProperty != dataSource.aBaseEntityProperty) this.aBaseEntityProperty = dataSource.aBaseEntityProperty
      if (this.dBaseEntityProperty != dataSource.dBaseEntityProperty) this.dBaseEntityProperty = dataSource.dBaseEntityProperty
      if (this.bBaseEntityProperty != dataSource.bBaseEntityProperty) this.bBaseEntityProperty = dataSource.bBaseEntityProperty
      if (this.sealedDataClassProperty != dataSource.sealedDataClassProperty) this.sealedDataClassProperty =
        dataSource.sealedDataClassProperty
      if (this.cChildEntityProperty != dataSource.cChildEntityProperty) this.cChildEntityProperty = dataSource.cChildEntityProperty
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

    override var moduleId: SimpleId
      get() = getEntityData().moduleId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).moduleId = value
        changedProperty.add("moduleId")

      }

    override var aBaseEntityProperty: String
      get() = getEntityData().aBaseEntityProperty
      set(value) {
        checkModificationAllowed()
        getEntityData(true).aBaseEntityProperty = value
        changedProperty.add("aBaseEntityProperty")
      }

    override var dBaseEntityProperty: String
      get() = getEntityData().dBaseEntityProperty
      set(value) {
        checkModificationAllowed()
        getEntityData(true).dBaseEntityProperty = value
        changedProperty.add("dBaseEntityProperty")
      }

    override var bBaseEntityProperty: String
      get() = getEntityData().bBaseEntityProperty
      set(value) {
        checkModificationAllowed()
        getEntityData(true).bBaseEntityProperty = value
        changedProperty.add("bBaseEntityProperty")
      }

    override var sealedDataClassProperty: BaseDataClass
      get() = getEntityData().sealedDataClassProperty
      set(value) {
        checkModificationAllowed()
        getEntityData(true).sealedDataClassProperty = value
        changedProperty.add("sealedDataClassProperty")

      }

    override var cChildEntityProperty: String
      get() = getEntityData().cChildEntityProperty
      set(value) {
        checkModificationAllowed()
        getEntityData(true).cChildEntityProperty = value
        changedProperty.add("cChildEntityProperty")
      }

    override fun getEntityClass(): Class<ChildEntity> = ChildEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ChildEntityData : WorkspaceEntityData<ChildEntity>(), SoftLinkable {
  lateinit var name: String
  lateinit var moduleId: SimpleId
  lateinit var aBaseEntityProperty: String
  lateinit var dBaseEntityProperty: String
  lateinit var bBaseEntityProperty: String
  lateinit var sealedDataClassProperty: BaseDataClass
  lateinit var cChildEntityProperty: String

  internal fun isNameInitialized(): Boolean = ::name.isInitialized
  internal fun isModuleIdInitialized(): Boolean = ::moduleId.isInitialized
  internal fun isABaseEntityPropertyInitialized(): Boolean = ::aBaseEntityProperty.isInitialized
  internal fun isDBaseEntityPropertyInitialized(): Boolean = ::dBaseEntityProperty.isInitialized
  internal fun isBBaseEntityPropertyInitialized(): Boolean = ::bBaseEntityProperty.isInitialized
  internal fun isSealedDataClassPropertyInitialized(): Boolean = ::sealedDataClassProperty.isInitialized
  internal fun isCChildEntityPropertyInitialized(): Boolean = ::cChildEntityProperty.isInitialized

  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    result.add(moduleId)
    val _sealedDataClassProperty = sealedDataClassProperty
    when (_sealedDataClassProperty) {
      is DerivedDataClass -> {
        val __sealedDataClassProperty = _sealedDataClassProperty
        when (__sealedDataClassProperty) {
          is DerivedDerivedDataClass -> {
          }
        }
      }
    }
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    index.index(this, moduleId)
    val _sealedDataClassProperty = sealedDataClassProperty
    when (_sealedDataClassProperty) {
      is DerivedDataClass -> {
        val __sealedDataClassProperty = _sealedDataClassProperty
        when (__sealedDataClassProperty) {
          is DerivedDerivedDataClass -> {
          }
        }
      }
    }
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    // TODO verify logic
    val mutablePreviousSet = HashSet(prev)
    val removedItem_moduleId = mutablePreviousSet.remove(moduleId)
    if (!removedItem_moduleId) {
      index.index(this, moduleId)
    }
    val _sealedDataClassProperty = sealedDataClassProperty
    when (_sealedDataClassProperty) {
      is DerivedDataClass -> {
        val __sealedDataClassProperty = _sealedDataClassProperty
        when (__sealedDataClassProperty) {
          is DerivedDerivedDataClass -> {
          }
        }
      }
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
    }
  }

  override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
    var changed = false
    val moduleId_data = if (moduleId == oldLink) {
      changed = true
      newLink as SimpleId
    }
    else {
      null
    }
    if (moduleId_data != null) {
      moduleId = moduleId_data
    }
    val _sealedDataClassProperty = sealedDataClassProperty
    val res_sealedDataClassProperty = when (_sealedDataClassProperty) {
      is DerivedDataClass -> {
        val __sealedDataClassProperty = _sealedDataClassProperty
        val res__sealedDataClassProperty = when (__sealedDataClassProperty) {
          is DerivedDerivedDataClass -> {
            __sealedDataClassProperty
          }
        }
        res__sealedDataClassProperty
      }
    }
    if (res_sealedDataClassProperty != null) {
      sealedDataClassProperty = res_sealedDataClassProperty
    }
    return changed
  }

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ChildEntity> {
    val modifiable = ChildEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): ChildEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = ChildEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.workspaceModel.test.api.ChildEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ChildEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return ChildEntity(
      name, moduleId, aBaseEntityProperty, dBaseEntityProperty, bBaseEntityProperty, sealedDataClassProperty, cChildEntityProperty,
      entitySource
    ) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ChildEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.name != other.name) return false
    if (this.moduleId != other.moduleId) return false
    if (this.aBaseEntityProperty != other.aBaseEntityProperty) return false
    if (this.dBaseEntityProperty != other.dBaseEntityProperty) return false
    if (this.bBaseEntityProperty != other.bBaseEntityProperty) return false
    if (this.sealedDataClassProperty != other.sealedDataClassProperty) return false
    if (this.cChildEntityProperty != other.cChildEntityProperty) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ChildEntityData

    if (this.name != other.name) return false
    if (this.moduleId != other.moduleId) return false
    if (this.aBaseEntityProperty != other.aBaseEntityProperty) return false
    if (this.dBaseEntityProperty != other.dBaseEntityProperty) return false
    if (this.bBaseEntityProperty != other.bBaseEntityProperty) return false
    if (this.sealedDataClassProperty != other.sealedDataClassProperty) return false
    if (this.cChildEntityProperty != other.cChildEntityProperty) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + moduleId.hashCode()
    result = 31 * result + aBaseEntityProperty.hashCode()
    result = 31 * result + dBaseEntityProperty.hashCode()
    result = 31 * result + bBaseEntityProperty.hashCode()
    result = 31 * result + sealedDataClassProperty.hashCode()
    result = 31 * result + cChildEntityProperty.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + moduleId.hashCode()
    result = 31 * result + aBaseEntityProperty.hashCode()
    result = 31 * result + dBaseEntityProperty.hashCode()
    result = 31 * result + bBaseEntityProperty.hashCode()
    result = 31 * result + sealedDataClassProperty.hashCode()
    result = 31 * result + cChildEntityProperty.hashCode()
    return result
  }
}
