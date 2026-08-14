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
import com.intellij.platform.workspace.storage.testEntities.entities.PCDId2
import com.intellij.platform.workspace.storage.testEntities.entities.PcdChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.PcdChildEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.PcdParent2Entity
import com.intellij.platform.workspace.storage.testEntities.entities.PcdParent2EntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class PcdParent2EntityImpl(private val dataSource: PcdParent2EntityData) : PcdParent2Entity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val CHILDREN_CONNECTION_ID: ConnectionId =
      ConnectionId.create(PcdParent2Entity::class.java, PcdChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    private val connections = listOf<ConnectionId>(CHILDREN_CONNECTION_ID)
  }

  override val symbolicId: PCDId2 = super.symbolicId

  override val name: String
    get() {
      readField("name")
      return dataSource.name
    }
  override val version: Int
    get() {
      readField("version")
      return dataSource.version
    }
  override val children: List<PcdChildEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(CHILDREN_CONNECTION_ID, this) as? Sequence<PcdChildEntity>)?.toList()
            ?: error("Children list children not found for PcdParent2Entity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: PcdParent2EntityData?) : ModifiableWorkspaceEntityBase<PcdParent2Entity, PcdParent2EntityData>(result),
                                                          PcdParent2EntityBuilder {
    internal constructor() : this(PcdParent2EntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isNameInitialized()) {
        error("Field PcdParent2Entity#name should be initialized")
      }
// Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.instrumentation.getManyChildrenBuilders(CHILDREN_CONNECTION_ID, this) == null) {
          error("Field PcdParent2Entity#children should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
          error("Field PcdParent2Entity#children should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as PcdParent2Entity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.version != dataSource.version) this.version = dataSource.version
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
    override var version: Int
      get() = getEntityData().version
      set(value) {
        checkModificationAllowed()
        getEntityData(true).version = value
        changedProperty.add("version")
      }
    override var children: List<PcdChildEntityBuilder>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(CHILDREN_CONNECTION_ID) as List<PcdChildEntityBuilder>
      set(value) {
        changeChildren(value, CHILDREN_CONNECTION_ID)
        changedProperty.add("children")
      }

    override fun getEntityClass(): Class<PcdParent2Entity> = PcdParent2Entity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class PcdParent2EntityData : WorkspaceEntityData<PcdParent2Entity>() {
  lateinit var name: String
  var version: Int = 0
  internal fun isNameInitialized(): Boolean = ::name.isInitialized
  override fun newInstance(): PcdParent2Entity = PcdParent2EntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<PcdParent2Entity, *> = PcdParent2EntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.PcdParent2Entity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return PcdParent2Entity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return PcdParent2Entity(name, version, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as PcdParent2EntityData
    if (this.entitySource != other.entitySource) return false
    if (this.name != other.name) return false
    if (this.version != other.version) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as PcdParent2EntityData
    if (this.name != other.name) return false
    if (this.version != other.version) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + version.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + version.hashCode()
    return result
  }
}
