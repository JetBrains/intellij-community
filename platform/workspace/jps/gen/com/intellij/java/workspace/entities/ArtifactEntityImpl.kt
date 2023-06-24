// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.EntityInformation
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.UsedClassesCollector
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.extractOneToAbstractOneChild
import com.intellij.platform.workspace.storage.impl.extractOneToManyChildren
import com.intellij.platform.workspace.storage.impl.extractOneToOneChild
import com.intellij.platform.workspace.storage.impl.updateOneToAbstractOneChildOfParent
import com.intellij.platform.workspace.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.platform.workspace.storage.impl.updateOneToOneChildOfParent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.NonNls

@GeneratedCodeApiVersion(2)
@GeneratedCodeImplVersion(2)
open class ArtifactEntityImpl(val dataSource: ArtifactEntityData) : ArtifactEntity, WorkspaceEntityBase() {

  companion object {
    internal val ROOTELEMENT_CONNECTION_ID: ConnectionId = ConnectionId.create(ArtifactEntity::class.java,
                                                                               CompositePackagingElementEntity::class.java,
                                                                               ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, true)
    internal val CUSTOMPROPERTIES_CONNECTION_ID: ConnectionId = ConnectionId.create(ArtifactEntity::class.java,
                                                                                    ArtifactPropertiesEntity::class.java,
                                                                                    ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID: ConnectionId = ConnectionId.create(ArtifactEntity::class.java,
                                                                                                  ArtifactOutputPackagingElementEntity::class.java,
                                                                                                  ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                  false)

    val connections = listOf<ConnectionId>(
      ROOTELEMENT_CONNECTION_ID,
      CUSTOMPROPERTIES_CONNECTION_ID,
      ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID,
    )

  }

  override val name: String
    get() = dataSource.name

  override val artifactType: String
    get() = dataSource.artifactType

  override val includeInProjectBuild: Boolean get() = dataSource.includeInProjectBuild
  override val outputUrl: VirtualFileUrl?
    get() = dataSource.outputUrl

  override val rootElement: CompositePackagingElementEntity?
    get() = snapshot.extractOneToAbstractOneChild(ROOTELEMENT_CONNECTION_ID, this)

  override val customProperties: List<ArtifactPropertiesEntity>
    get() = snapshot.extractOneToManyChildren<ArtifactPropertiesEntity>(CUSTOMPROPERTIES_CONNECTION_ID, this)!!.toList()

  override val artifactOutputPackagingElement: ArtifactOutputPackagingElementEntity?
    get() = snapshot.extractOneToOneChild(ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID, this)

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: ArtifactEntityData?) : ModifiableWorkspaceEntityBase<ArtifactEntity, ArtifactEntityData>(
    result), ArtifactEntity.Builder {
    constructor() : this(ArtifactEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ArtifactEntity is already created in a different builder")
        }
      }

      this.diff = builder
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      index(this, "outputUrl", this.outputUrl)
      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isNameInitialized()) {
        error("Field ArtifactEntity#name should be initialized")
      }
      if (!getEntityData().isArtifactTypeInitialized()) {
        error("Field ArtifactEntity#artifactType should be initialized")
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CUSTOMPROPERTIES_CONNECTION_ID, this) == null) {
          error("Field ArtifactEntity#customProperties should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CUSTOMPROPERTIES_CONNECTION_ID)] == null) {
          error("Field ArtifactEntity#customProperties should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ArtifactEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.artifactType != dataSource.artifactType) this.artifactType = dataSource.artifactType
      if (this.includeInProjectBuild != dataSource.includeInProjectBuild) this.includeInProjectBuild = dataSource.includeInProjectBuild
      if (this.outputUrl != dataSource?.outputUrl) this.outputUrl = dataSource.outputUrl
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

    override var artifactType: String
      get() = getEntityData().artifactType
      set(value) {
        checkModificationAllowed()
        getEntityData(true).artifactType = value
        changedProperty.add("artifactType")
      }

    override var includeInProjectBuild: Boolean
      get() = getEntityData().includeInProjectBuild
      set(value) {
        checkModificationAllowed()
        getEntityData(true).includeInProjectBuild = value
        changedProperty.add("includeInProjectBuild")
      }

    override var outputUrl: VirtualFileUrl?
      get() = getEntityData().outputUrl
      set(value) {
        checkModificationAllowed()
        getEntityData(true).outputUrl = value
        changedProperty.add("outputUrl")
        val _diff = diff
        if (_diff != null) index(this, "outputUrl", value)
      }

    override var rootElement: CompositePackagingElementEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToAbstractOneChild(ROOTELEMENT_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true,
                                                                                                             ROOTELEMENT_CONNECTION_ID)] as? CompositePackagingElementEntity
        }
        else {
          this.entityLinks[EntityLink(true, ROOTELEMENT_CONNECTION_ID)] as? CompositePackagingElementEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, ROOTELEMENT_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToAbstractOneChildOfParent(ROOTELEMENT_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, ROOTELEMENT_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true, ROOTELEMENT_CONNECTION_ID)] = value
        }
        changedProperty.add("rootElement")
      }

    // List of non-abstract referenced types
    var _customProperties: List<ArtifactPropertiesEntity>? = emptyList()
    override var customProperties: List<ArtifactPropertiesEntity>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyChildren<ArtifactPropertiesEntity>(CUSTOMPROPERTIES_CONNECTION_ID,
                                                                   this)!!.toList() + (this.entityLinks[EntityLink(true,
                                                                                                                   CUSTOMPROPERTIES_CONNECTION_ID)] as? List<ArtifactPropertiesEntity>
                                                                                       ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, CUSTOMPROPERTIES_CONNECTION_ID)] as? List<ArtifactPropertiesEntity> ?: emptyList()
        }
      }
      set(value) {
        // Setter of the list of non-abstract referenced types
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null) {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *> && (item_value as? ModifiableWorkspaceEntityBase<*, *>)?.diff == null) {
              // Backref setup before adding to store
              if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
                item_value.entityLinks[EntityLink(false, CUSTOMPROPERTIES_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable

              _diff.addEntity(item_value)
            }
          }
          _diff.updateOneToManyChildrenOfParent(CUSTOMPROPERTIES_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, CUSTOMPROPERTIES_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, CUSTOMPROPERTIES_CONNECTION_ID)] = value
        }
        changedProperty.add("customProperties")
      }

    override var artifactOutputPackagingElement: ArtifactOutputPackagingElementEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToOneChild(ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true,
                                                                                                                        ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID)] as? ArtifactOutputPackagingElementEntity
        }
        else {
          this.entityLinks[EntityLink(true, ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID)] as? ArtifactOutputPackagingElementEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true, ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID)] = value
        }
        changedProperty.add("artifactOutputPackagingElement")
      }

    override fun getEntityClass(): Class<ArtifactEntity> = ArtifactEntity::class.java
  }
}

class ArtifactEntityData : WorkspaceEntityData.WithCalculableSymbolicId<ArtifactEntity>() {
  lateinit var name: String
  lateinit var artifactType: String
  var includeInProjectBuild: Boolean = false
  var outputUrl: VirtualFileUrl? = null

  fun isNameInitialized(): Boolean = ::name.isInitialized
  fun isArtifactTypeInitialized(): Boolean = ::artifactType.isInitialized


  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<ArtifactEntity> {
    val modifiable = ArtifactEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): ArtifactEntity {
    return getCached(snapshot) {
      val entity = ArtifactEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun symbolicId(): SymbolicEntityId<*> {
    return ArtifactId(name)
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ArtifactEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return ArtifactEntity(name, artifactType, includeInProjectBuild, entitySource) {
      this.outputUrl = this@ArtifactEntityData.outputUrl
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ArtifactEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.name != other.name) return false
    if (this.artifactType != other.artifactType) return false
    if (this.includeInProjectBuild != other.includeInProjectBuild) return false
    if (this.outputUrl != other.outputUrl) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ArtifactEntityData

    if (this.name != other.name) return false
    if (this.artifactType != other.artifactType) return false
    if (this.includeInProjectBuild != other.includeInProjectBuild) return false
    if (this.outputUrl != other.outputUrl) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + artifactType.hashCode()
    result = 31 * result + includeInProjectBuild.hashCode()
    result = 31 * result + outputUrl.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + artifactType.hashCode()
    result = 31 * result + includeInProjectBuild.hashCode()
    result = 31 * result + outputUrl.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    this.outputUrl?.let { collector.add(it::class.java) }
    collector.sameForAllEntities = false
  }
}
