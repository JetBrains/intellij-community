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
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.XChildChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XChildChildEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.XChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XChildEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.XParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.XParentEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class XChildChildEntityImpl(private val dataSource: XChildChildEntityData) : XChildChildEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val PARENT1_CONNECTION_ID: ConnectionId =
      ConnectionId.create(XParentEntity::class.java, XChildChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val PARENT2_CONNECTION_ID: ConnectionId =
      ConnectionId.create(XChildEntity::class.java, XChildChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    private val connections = listOf<ConnectionId>(PARENT1_CONNECTION_ID, PARENT2_CONNECTION_ID)
  }

  override val parent1: XParentEntity
    get() = snapshot.instrumentation.getParent(PARENT1_CONNECTION_ID, this) as? XParentEntity
            ?: error("Parent parent1 not found for XChildChildEntity")
  override val parent2: XChildEntity
    get() = snapshot.instrumentation.getParent(PARENT2_CONNECTION_ID, this) as? XChildEntity
            ?: error("Parent parent2 not found for XChildChildEntity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: XChildChildEntityData?) : ModifiableWorkspaceEntityBase<XChildChildEntity, XChildChildEntityData>(result),
                                                           XChildChildEntityBuilder {
    internal constructor() : this(XChildChildEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(PARENT1_CONNECTION_ID, this) == null) {
          error("Field XChildChildEntity#parent1 should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENT1_CONNECTION_ID)] == null) {
          error("Field XChildChildEntity#parent1 should be initialized")
        }
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(PARENT2_CONNECTION_ID, this) == null) {
          error("Field XChildChildEntity#parent2 should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENT2_CONNECTION_ID)] == null) {
          error("Field XChildChildEntity#parent2 should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as XChildChildEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var parent1: XParentEntityBuilder
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(PARENT1_CONNECTION_ID, this) as? XParentEntityBuilder)
          ?: (this.entityLinks[EntityLink(false, PARENT1_CONNECTION_ID)] as? XParentEntityBuilder)
          ?: error("parent1 is null for XChildChildEntity")
        }
        else {
          (this.entityLinks[EntityLink(false, PARENT1_CONNECTION_ID)] as? XParentEntityBuilder)
          ?: error("parent1 is null for XChildChildEntity")
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
// Setting backref of the list
          @Suppress("UNCHECKED_CAST")
          val data = (value.entityLinks[EntityLink(true, PARENT1_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
          value.entityLinks[EntityLink(true, PARENT1_CONNECTION_ID)] = data
          @Suppress("UNCHECKED_CAST")
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.instrumentation.addChild(PARENT1_CONNECTION_ID, value, this)
        }
        else {
// Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val data = (value.entityLinks[EntityLink(true, PARENT1_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENT1_CONNECTION_ID)] = data
          }
          this.entityLinks[EntityLink(false, PARENT1_CONNECTION_ID)] = value
        }
        changedProperty.add("parent1")
      }
    override var parent2: XChildEntityBuilder
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(PARENT2_CONNECTION_ID, this) as? XChildEntityBuilder)
          ?: (this.entityLinks[EntityLink(false, PARENT2_CONNECTION_ID)] as? XChildEntityBuilder)
          ?: error("parent2 is null for XChildChildEntity")
        }
        else {
          (this.entityLinks[EntityLink(false, PARENT2_CONNECTION_ID)] as? XChildEntityBuilder)
          ?: error("parent2 is null for XChildChildEntity")
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
// Setting backref of the list
          @Suppress("UNCHECKED_CAST")
          val data = (value.entityLinks[EntityLink(true, PARENT2_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
          value.entityLinks[EntityLink(true, PARENT2_CONNECTION_ID)] = data
          @Suppress("UNCHECKED_CAST")
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.instrumentation.addChild(PARENT2_CONNECTION_ID, value, this)
        }
        else {
// Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val data = (value.entityLinks[EntityLink(true, PARENT2_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENT2_CONNECTION_ID)] = data
          }
          this.entityLinks[EntityLink(false, PARENT2_CONNECTION_ID)] = value
        }
        changedProperty.add("parent2")
      }

    override fun getEntityClass(): Class<XChildChildEntity> = XChildChildEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class XChildChildEntityData : WorkspaceEntityData<XChildChildEntity>() {
  override fun newInstance(): XChildChildEntity = XChildChildEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<XChildChildEntity, *> = XChildChildEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.XChildChildEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return XChildChildEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return XChildChildEntity(entitySource) {
      parents.filterIsInstance<XParentEntityBuilder>().singleOrNull()?.let { this.parent1 = it }
      parents.filterIsInstance<XChildEntityBuilder>().singleOrNull()?.let { this.parent2 = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(XParentEntity::class.java)
    res.add(XChildEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as XChildChildEntityData
    if (this.entitySource != other.entitySource) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as XChildChildEntityData
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    return result
  }
}
