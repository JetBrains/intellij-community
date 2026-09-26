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
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.XChildWithOptionalParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XChildWithOptionalParentEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.XParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XParentEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class XChildWithOptionalParentEntityImpl(private val dataSource: XChildWithOptionalParentEntityData) :
  XChildWithOptionalParentEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val OPTIONALPARENT_CONNECTION_ID: ConnectionId = ConnectionId.create(XParentEntity::class.java,
                                                                                  XChildWithOptionalParentEntity::class.java,
                                                                                  ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                  true)
    private val connections = listOf<ConnectionId>(OPTIONALPARENT_CONNECTION_ID)
  }

  override val childProperty: String
    get() {
      readField("childProperty")
      return dataSource.childProperty
    }
  override val optionalParent: XParentEntity?
    get() = snapshot.instrumentation.getParent(OPTIONALPARENT_CONNECTION_ID, this) as? XParentEntity
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: XChildWithOptionalParentEntityData?) :
    ModifiableWorkspaceEntityBase<XChildWithOptionalParentEntity, XChildWithOptionalParentEntityData>(result),
    XChildWithOptionalParentEntityBuilder {
    internal constructor() : this(XChildWithOptionalParentEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isChildPropertyInitialized()) {
        error("Field XChildWithOptionalParentEntity#childProperty should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as XChildWithOptionalParentEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.childProperty != dataSource.childProperty) this.childProperty = dataSource.childProperty
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var childProperty: String
      get() = getEntityData().childProperty
      set(value) {
        checkModificationAllowed()
        getEntityData(true).childProperty = value
        changedProperty.add("childProperty")
      }
    override var optionalParent: XParentEntityBuilder?
      get() = getParent(OPTIONALPARENT_CONNECTION_ID) as? XParentEntityBuilder?
              ?: error("optionalParent is null for XChildWithOptionalParentEntity")
      set(value) {
        changeParentOfMany(value, OPTIONALPARENT_CONNECTION_ID)
        changedProperty.add("optionalParent")
      }

    override fun getEntityClass(): Class<XChildWithOptionalParentEntity> = XChildWithOptionalParentEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class XChildWithOptionalParentEntityData : WorkspaceEntityData<XChildWithOptionalParentEntity>() {
  lateinit var childProperty: String
  internal fun isChildPropertyInitialized(): Boolean = ::childProperty.isInitialized
  override fun newInstance(): XChildWithOptionalParentEntity = XChildWithOptionalParentEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<XChildWithOptionalParentEntity, *> =
    XChildWithOptionalParentEntityImpl.Builder(null)

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.XChildWithOptionalParentEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return XChildWithOptionalParentEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return XChildWithOptionalParentEntity(childProperty, entitySource) {
      this.optionalParent = parents.filterIsInstance<XParentEntityBuilder>().singleOrNull()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as XChildWithOptionalParentEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.childProperty != other.childProperty) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as XChildWithOptionalParentEntityData
    if (this.childProperty != other.childProperty) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + childProperty.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + childProperty.hashCode()
    return result
  }
}
