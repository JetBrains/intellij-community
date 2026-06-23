// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.java.workspace.entities.impl

import com.intellij.java.workspace.entities.JavaModuleCompilerOptionsEntity
import com.intellij.java.workspace.entities.JavaModuleCompilerOptionsEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
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

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class JavaModuleCompilerOptionsEntityImpl(private val dataSource: JavaModuleCompilerOptionsEntityData) :
  JavaModuleCompilerOptionsEntity, WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java,
                                                                          JavaModuleCompilerOptionsEntity::class.java,
                                                                          ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                          false)
    private val connections = listOf<ConnectionId>(MODULE_CONNECTION_ID)

  }

  override val module: ModuleEntity
    get() = snapshot.instrumentation.getParent(MODULE_CONNECTION_ID, this) as? ModuleEntity
            ?: error("Parent module not found for JavaModuleCompilerOptionsEntity")
  override val additionalOptions: List<String>
    get() {
      readField("additionalOptions")
      return dataSource.additionalOptions
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: JavaModuleCompilerOptionsEntityData?) :
    ModifiableWorkspaceEntityBase<JavaModuleCompilerOptionsEntity, JavaModuleCompilerOptionsEntityData>(result),
    JavaModuleCompilerOptionsEntityBuilder {
    internal constructor() : this(JavaModuleCompilerOptionsEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity JavaModuleCompilerOptionsEntity is already created in a different builder")
        }
      }
      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
// After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
// Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null
// Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(MODULE_CONNECTION_ID, this) == null) {
          error("Field JavaModuleCompilerOptionsEntity#module should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null) {
          error("Field JavaModuleCompilerOptionsEntity#module should be initialized")
        }
      }
      if (!getEntityData().isAdditionalOptionsInitialized()) {
        error("Field JavaModuleCompilerOptionsEntity#additionalOptions should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_additionalOptions = getEntityData().additionalOptions
      if (collection_additionalOptions is MutableWorkspaceList<*>) {
        collection_additionalOptions.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as JavaModuleCompilerOptionsEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.additionalOptions != dataSource.additionalOptions) this.additionalOptions = dataSource.additionalOptions.toMutableList()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }
    override var module: ModuleEntityBuilder
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(MODULE_CONNECTION_ID, this) as? ModuleEntityBuilder)
          ?: (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] as? ModuleEntityBuilder)
          ?: error("module is null for JavaModuleCompilerOptionsEntity")
        }
        else {
          (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] as? ModuleEntityBuilder)
          ?: error("module is null for JavaModuleCompilerOptionsEntity")
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = this
          }
// else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.instrumentation.addChild(MODULE_CONNECTION_ID, value, this)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = this
          }
// else you're attaching a new entity to an existing entity that is not modifiable
          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] = value
        }
        changedProperty.add("module")
      }

    private val additionalOptionsUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("additionalOptions")
    }
    override var additionalOptions: MutableList<String>
      get() {
        val collection_additionalOptions = getEntityData().additionalOptions
        if (collection_additionalOptions !is MutableWorkspaceList) return collection_additionalOptions
        if (diff == null || modifiable.get()) {
          collection_additionalOptions.setModificationUpdateAction(additionalOptionsUpdater)
        }
        else {
          collection_additionalOptions.cleanModificationUpdateAction()
        }
        return collection_additionalOptions
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).additionalOptions = value
        additionalOptionsUpdater.invoke(value)
      }

    override fun getEntityClass(): Class<JavaModuleCompilerOptionsEntity> = JavaModuleCompilerOptionsEntity::class.java
  }

}

@OptIn(WorkspaceEntityInternalApi::class)
internal class JavaModuleCompilerOptionsEntityData : WorkspaceEntityData<JavaModuleCompilerOptionsEntity>() {
  lateinit var additionalOptions: MutableList<String>

  internal fun isAdditionalOptionsInitialized(): Boolean = ::additionalOptions.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<JavaModuleCompilerOptionsEntity> {
    val modifiable = JavaModuleCompilerOptionsEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorageInstrumentation): JavaModuleCompilerOptionsEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = JavaModuleCompilerOptionsEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.java.workspace.entities.JavaModuleCompilerOptionsEntity") as EntityMetadata
  }

  override fun clone(): JavaModuleCompilerOptionsEntityData {
    val clonedEntity = super.clone()
    clonedEntity as JavaModuleCompilerOptionsEntityData
    clonedEntity.additionalOptions = clonedEntity.additionalOptions.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return JavaModuleCompilerOptionsEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return JavaModuleCompilerOptionsEntity(additionalOptions, entitySource) {
      parents.filterIsInstance<ModuleEntityBuilder>().singleOrNull()?.let { this.module = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ModuleEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as JavaModuleCompilerOptionsEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.additionalOptions != other.additionalOptions) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as JavaModuleCompilerOptionsEntityData
    if (this.additionalOptions != other.additionalOptions) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + additionalOptions.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + additionalOptions.hashCode()
    return result
  }
}
