// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.entities.impl

import com.intellij.java.workspace.entities.ArtifactEntity
import com.intellij.java.workspace.entities.CompositePackagingElementEntity
import com.intellij.java.workspace.entities.CustomPackagingElementEntity
import com.intellij.java.workspace.entities.PackagingElementEntity
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.extractOneToAbstractManyChildren
import com.intellij.platform.workspace.storage.impl.extractOneToAbstractManyParent
import com.intellij.platform.workspace.storage.impl.extractOneToAbstractOneParent
import com.intellij.platform.workspace.storage.impl.extractOneToManyChildren
import com.intellij.platform.workspace.storage.impl.updateOneToAbstractManyChildrenOfParent
import com.intellij.platform.workspace.storage.impl.updateOneToAbstractManyParentOfChild
import com.intellij.platform.workspace.storage.impl.updateOneToAbstractOneParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.NonNls

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class CustomPackagingElementEntityImpl(private val dataSource: CustomPackagingElementEntityData) : CustomPackagingElementEntity, WorkspaceEntityBase(
  dataSource) {

  private companion object {
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositePackagingElementEntity::class.java,
                                                                                PackagingElementEntity::class.java,
                                                                                ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, true)
    internal val ARTIFACT_CONNECTION_ID: ConnectionId = ConnectionId.create(ArtifactEntity::class.java,
                                                                            CompositePackagingElementEntity::class.java,
                                                                            ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, true)
    internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositePackagingElementEntity::class.java,
                                                                            PackagingElementEntity::class.java,
                                                                            ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, true)

    private val connections = listOf<ConnectionId>(
      PARENTENTITY_CONNECTION_ID,
      ARTIFACT_CONNECTION_ID,
      CHILDREN_CONNECTION_ID,
    )

  }

  override val parentEntity: CompositePackagingElementEntity?
    get() = snapshot.extractOneToAbstractManyParent(PARENTENTITY_CONNECTION_ID, this)

  override val artifact: ArtifactEntity?
    get() = snapshot.extractOneToAbstractOneParent(ARTIFACT_CONNECTION_ID, this)

  override val children: List<PackagingElementEntity>
    get() = snapshot.extractOneToAbstractManyChildren<PackagingElementEntity>(CHILDREN_CONNECTION_ID, this)!!.toList()

  override val typeId: String
    get() {
      readField("typeId")
      return dataSource.typeId
    }

  override val propertiesXmlTag: String
    get() {
      readField("propertiesXmlTag")
      return dataSource.propertiesXmlTag
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: CustomPackagingElementEntityData?) : ModifiableWorkspaceEntityBase<CustomPackagingElementEntity, CustomPackagingElementEntityData>(
    result), CustomPackagingElementEntity.Builder {
    internal constructor() : this(CustomPackagingElementEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity CustomPackagingElementEntity is already created in a different builder")
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
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null) {
          error("Field CompositePackagingElementEntity#children should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
          error("Field CompositePackagingElementEntity#children should be initialized")
        }
      }
      if (!getEntityData().isTypeIdInitialized()) {
        error("Field CustomPackagingElementEntity#typeId should be initialized")
      }
      if (!getEntityData().isPropertiesXmlTagInitialized()) {
        error("Field CustomPackagingElementEntity#propertiesXmlTag should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as CustomPackagingElementEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.typeId != dataSource.typeId) this.typeId = dataSource.typeId
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

    override var parentEntity: CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>?
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(PARENTENTITY_CONNECTION_ID,
                                                                           this) as? CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>)
          ?: (this.entityLinks[EntityLink(false,
                                          PARENTENTITY_CONNECTION_ID)] as? CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>)
        }
        else {
          this.entityLinks[EntityLink(false,
                                      PARENTENTITY_CONNECTION_ID)] as? CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToAbstractManyParentOfChild(PARENTENTITY_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] = value
        }
        changedProperty.add("parentEntity")
      }

    override var artifact: ArtifactEntity.Builder?
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(ARTIFACT_CONNECTION_ID, this) as? ArtifactEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, ARTIFACT_CONNECTION_ID)] as? ArtifactEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, ARTIFACT_CONNECTION_ID)] as? ArtifactEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, ARTIFACT_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToAbstractOneParentOfChild(ARTIFACT_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, ARTIFACT_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, ARTIFACT_CONNECTION_ID)] = value
        }
        changedProperty.add("artifact")
      }

    override var children: List<PackagingElementEntity.Builder<out PackagingElementEntity>>
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(CHILDREN_CONNECTION_ID,
                                                                                  this)!!.toList() as List<PackagingElementEntity.Builder<out PackagingElementEntity>>) +
          (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<PackagingElementEntity.Builder<out PackagingElementEntity>>
           ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as List<PackagingElementEntity.Builder<out PackagingElementEntity>>
          ?: emptyList()
        }
      }
      set(value) {
        // Set list of ref types for abstract entities
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null) {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *> && (item_value as? ModifiableWorkspaceEntityBase<*, *>)?.diff == null) {
              // Backref setup before adding to store an abstract entity
              if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
                item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable
              _diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
            }
          }
          _diff.updateOneToAbstractManyChildrenOfParent(CHILDREN_CONNECTION_ID, this, value.asSequence())
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] = value
        }
        changedProperty.add("children")
      }

    override var typeId: String
      get() = getEntityData().typeId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).typeId = value
        changedProperty.add("typeId")
      }

    override var propertiesXmlTag: String
      get() = getEntityData().propertiesXmlTag
      set(value) {
        checkModificationAllowed()
        getEntityData(true).propertiesXmlTag = value
        changedProperty.add("propertiesXmlTag")
      }

    override fun getEntityClass(): Class<CustomPackagingElementEntity> = CustomPackagingElementEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class CustomPackagingElementEntityData : WorkspaceEntityData<CustomPackagingElementEntity>() {
  lateinit var typeId: String
  lateinit var propertiesXmlTag: String

  internal fun isTypeIdInitialized(): Boolean = ::typeId.isInitialized
  internal fun isPropertiesXmlTagInitialized(): Boolean = ::propertiesXmlTag.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<CustomPackagingElementEntity> {
    val modifiable = CustomPackagingElementEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): CustomPackagingElementEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = CustomPackagingElementEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.java.workspace.entities.CustomPackagingElementEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return CustomPackagingElementEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return CustomPackagingElementEntity(typeId, propertiesXmlTag, entitySource) {
      this.parentEntity = parents.filterIsInstance<CompositePackagingElementEntity.Builder<out CompositePackagingElementEntity>>().singleOrNull()
      this.artifact = parents.filterIsInstance<ArtifactEntity.Builder>().singleOrNull()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as CustomPackagingElementEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.typeId != other.typeId) return false
    if (this.propertiesXmlTag != other.propertiesXmlTag) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as CustomPackagingElementEntityData

    if (this.typeId != other.typeId) return false
    if (this.propertiesXmlTag != other.propertiesXmlTag) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + typeId.hashCode()
    result = 31 * result + propertiesXmlTag.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + typeId.hashCode()
    result = 31 * result + propertiesXmlTag.hashCode()
    return result
  }
}
