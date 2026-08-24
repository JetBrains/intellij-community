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
import com.intellij.platform.workspace.storage.testEntities.entities.XChildChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XChildChildEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.XChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XChildEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.XChildWithOptionalParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XChildWithOptionalParentEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.XParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XParentEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class XParentEntityImpl(private val dataSource: XParentEntityData) : XParentEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val CHILDREN_CONNECTION_ID: ConnectionId =
      ConnectionId.create(XParentEntity::class.java, XChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val OPTIONALCHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(XParentEntity::class.java,
                                                                                    XChildWithOptionalParentEntity::class.java,
                                                                                    ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                    true)
    internal val CHILDCHILD_CONNECTION_ID: ConnectionId =
      ConnectionId.create(XParentEntity::class.java, XChildChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    private val connections = listOf<ConnectionId>(CHILDREN_CONNECTION_ID, OPTIONALCHILDREN_CONNECTION_ID, CHILDCHILD_CONNECTION_ID)
  }

  override val parentProperty: String
    get() {
      readField("parentProperty")
      return dataSource.parentProperty
    }
  override val children: List<XChildEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(CHILDREN_CONNECTION_ID, this) as? Sequence<XChildEntity>)?.toList()
            ?: error("Children list children not found for XParentEntity")
  override val optionalChildren: List<XChildWithOptionalParentEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(OPTIONALCHILDREN_CONNECTION_ID,
                                                      this) as? Sequence<XChildWithOptionalParentEntity>)?.toList()
            ?: error("Children list optionalChildren not found for XParentEntity")
  override val childChild: List<XChildChildEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(CHILDCHILD_CONNECTION_ID, this) as? Sequence<XChildChildEntity>)?.toList()
            ?: error("Children list childChild not found for XParentEntity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: XParentEntityData?) : ModifiableWorkspaceEntityBase<XParentEntity, XParentEntityData>(result),
                                                       XParentEntityBuilder {
    internal constructor() : this(XParentEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isParentPropertyInitialized()) {
        error("Field XParentEntity#parentProperty should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as XParentEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.parentProperty != dataSource.parentProperty) this.parentProperty = dataSource.parentProperty
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var parentProperty: String
      get() = getEntityData().parentProperty
      set(value) {
        checkModificationAllowed()
        getEntityData(true).parentProperty = value
        changedProperty.add("parentProperty")
      }
    override var children: List<XChildEntityBuilder>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(CHILDREN_CONNECTION_ID) as List<XChildEntityBuilder>
      set(value) {
        changeChildren(value, CHILDREN_CONNECTION_ID)
        changedProperty.add("children")
      }
    override var optionalChildren: List<XChildWithOptionalParentEntityBuilder>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(OPTIONALCHILDREN_CONNECTION_ID) as List<XChildWithOptionalParentEntityBuilder>
      set(value) {
        changeChildren(value, OPTIONALCHILDREN_CONNECTION_ID)
        changedProperty.add("optionalChildren")
      }
    override var childChild: List<XChildChildEntityBuilder>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(CHILDCHILD_CONNECTION_ID) as List<XChildChildEntityBuilder>
      set(value) {
        changeChildren(value, CHILDCHILD_CONNECTION_ID)
        changedProperty.add("childChild")
      }

    override fun getEntityClass(): Class<XParentEntity> = XParentEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class XParentEntityData : WorkspaceEntityData<XParentEntity>() {
  lateinit var parentProperty: String
  internal fun isParentPropertyInitialized(): Boolean = ::parentProperty.isInitialized
  override fun newInstance(): XParentEntity = XParentEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<XParentEntity, *> = XParentEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.XParentEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return XParentEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return XParentEntity(parentProperty, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as XParentEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.parentProperty != other.parentProperty) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as XParentEntityData
    if (this.parentProperty != other.parentProperty) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + parentProperty.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + parentProperty.hashCode()
    return result
  }
}
