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
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.AnotherOneToOneRefEntity
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.AnotherOneToOneRefEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToOneRefEntity
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToOneRefEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class OneToOneRefEntityImpl(private val dataSource: OneToOneRefEntityData) : OneToOneRefEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val ANOTHERENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(OneToOneRefEntity::class.java,
                                                                                 AnotherOneToOneRefEntity::class.java,
                                                                                 ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                 false)
    private val connections = listOf<ConnectionId>(ANOTHERENTITY_CONNECTION_ID)
  }

  override val version: Int
    get() {
      readField("version")
      return dataSource.version
    }
  override val text: String
    get() {
      readField("text")
      return dataSource.text
    }
  override val anotherEntity: List<AnotherOneToOneRefEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(ANOTHERENTITY_CONNECTION_ID, this) as? Sequence<AnotherOneToOneRefEntity>)?.toList()
            ?: error("Children list anotherEntity not found for OneToOneRefEntity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: OneToOneRefEntityData?) : ModifiableWorkspaceEntityBase<OneToOneRefEntity, OneToOneRefEntityData>(result),
                                                           OneToOneRefEntityBuilder {
    internal constructor() : this(OneToOneRefEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isTextInitialized()) {
        error("Field OneToOneRefEntity#text should be initialized")
      }
// Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.instrumentation.getManyChildrenBuilders(ANOTHERENTITY_CONNECTION_ID, this) == null) {
          error("Field OneToOneRefEntity#anotherEntity should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, ANOTHERENTITY_CONNECTION_ID)] == null) {
          error("Field OneToOneRefEntity#anotherEntity should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as OneToOneRefEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.version != dataSource.version) this.version = dataSource.version
      if (this.text != dataSource.text) this.text = dataSource.text
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var version: Int
      get() = getEntityData().version
      set(value) {
        checkModificationAllowed()
        getEntityData(true).version = value
        changedProperty.add("version")
      }
    override var text: String
      get() = getEntityData().text
      set(value) {
        checkModificationAllowed()
        getEntityData(true).text = value
        changedProperty.add("text")
      }
    override var anotherEntity: List<AnotherOneToOneRefEntityBuilder>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(ANOTHERENTITY_CONNECTION_ID) as List<AnotherOneToOneRefEntityBuilder>
      set(value) {
        changeChildren(value, ANOTHERENTITY_CONNECTION_ID)
        changedProperty.add("anotherEntity")
      }

    override fun getEntityClass(): Class<OneToOneRefEntity> = OneToOneRefEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class OneToOneRefEntityData : WorkspaceEntityData<OneToOneRefEntity>() {
  var version: Int = 0
  lateinit var text: String
  internal fun isTextInitialized(): Boolean = ::text.isInitialized
  override fun newInstance(): OneToOneRefEntity = OneToOneRefEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<OneToOneRefEntity, *> = OneToOneRefEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToOneRefEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return OneToOneRefEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return OneToOneRefEntity(version, text, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as OneToOneRefEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.version != other.version) return false
    if (this.text != other.text) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as OneToOneRefEntityData
    if (this.version != other.version) return false
    if (this.text != other.text) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + text.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + text.hashCode()
    return result
  }
}
