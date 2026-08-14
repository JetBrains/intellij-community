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
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.DataClassX
import com.intellij.platform.workspace.storage.testEntities.entities.XChildChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XChildChildEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.XChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XChildEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.XParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XParentEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class XChildEntityImpl(private val dataSource: XChildEntityData) : XChildEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId =
      ConnectionId.create(XParentEntity::class.java, XChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val CHILDCHILD_CONNECTION_ID: ConnectionId =
      ConnectionId.create(XChildEntity::class.java, XChildChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    private val connections = listOf<ConnectionId>(PARENTENTITY_CONNECTION_ID, CHILDCHILD_CONNECTION_ID)
  }

  override val childProperty: String
    get() {
      readField("childProperty")
      return dataSource.childProperty
    }
  override val dataClass: DataClassX?
    get() {
      readField("dataClass")
      return dataSource.dataClass
    }
  override val parentEntity: XParentEntity
    get() = snapshot.instrumentation.getParent(PARENTENTITY_CONNECTION_ID, this) as? XParentEntity
            ?: error("Parent parentEntity not found for XChildEntity")
  override val childChild: List<XChildChildEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(CHILDCHILD_CONNECTION_ID, this) as? Sequence<XChildChildEntity>)?.toList()
            ?: error("Children list childChild not found for XChildEntity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: XChildEntityData?) : ModifiableWorkspaceEntityBase<XChildEntity, XChildEntityData>(result),
                                                      XChildEntityBuilder {
    internal constructor() : this(XChildEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isChildPropertyInitialized()) {
        error("Field XChildEntity#childProperty should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(PARENTENTITY_CONNECTION_ID, this) == null) {
          error("Field XChildEntity#parentEntity should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] == null) {
          error("Field XChildEntity#parentEntity should be initialized")
        }
      }
// Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.instrumentation.getManyChildrenBuilders(CHILDCHILD_CONNECTION_ID, this) == null) {
          error("Field XChildEntity#childChild should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDCHILD_CONNECTION_ID)] == null) {
          error("Field XChildEntity#childChild should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as XChildEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.childProperty != dataSource.childProperty) this.childProperty = dataSource.childProperty
      if (this.dataClass != dataSource?.dataClass) this.dataClass = dataSource.dataClass
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
    override var dataClass: DataClassX?
      get() = getEntityData().dataClass
      set(value) {
        checkModificationAllowed()
        getEntityData(true).dataClass = value
        changedProperty.add("dataClass")
      }
    override var parentEntity: XParentEntityBuilder
      get() = getParent(PARENTENTITY_CONNECTION_ID) as? XParentEntityBuilder ?: error("parentEntity is null for XChildEntity")
      set(value) {
        changeParentOfMany(value, PARENTENTITY_CONNECTION_ID)
        changedProperty.add("parentEntity")
      }
    override var childChild: List<XChildChildEntityBuilder>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(CHILDCHILD_CONNECTION_ID) as List<XChildChildEntityBuilder>
      set(value) {
        changeChildren(value, CHILDCHILD_CONNECTION_ID)
        changedProperty.add("childChild")
      }

    override fun getEntityClass(): Class<XChildEntity> = XChildEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class XChildEntityData : WorkspaceEntityData<XChildEntity>() {
  lateinit var childProperty: String
  var dataClass: DataClassX? = null
  internal fun isChildPropertyInitialized(): Boolean = ::childProperty.isInitialized
  override fun newInstance(): XChildEntity = XChildEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<XChildEntity, *> = XChildEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.XChildEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return XChildEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return XChildEntity(childProperty, entitySource) {
      this.dataClass = this@XChildEntityData.dataClass
      parents.filterIsInstance<XParentEntityBuilder>().singleOrNull()?.let { this.parentEntity = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(XParentEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as XChildEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.childProperty != other.childProperty) return false
    if (this.dataClass != other.dataClass) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as XChildEntityData
    if (this.childProperty != other.childProperty) return false
    if (this.dataClass != other.dataClass) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + childProperty.hashCode()
    result = 31 * result + dataClass.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + childProperty.hashCode()
    result = 31 * result + dataClass.hashCode()
    return result
  }
}
