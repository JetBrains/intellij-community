// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.EntityInformation
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.UsedClassesCollector
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.extractOneToOneParent
import com.intellij.platform.workspace.storage.impl.updateOneToOneParentOfChild
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.NonNls

@GeneratedCodeApiVersion(2)
@GeneratedCodeImplVersion(2)
open class CustomSourceRootPropertiesEntityImpl(val dataSource: CustomSourceRootPropertiesEntityData) : CustomSourceRootPropertiesEntity, WorkspaceEntityBase() {

  companion object {
    internal val SOURCEROOT_CONNECTION_ID: ConnectionId = ConnectionId.create(SourceRootEntity::class.java,
                                                                              CustomSourceRootPropertiesEntity::class.java,
                                                                              ConnectionId.ConnectionType.ONE_TO_ONE, false)

    val connections = listOf<ConnectionId>(
      SOURCEROOT_CONNECTION_ID,
    )

  }

  override val sourceRoot: SourceRootEntity
    get() = snapshot.extractOneToOneParent(SOURCEROOT_CONNECTION_ID, this)!!

  override val propertiesXmlTag: String
    get() = dataSource.propertiesXmlTag

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: CustomSourceRootPropertiesEntityData?) : ModifiableWorkspaceEntityBase<CustomSourceRootPropertiesEntity, CustomSourceRootPropertiesEntityData>(
    result), CustomSourceRootPropertiesEntity.Builder {
    constructor() : this(CustomSourceRootPropertiesEntityData())

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
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
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
      if (!getEntityData().isPropertiesXmlTagInitialized()) {
        error("Field CustomSourceRootPropertiesEntity#propertiesXmlTag should be initialized")
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

    override var sourceRoot: SourceRootEntity
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToOneParent(SOURCEROOT_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                     SOURCEROOT_CONNECTION_ID)]!! as SourceRootEntity
        }
        else {
          this.entityLinks[EntityLink(false, SOURCEROOT_CONNECTION_ID)]!! as SourceRootEntity
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
          _diff.addEntity(value)
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

    override var propertiesXmlTag: String
      get() = getEntityData().propertiesXmlTag
      set(value) {
        checkModificationAllowed()
        getEntityData(true).propertiesXmlTag = value
        changedProperty.add("propertiesXmlTag")
      }

    override fun getEntityClass(): Class<CustomSourceRootPropertiesEntity> = CustomSourceRootPropertiesEntity::class.java
  }
}

class CustomSourceRootPropertiesEntityData : WorkspaceEntityData<CustomSourceRootPropertiesEntity>() {
  lateinit var propertiesXmlTag: String

  fun isPropertiesXmlTagInitialized(): Boolean = ::propertiesXmlTag.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<CustomSourceRootPropertiesEntity> {
    val modifiable = CustomSourceRootPropertiesEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): CustomSourceRootPropertiesEntity {
    return getCached(snapshot) {
      val entity = CustomSourceRootPropertiesEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return CustomSourceRootPropertiesEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return CustomSourceRootPropertiesEntity(propertiesXmlTag, entitySource) {
      parents.filterIsInstance<SourceRootEntity>().singleOrNull()?.let { this.sourceRoot = it }
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

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.sameForAllEntities = true
  }
}
