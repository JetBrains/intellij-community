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
import com.intellij.platform.workspace.storage.testEntities.entities.KeyChild
import com.intellij.platform.workspace.storage.testEntities.entities.KeyChildBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.KeyParent
import com.intellij.platform.workspace.storage.testEntities.entities.KeyParentBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class KeyParentImpl(private val dataSource: KeyParentData) : KeyParent, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val CHILDREN_CONNECTION_ID: ConnectionId =
      ConnectionId.create(KeyParent::class.java, KeyChild::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    private val connections = listOf<ConnectionId>(CHILDREN_CONNECTION_ID)
  }

  override val keyField: String
    get() {
      readField("keyField")
      return dataSource.keyField
    }
  override val notKeyField: String
    get() {
      readField("notKeyField")
      return dataSource.notKeyField
    }
  override val children: List<KeyChild>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(CHILDREN_CONNECTION_ID, this) as? Sequence<KeyChild>)?.toList()
            ?: error("Children list children not found for KeyParent")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: KeyParentData?) : ModifiableWorkspaceEntityBase<KeyParent, KeyParentData>(result), KeyParentBuilder {
    internal constructor() : this(KeyParentData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isKeyFieldInitialized()) {
        error("Field KeyParent#keyField should be initialized")
      }
      if (!getEntityData().isNotKeyFieldInitialized()) {
        error("Field KeyParent#notKeyField should be initialized")
      }
// Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.instrumentation.getManyChildrenBuilders(CHILDREN_CONNECTION_ID, this) == null) {
          error("Field KeyParent#children should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
          error("Field KeyParent#children should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as KeyParent
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.keyField != dataSource.keyField) this.keyField = dataSource.keyField
      if (this.notKeyField != dataSource.notKeyField) this.notKeyField = dataSource.notKeyField
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var keyField: String
      get() = getEntityData().keyField
      set(value) {
        checkModificationAllowed()
        getEntityData(true).keyField = value
        changedProperty.add("keyField")
      }
    override var notKeyField: String
      get() = getEntityData().notKeyField
      set(value) {
        checkModificationAllowed()
        getEntityData(true).notKeyField = value
        changedProperty.add("notKeyField")
      }
    override var children: List<KeyChildBuilder>
      @Suppress("UNCHECKED_CAST")
      get() = getChildren(CHILDREN_CONNECTION_ID) as List<KeyChildBuilder>
      set(value) {
        changeChildren(value, CHILDREN_CONNECTION_ID)
        changedProperty.add("children")
      }

    override fun getEntityClass(): Class<KeyParent> = KeyParent::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class KeyParentData : WorkspaceEntityData<KeyParent>() {
  lateinit var keyField: String
  lateinit var notKeyField: String
  internal fun isKeyFieldInitialized(): Boolean = ::keyField.isInitialized
  internal fun isNotKeyFieldInitialized(): Boolean = ::notKeyField.isInitialized
  override fun newInstance(): KeyParent = KeyParentImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<KeyParent, *> = KeyParentImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.KeyParent") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return KeyParent::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return KeyParent(keyField, notKeyField, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as KeyParentData
    if (this.entitySource != other.entitySource) return false
    if (this.keyField != other.keyField) return false
    if (this.notKeyField != other.notKeyField) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as KeyParentData
    if (this.keyField != other.keyField) return false
    if (this.notKeyField != other.notKeyField) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + keyField.hashCode()
    result = 31 * result + notKeyField.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + keyField.hashCode()
    result = 31 * result + notKeyField.hashCode()
    return result
  }

  override fun equalsByKey(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as KeyParentData
    if (this.keyField != other.keyField) return false
    return true
  }

  override fun hashCodeByKey(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + keyField.hashCode()
    return result
  }
}
