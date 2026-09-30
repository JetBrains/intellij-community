// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.storage.testEntities.entities.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.DataClassWithVfus
import com.intellij.platform.workspace.storage.testEntities.entities.EntityWithDataClassWithVfu
import com.intellij.platform.workspace.storage.testEntities.entities.EntityWithDataClassWithVfuBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class EntityWithDataClassWithVfuImpl(private val dataSource: EntityWithDataClassWithVfuData) : EntityWithDataClassWithVfu,
                                                                                                        WorkspaceEntityBase(dataSource) {

  override val singleDataClass: DataClassWithVfus
    get() {
      readField("singleDataClass")
      return dataSource.singleDataClass
    }
  override val listOfDataClass: List<DataClassWithVfus>
    get() {
      readField("listOfDataClass")
      return dataSource.listOfDataClass
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return emptyList()
  }

  internal class Builder(result: EntityWithDataClassWithVfuData?) :
    ModifiableWorkspaceEntityBase<EntityWithDataClassWithVfu, EntityWithDataClassWithVfuData>(result), EntityWithDataClassWithVfuBuilder {
    internal constructor() : this(EntityWithDataClassWithVfuData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isSingleDataClassInitialized()) {
        error("Field EntityWithDataClassWithVfu#singleDataClass should be initialized")
      }
      if (!getEntityData().isListOfDataClassInitialized()) {
        error("Field EntityWithDataClassWithVfu#listOfDataClass should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return emptyList()
    }

    override fun afterModification() {
      val collection_listOfDataClass = getEntityData().listOfDataClass
      if (collection_listOfDataClass is MutableWorkspaceList<*>) {
        collection_listOfDataClass.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as EntityWithDataClassWithVfu
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.singleDataClass != dataSource.singleDataClass) this.singleDataClass = dataSource.singleDataClass
      if (this.listOfDataClass != dataSource.listOfDataClass) this.listOfDataClass = dataSource.listOfDataClass.toMutableList()
      updateChildToParentReferences(parents)
    }

    override fun index() {
      index(this, "singleDataClass.vfu", this.singleDataClass.vfu)
      index(this, "singleDataClass.vfus", this.singleDataClass.vfus)
      index(this, "listOfDataClass.vfu", this.listOfDataClass.map { it.vfu })
      index(this, "listOfDataClass.vfus", this.listOfDataClass.flatMap { it.vfus })
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var singleDataClass: DataClassWithVfus
      get() = getEntityData().singleDataClass
      set(value) {
        checkModificationAllowed()
        getEntityData(true).singleDataClass = value
        changedProperty.add("singleDataClass")
        val _diff = diff
        if (_diff != null) {
          index(this, "singleDataClass.vfu", value.vfu)
          index(this, "singleDataClass.vfus", value.vfus)
        }
      }
    private val listOfDataClassUpdater: (value: List<DataClassWithVfus>) -> Unit = { value ->
      if (diff != null) {
        index(this, "listOfDataClass.vfu", value.map { it.vfu })
        index(this, "listOfDataClass.vfus", value.flatMap { it.vfus })
      }
      changedProperty.add("listOfDataClass")
    }
    override var listOfDataClass: MutableList<DataClassWithVfus>
      get() {
        val collection_listOfDataClass = getEntityData().listOfDataClass
        if (collection_listOfDataClass !is MutableWorkspaceList) return collection_listOfDataClass
        if (diff == null || modifiable.get()) {
          collection_listOfDataClass.setModificationUpdateAction(listOfDataClassUpdater)
        }
        else {
          collection_listOfDataClass.cleanModificationUpdateAction()
        }
        return collection_listOfDataClass
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).listOfDataClass = value
        listOfDataClassUpdater.invoke(value)
      }

    override fun getEntityClass(): Class<EntityWithDataClassWithVfu> = EntityWithDataClassWithVfu::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class EntityWithDataClassWithVfuData : WorkspaceEntityData<EntityWithDataClassWithVfu>() {
  lateinit var singleDataClass: DataClassWithVfus
  lateinit var listOfDataClass: MutableList<DataClassWithVfus>
  internal fun isSingleDataClassInitialized(): Boolean = ::singleDataClass.isInitialized
  internal fun isListOfDataClassInitialized(): Boolean = ::listOfDataClass.isInitialized
  override fun newInstance(): EntityWithDataClassWithVfu = EntityWithDataClassWithVfuImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<EntityWithDataClassWithVfu, *> =
    EntityWithDataClassWithVfuImpl.Builder(null)

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.EntityWithDataClassWithVfu") as EntityMetadata
  }

  override fun clone(): EntityWithDataClassWithVfuData {
    val clonedEntity = super.clone()
    clonedEntity as EntityWithDataClassWithVfuData
    clonedEntity.listOfDataClass = clonedEntity.listOfDataClass.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return EntityWithDataClassWithVfu::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return EntityWithDataClassWithVfu(singleDataClass, listOfDataClass, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as EntityWithDataClassWithVfuData
    if (this.entitySource != other.entitySource) return false
    if (this.singleDataClass != other.singleDataClass) return false
    if (this.listOfDataClass != other.listOfDataClass) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as EntityWithDataClassWithVfuData
    if (this.singleDataClass != other.singleDataClass) return false
    if (this.listOfDataClass != other.listOfDataClass) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + singleDataClass.hashCode()
    result = 31 * result + listOfDataClass.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + singleDataClass.hashCode()
    result = 31 * result + listOfDataClass.hashCode()
    return result
  }
}
