// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.devkit.workspaceModel.jsonDump.impl

import com.intellij.devkit.workspaceModel.jsonDump.BaseTestEntity
import com.intellij.devkit.workspaceModel.jsonDump.BaseTestEntityBuilder
import com.intellij.devkit.workspaceModel.jsonDump.ExtensionChildEntity
import com.intellij.devkit.workspaceModel.jsonDump.ExtensionChildEntityBuilder
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ExtensionChildEntityImpl(private val dataSource: ExtensionChildEntityData) : ExtensionChildEntity,
                                                                                            WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val PARENT_CONNECTION_ID: ConnectionId =
      ConnectionId.create(BaseTestEntity::class.java, ExtensionChildEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)
    private val connections = listOf<ConnectionId>(PARENT_CONNECTION_ID)

  }

  override val extensionChildName: String
    get() {
      readField("extensionChildName")
      return dataSource.extensionChildName
    }
  override val parent: BaseTestEntity
    get() = snapshot.instrumentation.getParent(PARENT_CONNECTION_ID, this) as? BaseTestEntity
            ?: error("Parent parent not found for ExtensionChildEntity")
  override val listOfUrls: List<VirtualFileUrl>
    get() {
      readField("listOfUrls")
      return dataSource.listOfUrls
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: ExtensionChildEntityData?) :
    ModifiableWorkspaceEntityBase<ExtensionChildEntity, ExtensionChildEntityData>(result), ExtensionChildEntityBuilder {
    internal constructor() : this(ExtensionChildEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ExtensionChildEntity is already created in a different builder")
        }
      }
      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
// After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
// Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null
      index(this, "listOfUrls", this.listOfUrls)
// Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isExtensionChildNameInitialized()) {
        error("Field ExtensionChildEntity#extensionChildName should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(PARENT_CONNECTION_ID, this) == null) {
          error("Field ExtensionChildEntity#parent should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] == null) {
          error("Field ExtensionChildEntity#parent should be initialized")
        }
      }
      if (!getEntityData().isListOfUrlsInitialized()) {
        error("Field ExtensionChildEntity#listOfUrls should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_listOfUrls = getEntityData().listOfUrls
      if (collection_listOfUrls is MutableWorkspaceList<*>) {
        collection_listOfUrls.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ExtensionChildEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.extensionChildName != dataSource.extensionChildName) this.extensionChildName = dataSource.extensionChildName
      if (this.listOfUrls != dataSource.listOfUrls) this.listOfUrls = dataSource.listOfUrls.toMutableList()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }
    override var extensionChildName: String
      get() = getEntityData().extensionChildName
      set(value) {
        checkModificationAllowed()
        getEntityData(true).extensionChildName = value
        changedProperty.add("extensionChildName")
      }
    override var parent: BaseTestEntityBuilder
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(PARENT_CONNECTION_ID, this) as? BaseTestEntityBuilder)
          ?: (this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] as? BaseTestEntityBuilder)
          ?: error("parent is null for ExtensionChildEntity")
        }
        else {
          (this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] as? BaseTestEntityBuilder)
          ?: error("parent is null for ExtensionChildEntity")
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
// Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, PARENT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENT_CONNECTION_ID)] = data
          }
// else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.instrumentation.addChild(PARENT_CONNECTION_ID, value, this)
        }
        else {
// Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, PARENT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENT_CONNECTION_ID)] = data
          }
// else you're attaching a new entity to an existing entity that is not modifiable
          this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] = value
        }
        changedProperty.add("parent")
      }

    private val listOfUrlsUpdater: (value: List<VirtualFileUrl>) -> Unit = { value ->
      val _diff = diff
      if (_diff != null) index(this, "listOfUrls", value)
      changedProperty.add("listOfUrls")
    }
    override var listOfUrls: MutableList<VirtualFileUrl>
      get() {
        val collection_listOfUrls = getEntityData().listOfUrls
        if (collection_listOfUrls !is MutableWorkspaceList) return collection_listOfUrls
        if (diff == null || modifiable.get()) {
          collection_listOfUrls.setModificationUpdateAction(listOfUrlsUpdater)
        }
        else {
          collection_listOfUrls.cleanModificationUpdateAction()
        }
        return collection_listOfUrls
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).listOfUrls = value
        listOfUrlsUpdater.invoke(value)
      }

    override fun getEntityClass(): Class<ExtensionChildEntity> = ExtensionChildEntity::class.java
  }

}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ExtensionChildEntityData : WorkspaceEntityData<ExtensionChildEntity>() {
  lateinit var extensionChildName: String
  lateinit var listOfUrls: MutableList<VirtualFileUrl>

  internal fun isExtensionChildNameInitialized(): Boolean = ::extensionChildName.isInitialized
  internal fun isListOfUrlsInitialized(): Boolean = ::listOfUrls.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<ExtensionChildEntity> {
    val modifiable = ExtensionChildEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorageInstrumentation): ExtensionChildEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = ExtensionChildEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.devkit.workspaceModel.jsonDump.ExtensionChildEntity") as EntityMetadata
  }

  override fun clone(): ExtensionChildEntityData {
    val clonedEntity = super.clone()
    clonedEntity as ExtensionChildEntityData
    clonedEntity.listOfUrls = clonedEntity.listOfUrls.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ExtensionChildEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ExtensionChildEntity(extensionChildName, listOfUrls, entitySource) {
      parents.filterIsInstance<BaseTestEntityBuilder>().singleOrNull()?.let { this.parent = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(BaseTestEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ExtensionChildEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.extensionChildName != other.extensionChildName) return false
    if (this.listOfUrls != other.listOfUrls) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ExtensionChildEntityData
    if (this.extensionChildName != other.extensionChildName) return false
    if (this.listOfUrls != other.listOfUrls) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + extensionChildName.hashCode()
    result = 31 * result + listOfUrls.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + extensionChildName.hashCode()
    result = 31 * result + listOfUrls.hashCode()
    return result
  }
}
