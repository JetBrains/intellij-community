// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("RootsExtensions")

package com.intellij.platform.workspace.jps.entities.impl

import com.intellij.platform.workspace.jps.entities.CustomSourceRootPropertiesEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.extractOneToOneParent
import com.intellij.platform.workspace.storage.impl.updateOneToOneParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls

@Internal
@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class CustomSourceRootPropertiesEntityImpl(private val dataSource: CustomSourceRootPropertiesEntityData) :
  CustomSourceRootPropertiesEntity, WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val SOURCEROOT_CONNECTION_ID: ConnectionId = ConnectionId.create(
      SourceRootEntity::class.java, CustomSourceRootPropertiesEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false
    )

    private val connections = listOf<ConnectionId>(
      SOURCEROOT_CONNECTION_ID,
    )

  }

  override val propertiesXmlTag: String
    get() {
      readField("propertiesXmlTag")
      return dataSource.propertiesXmlTag
    }

  override val sourceRoot: SourceRootEntity
    get() = snapshot.extractOneToOneParent(SOURCEROOT_CONNECTION_ID, this)!!

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: CustomSourceRootPropertiesEntityData?) :
    ModifiableWorkspaceEntityBase<CustomSourceRootPropertiesEntity, CustomSourceRootPropertiesEntityData>(result),
    CustomSourceRootPropertiesEntity.Builder {
    internal constructor() : this(CustomSourceRootPropertiesEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity CustomSourceRootPropertiesEntity is already created in a different builder")
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
      if (!getEntityData().isPropertiesXmlTagInitialized()) {
        error("Field CustomSourceRootPropertiesEntity#propertiesXmlTag should be initialized")
      }
      if (_diff != null) {
        if (_diff.extractOneToOneParent<WorkspaceEntityBase>(SOURCEROOT_CONNECTION_ID, this) == null) {
          error("Field CustomSourceRootPropertiesEntity#sourceRoot should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, SOURCEROOT_CONNECTION_ID)] == null) {
          error("Field CustomSourceRootPropertiesEntity#sourceRoot should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as CustomSourceRootPropertiesEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.propertiesXmlTag != dataSource.propertiesXmlTag) this.propertiesXmlTag = dataSource.propertiesXmlTag
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var propertiesXmlTag: String
      get() = getEntityData().propertiesXmlTag
      set(value) {
        checkModificationAllowed()
        getEntityData(true).propertiesXmlTag = value
        changedProperty.add("propertiesXmlTag")
      }

    override var sourceRoot: SourceRootEntity.Builder
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(SOURCEROOT_CONNECTION_ID, this) as? SourceRootEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, SOURCEROOT_CONNECTION_ID)]!! as SourceRootEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, SOURCEROOT_CONNECTION_ID)]!! as SourceRootEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, SOURCEROOT_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneParentOfChild(SOURCEROOT_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, SOURCEROOT_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, SOURCEROOT_CONNECTION_ID)] = value
        }
        changedProperty.add("sourceRoot")
      }

    override fun getEntityClass(): Class<CustomSourceRootPropertiesEntity> = CustomSourceRootPropertiesEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class CustomSourceRootPropertiesEntityData : WorkspaceEntityData<CustomSourceRootPropertiesEntity>() {
  lateinit var propertiesXmlTag: String

  internal fun isPropertiesXmlTagInitialized(): Boolean = ::propertiesXmlTag.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<CustomSourceRootPropertiesEntity> {
    val modifiable = CustomSourceRootPropertiesEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): CustomSourceRootPropertiesEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = CustomSourceRootPropertiesEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.jps.entities.CustomSourceRootPropertiesEntity"
    ) as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return CustomSourceRootPropertiesEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return CustomSourceRootPropertiesEntity(propertiesXmlTag, entitySource) {
      parents.filterIsInstance<SourceRootEntity.Builder>().singleOrNull()?.let { this.sourceRoot = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(SourceRootEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as CustomSourceRootPropertiesEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.propertiesXmlTag != other.propertiesXmlTag) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as CustomSourceRootPropertiesEntityData

    if (this.propertiesXmlTag != other.propertiesXmlTag) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + propertiesXmlTag.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + propertiesXmlTag.hashCode()
    return result
  }
}
