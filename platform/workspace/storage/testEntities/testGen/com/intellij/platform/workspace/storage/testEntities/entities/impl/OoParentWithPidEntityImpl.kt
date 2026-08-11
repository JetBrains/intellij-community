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
import com.intellij.platform.workspace.storage.testEntities.entities.OoChildAlsoWithPidEntity
import com.intellij.platform.workspace.storage.testEntities.entities.OoChildAlsoWithPidEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.OoChildForParentWithPidEntity
import com.intellij.platform.workspace.storage.testEntities.entities.OoChildForParentWithPidEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.OoParentEntityId
import com.intellij.platform.workspace.storage.testEntities.entities.OoParentWithPidEntity
import com.intellij.platform.workspace.storage.testEntities.entities.OoParentWithPidEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class OoParentWithPidEntityImpl(private val dataSource: OoParentWithPidEntityData) : OoParentWithPidEntity,
                                                                                              WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val CHILDONE_CONNECTION_ID: ConnectionId = ConnectionId.create(OoParentWithPidEntity::class.java,
                                                                            OoChildForParentWithPidEntity::class.java,
                                                                            ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                            false)
    internal val CHILDTHREE_CONNECTION_ID: ConnectionId = ConnectionId.create(OoParentWithPidEntity::class.java,
                                                                              OoChildAlsoWithPidEntity::class.java,
                                                                              ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                              false)
    private val connections = listOf<ConnectionId>(CHILDONE_CONNECTION_ID, CHILDTHREE_CONNECTION_ID)
  }

  override val symbolicId: OoParentEntityId = super.symbolicId

  override val parentProperty: String
    get() {
      readField("parentProperty")
      return dataSource.parentProperty
    }
  override val childOne: OoChildForParentWithPidEntity?
    get() = snapshot.instrumentation.getOneChild(CHILDONE_CONNECTION_ID, this) as? OoChildForParentWithPidEntity
  override val childThree: OoChildAlsoWithPidEntity?
    get() = snapshot.instrumentation.getOneChild(CHILDTHREE_CONNECTION_ID, this) as? OoChildAlsoWithPidEntity
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: OoParentWithPidEntityData?) :
    ModifiableWorkspaceEntityBase<OoParentWithPidEntity, OoParentWithPidEntityData>(result), OoParentWithPidEntityBuilder {
    internal constructor() : this(OoParentWithPidEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isParentPropertyInitialized()) {
        error("Field OoParentWithPidEntity#parentProperty should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as OoParentWithPidEntity
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
    override var childOne: OoChildForParentWithPidEntityBuilder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getOneChildBuilder(CHILDONE_CONNECTION_ID,
                                                                             this) as? OoChildForParentWithPidEntityBuilder)
          ?: (this.entityLinks[EntityLink(true, CHILDONE_CONNECTION_ID)] as? OoChildForParentWithPidEntityBuilder)
        }
        else {
          (this.entityLinks[EntityLink(true, CHILDONE_CONNECTION_ID)] as? OoChildForParentWithPidEntityBuilder)
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          value.entityLinks[EntityLink(false, CHILDONE_CONNECTION_ID)] = this
          @Suppress("UNCHECKED_CAST")
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.instrumentation.replaceChildren(CHILDONE_CONNECTION_ID, this, listOfNotNull(value))
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, CHILDONE_CONNECTION_ID)] = this
          }
          this.entityLinks[EntityLink(true, CHILDONE_CONNECTION_ID)] = value
        }
        changedProperty.add("childOne")
      }
    override var childThree: OoChildAlsoWithPidEntityBuilder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getOneChildBuilder(CHILDTHREE_CONNECTION_ID,
                                                                             this) as? OoChildAlsoWithPidEntityBuilder)
          ?: (this.entityLinks[EntityLink(true, CHILDTHREE_CONNECTION_ID)] as? OoChildAlsoWithPidEntityBuilder)
        }
        else {
          (this.entityLinks[EntityLink(true, CHILDTHREE_CONNECTION_ID)] as? OoChildAlsoWithPidEntityBuilder)
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          value.entityLinks[EntityLink(false, CHILDTHREE_CONNECTION_ID)] = this
          @Suppress("UNCHECKED_CAST")
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.instrumentation.replaceChildren(CHILDTHREE_CONNECTION_ID, this, listOfNotNull(value))
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, CHILDTHREE_CONNECTION_ID)] = this
          }
          this.entityLinks[EntityLink(true, CHILDTHREE_CONNECTION_ID)] = value
        }
        changedProperty.add("childThree")
      }

    override fun getEntityClass(): Class<OoParentWithPidEntity> = OoParentWithPidEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class OoParentWithPidEntityData : WorkspaceEntityData<OoParentWithPidEntity>() {
  lateinit var parentProperty: String
  internal fun isParentPropertyInitialized(): Boolean = ::parentProperty.isInitialized
  override fun newInstance(): OoParentWithPidEntity = OoParentWithPidEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<OoParentWithPidEntity, *> = OoParentWithPidEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.OoParentWithPidEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return OoParentWithPidEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return OoParentWithPidEntity(parentProperty, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as OoParentWithPidEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.parentProperty != other.parentProperty) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as OoParentWithPidEntityData
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
