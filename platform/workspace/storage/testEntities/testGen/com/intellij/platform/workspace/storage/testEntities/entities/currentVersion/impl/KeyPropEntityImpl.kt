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
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.KeyPropEntity
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.KeyPropEntityBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class KeyPropEntityImpl(private val dataSource: KeyPropEntityData) : KeyPropEntity, WorkspaceEntityBase(dataSource) {

  override val someInt: Int
    get() {
      readField("someInt")
      return dataSource.someInt
    }
  override val text: String
    get() {
      readField("text")
      return dataSource.text
    }
  override val url: VirtualFileUrl
    get() {
      readField("url")
      return dataSource.url
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return emptyList()
  }

  internal class Builder(result: KeyPropEntityData?) : ModifiableWorkspaceEntityBase<KeyPropEntity, KeyPropEntityData>(result),
                                                       KeyPropEntityBuilder {
    internal constructor() : this(KeyPropEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isTextInitialized()) {
        error("Field KeyPropEntity#text should be initialized")
      }
      if (!getEntityData().isUrlInitialized()) {
        error("Field KeyPropEntity#url should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return emptyList()
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as KeyPropEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.someInt != dataSource.someInt) this.someInt = dataSource.someInt
      if (this.text != dataSource.text) this.text = dataSource.text
      if (this.url != dataSource.url) this.url = dataSource.url
      updateChildToParentReferences(parents)
    }

    override fun index() {
      index(this, "url", this.url)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var someInt: Int
      get() = getEntityData().someInt
      set(value) {
        checkModificationAllowed()
        getEntityData(true).someInt = value
        changedProperty.add("someInt")
      }
    override var text: String
      get() = getEntityData().text
      set(value) {
        checkModificationAllowed()
        getEntityData(true).text = value
        changedProperty.add("text")
      }
    override var url: VirtualFileUrl
      get() = getEntityData().url
      set(value) {
        checkModificationAllowed()
        getEntityData(true).url = value
        changedProperty.add("url")
        val _diff = diff
        if (_diff != null) index(this, "url", value)
      }

    override fun getEntityClass(): Class<KeyPropEntity> = KeyPropEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class KeyPropEntityData : WorkspaceEntityData<KeyPropEntity>() {
  var someInt: Int = 0
  lateinit var text: String
  lateinit var url: VirtualFileUrl
  internal fun isTextInitialized(): Boolean = ::text.isInitialized
  internal fun isUrlInitialized(): Boolean = ::url.isInitialized
  override fun newInstance(): KeyPropEntity = KeyPropEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<KeyPropEntity, *> = KeyPropEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.KeyPropEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return KeyPropEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return KeyPropEntity(someInt, text, url, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as KeyPropEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.someInt != other.someInt) return false
    if (this.text != other.text) return false
    if (this.url != other.url) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as KeyPropEntityData
    if (this.someInt != other.someInt) return false
    if (this.text != other.text) return false
    if (this.url != other.url) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + someInt.hashCode()
    result = 31 * result + text.hashCode()
    result = 31 * result + url.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + someInt.hashCode()
    result = 31 * result + text.hashCode()
    result = 31 * result + url.hashCode()
    return result
  }
}
