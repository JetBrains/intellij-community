// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.EntityLink
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.UsedClassesCollector
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractManyChildren
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractManyParent
import com.intellij.workspaceModel.storage.impl.extractOneToAbstractOneParent
import com.intellij.workspaceModel.storage.impl.extractOneToManyChildren
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractManyChildrenOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractManyParentOfChild
import com.intellij.workspaceModel.storage.impl.updateOneToAbstractOneParentOfChild
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class CustomPackagingElementEntityImpl : CustomPackagingElementEntity, WorkspaceEntityBase() {

  companion object {
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositePackagingElementEntity::class.java,
                                                                                PackagingElementEntity::class.java,
                                                                                ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, true)
    internal val ARTIFACT_CONNECTION_ID: ConnectionId = ConnectionId.create(ArtifactEntity::class.java,
                                                                            CompositePackagingElementEntity::class.java,
                                                                            ConnectionId.ConnectionType.ABSTRACT_ONE_TO_ONE, true)
    internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(CompositePackagingElementEntity::class.java,
                                                                            PackagingElementEntity::class.java,
                                                                            ConnectionId.ConnectionType.ONE_TO_ABSTRACT_MANY, true)

    val connections = listOf<ConnectionId>(
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

  @JvmField
  var _typeId: String? = null
  override val typeId: String
    get() = _typeId!!

  @JvmField
  var _propertiesXmlTag: String? = null
  override val propertiesXmlTag: String
    get() = _propertiesXmlTag!!

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(val result: CustomPackagingElementEntityData?) : ModifiableWorkspaceEntityBase<CustomPackagingElementEntity>(), CustomPackagingElementEntity.Builder {
    constructor() : this(CustomPackagingElementEntityData())

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
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()

      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    fun checkInitialization() {
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
      this.entitySource = dataSource.entitySource
      this.typeId = dataSource.typeId
      this.propertiesXmlTag = dataSource.propertiesXmlTag
      if (parents != null) {
        this.parentEntity = parents.filterIsInstance<CompositePackagingElementEntity>().singleOrNull()
        this.artifact = parents.filterIsInstance<ArtifactEntity>().singleOrNull()
      }
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData().entitySource = value
        changedProperty.add("entitySource")

      }

    override var parentEntity: CompositePackagingElementEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToAbstractManyParent(PARENTENTITY_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                                PARENTENTITY_CONNECTION_ID)] as? CompositePackagingElementEntity
        }
        else {
          this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] as? CompositePackagingElementEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*>) {
            val data = (value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
          _diff.updateOneToAbstractManyParentOfChild(PARENTENTITY_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*>) {
            val data = (value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] = value
        }
        changedProperty.add("parentEntity")
      }

    override var artifact: ArtifactEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToAbstractOneParent(ARTIFACT_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                           ARTIFACT_CONNECTION_ID)] as? ArtifactEntity
        }
        else {
          this.entityLinks[EntityLink(false, ARTIFACT_CONNECTION_ID)] as? ArtifactEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*>) {
            value.entityLinks[EntityLink(true, ARTIFACT_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
          _diff.updateOneToAbstractOneParentOfChild(ARTIFACT_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*>) {
            value.entityLinks[EntityLink(true, ARTIFACT_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, ARTIFACT_CONNECTION_ID)] = value
        }
        changedProperty.add("artifact")
      }

    override var children: List<PackagingElementEntity>
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToAbstractManyChildren<PackagingElementEntity>(CHILDREN_CONNECTION_ID,
                                                                         this)!!.toList() + (this.entityLinks[EntityLink(true,
                                                                                                                         CHILDREN_CONNECTION_ID)] as? List<PackagingElementEntity>
                                                                                             ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as List<PackagingElementEntity> ?: emptyList()
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null) {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*> && (item_value as? ModifiableWorkspaceEntityBase<*>)?.diff == null) {
              _diff.addEntity(item_value)
            }
          }
          _diff.updateOneToAbstractManyChildrenOfParent(CHILDREN_CONNECTION_ID, this, value.asSequence())
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*>) {
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
        getEntityData().typeId = value
        changedProperty.add("typeId")
      }

    override var propertiesXmlTag: String
      get() = getEntityData().propertiesXmlTag
      set(value) {
        checkModificationAllowed()
        getEntityData().propertiesXmlTag = value
        changedProperty.add("propertiesXmlTag")
      }

    override fun getEntityData(): CustomPackagingElementEntityData = result ?: super.getEntityData() as CustomPackagingElementEntityData
    override fun getEntityClass(): Class<CustomPackagingElementEntity> = CustomPackagingElementEntity::class.java
  }
}

class CustomPackagingElementEntityData : WorkspaceEntityData<CustomPackagingElementEntity>() {
  lateinit var typeId: String
  lateinit var propertiesXmlTag: String

  fun isTypeIdInitialized(): Boolean = ::typeId.isInitialized
  fun isPropertiesXmlTagInitialized(): Boolean = ::propertiesXmlTag.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<CustomPackagingElementEntity> {
    val modifiable = CustomPackagingElementEntityImpl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
      modifiable.entitySource = this.entitySource
    }
    modifiable.changedProperty.clear()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): CustomPackagingElementEntity {
    val entity = CustomPackagingElementEntityImpl()
    entity._typeId = typeId
    entity._propertiesXmlTag = propertiesXmlTag
    entity.entitySource = entitySource
    entity.snapshot = snapshot
    entity.id = createEntityId()
    return entity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return CustomPackagingElementEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return CustomPackagingElementEntity(typeId, propertiesXmlTag, entitySource) {
      this.parentEntity = parents.filterIsInstance<CompositePackagingElementEntity>().singleOrNull()
      this.artifact = parents.filterIsInstance<ArtifactEntity>().singleOrNull()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as CustomPackagingElementEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.typeId != other.typeId) return false
    if (this.propertiesXmlTag != other.propertiesXmlTag) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

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

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.sameForAllEntities = true
  }
}
