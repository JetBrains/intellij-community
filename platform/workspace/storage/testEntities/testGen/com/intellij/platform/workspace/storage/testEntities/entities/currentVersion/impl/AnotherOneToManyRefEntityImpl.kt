// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl

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
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.AnotherOneToManyRefEntity
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.AnotherOneToManyRefEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToManyRefDataClass
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToManyRefEntity
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToManyRefEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class AnotherOneToManyRefEntityImpl(private val dataSource: AnotherOneToManyRefEntityData) : AnotherOneToManyRefEntity,
                                                                                                      WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(OneToManyRefEntity::class.java,
                                                                                AnotherOneToManyRefEntity::class.java,
                                                                                ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                false)
    private val connections = listOf<ConnectionId>(PARENTENTITY_CONNECTION_ID)
  }

  override val parentEntity: OneToManyRefEntity
    get() = snapshot.instrumentation.getParent(PARENTENTITY_CONNECTION_ID, this) as? OneToManyRefEntity
            ?: error("Parent parentEntity not found for AnotherOneToManyRefEntity")
  override val version: Int
    get() {
      readField("version")
      return dataSource.version
    }
  override val someData: OneToManyRefDataClass
    get() {
      readField("someData")
      return dataSource.someData
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: AnotherOneToManyRefEntityData?) :
    ModifiableWorkspaceEntityBase<AnotherOneToManyRefEntity, AnotherOneToManyRefEntityData>(result), AnotherOneToManyRefEntityBuilder {
    internal constructor() : this(AnotherOneToManyRefEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(PARENTENTITY_CONNECTION_ID, this) == null) {
          error("Field AnotherOneToManyRefEntity#parentEntity should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] == null) {
          error("Field AnotherOneToManyRefEntity#parentEntity should be initialized")
        }
      }
      if (!getEntityData().isSomeDataInitialized()) {
        error("Field AnotherOneToManyRefEntity#someData should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as AnotherOneToManyRefEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.version != dataSource.version) this.version = dataSource.version
      if (this.someData != dataSource.someData) this.someData = dataSource.someData
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var parentEntity: OneToManyRefEntityBuilder
      get() = getParent(PARENTENTITY_CONNECTION_ID) as? OneToManyRefEntityBuilder
              ?: error("parentEntity is null for AnotherOneToManyRefEntity")
      set(value) {
        changeParent(value, PARENTENTITY_CONNECTION_ID)
        changedProperty.add("parentEntity")
      }
    override var version: Int
      get() = getEntityData().version
      set(value) {
        checkModificationAllowed()
        getEntityData(true).version = value
        changedProperty.add("version")
      }
    override var someData: OneToManyRefDataClass
      get() = getEntityData().someData
      set(value) {
        checkModificationAllowed()
        getEntityData(true).someData = value
        changedProperty.add("someData")
      }

    override fun getEntityClass(): Class<AnotherOneToManyRefEntity> = AnotherOneToManyRefEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class AnotherOneToManyRefEntityData : WorkspaceEntityData<AnotherOneToManyRefEntity>() {
  var version: Int = 0
  lateinit var someData: OneToManyRefDataClass
  internal fun isSomeDataInitialized(): Boolean = ::someData.isInitialized
  override fun newInstance(): AnotherOneToManyRefEntity = AnotherOneToManyRefEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<AnotherOneToManyRefEntity, *> =
    AnotherOneToManyRefEntityImpl.Builder(null)

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.AnotherOneToManyRefEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return AnotherOneToManyRefEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return AnotherOneToManyRefEntity(version, someData, entitySource) {
      parents.filterIsInstance<OneToManyRefEntityBuilder>().singleOrNull()?.let { this.parentEntity = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(OneToManyRefEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as AnotherOneToManyRefEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.version != other.version) return false
    if (this.someData != other.someData) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as AnotherOneToManyRefEntityData
    if (this.version != other.version) return false
    if (this.someData != other.someData) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + someData.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + someData.hashCode()
    return result
  }
}
