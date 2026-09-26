// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl

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
import com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.AnotherOneToManyRefEntity
import com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.AnotherOneToManyRefEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToManyRefDataClass
import com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToManyRefEntity
import com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToManyRefEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class OneToManyRefEntityImpl(private val dataSource: OneToManyRefEntityData) : OneToManyRefEntity,
                                                                                        WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val ANOTHERENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(OneToManyRefEntity::class.java,
                                                                                 AnotherOneToManyRefEntity::class.java,
                                                                                 ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                 false)
    private val connections = listOf<ConnectionId>(ANOTHERENTITY_CONNECTION_ID)
  }

  override val someData: OneToManyRefDataClass
    get() {
      readField("someData")
      return dataSource.someData
    }
  override val anotherEntity: List<AnotherOneToManyRefEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(ANOTHERENTITY_CONNECTION_ID, this) as? Sequence<AnotherOneToManyRefEntity>)?.toList()
            ?: error("Children list anotherEntity not found for OneToManyRefEntity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: OneToManyRefEntityData?) :
    ModifiableWorkspaceEntityBase<OneToManyRefEntity, OneToManyRefEntityData>(result), OneToManyRefEntityBuilder {
    internal constructor() : this(OneToManyRefEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isSomeDataInitialized()) {
        error("Field OneToManyRefEntity#someData should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as OneToManyRefEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
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
    override var someData: OneToManyRefDataClass
      get() = getEntityData().someData
      set(value) {
        checkModificationAllowed()
        getEntityData(true).someData = value
        changedProperty.add("someData")
      }
    override var anotherEntity: List<AnotherOneToManyRefEntityBuilder>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(ANOTHERENTITY_CONNECTION_ID) as List<AnotherOneToManyRefEntityBuilder>
      set(value) {
        changeChildren(value, ANOTHERENTITY_CONNECTION_ID)
        changedProperty.add("anotherEntity")
      }

    override fun getEntityClass(): Class<OneToManyRefEntity> = OneToManyRefEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class OneToManyRefEntityData : WorkspaceEntityData<OneToManyRefEntity>() {
  lateinit var someData: OneToManyRefDataClass
  internal fun isSomeDataInitialized(): Boolean = ::someData.isInitialized
  override fun newInstance(): OneToManyRefEntity = OneToManyRefEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<OneToManyRefEntity, *> = OneToManyRefEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToManyRefEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return OneToManyRefEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return OneToManyRefEntity(someData, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as OneToManyRefEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.someData != other.someData) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as OneToManyRefEntityData
    if (this.someData != other.someData) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + someData.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + someData.hashCode()
    return result
  }
}
