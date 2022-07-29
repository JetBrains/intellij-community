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
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToManyParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyParentOfChild
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Abstract
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class ArtifactPropertiesEntityImpl : ArtifactPropertiesEntity, WorkspaceEntityBase() {

  companion object {
    internal val ARTIFACT_CONNECTION_ID: ConnectionId = ConnectionId.create(ArtifactEntity::class.java,
                                                                            ArtifactPropertiesEntity::class.java,
                                                                            ConnectionId.ConnectionType.ONE_TO_MANY, false)

    val connections = listOf<ConnectionId>(
      ARTIFACT_CONNECTION_ID,
    )

  }

  override val artifact: ArtifactEntity
    get() = snapshot.extractOneToManyParent(ARTIFACT_CONNECTION_ID, this)!!

  @JvmField
  var _providerType: String? = null
  override val providerType: String
    get() = _providerType!!

  @JvmField
  var _propertiesXmlTag: String? = null
  override val propertiesXmlTag: String?
    get() = _propertiesXmlTag

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(val result: ArtifactPropertiesEntityData?) : ModifiableWorkspaceEntityBase<ArtifactPropertiesEntity>(), ArtifactPropertiesEntity.Builder {
    constructor() : this(ArtifactPropertiesEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ArtifactPropertiesEntity is already created in a different builder")
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
      if (_diff != null) {
        if (_diff.extractOneToManyParent<WorkspaceEntityBase>(ARTIFACT_CONNECTION_ID, this) == null) {
          error("Field ArtifactPropertiesEntity#artifact should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, ARTIFACT_CONNECTION_ID)] == null) {
          error("Field ArtifactPropertiesEntity#artifact should be initialized")
        }
      }
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field ArtifactPropertiesEntity#entitySource should be initialized")
      }
      if (!getEntityData().isProviderTypeInitialized()) {
        error("Field ArtifactPropertiesEntity#providerType should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }


    override var artifact: ArtifactEntity
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyParent(ARTIFACT_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                    ARTIFACT_CONNECTION_ID)]!! as ArtifactEntity
        }
        else {
          this.entityLinks[EntityLink(false, ARTIFACT_CONNECTION_ID)]!! as ArtifactEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*>) {
            val data = (value.entityLinks[EntityLink(true, ARTIFACT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, ARTIFACT_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(ARTIFACT_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*>) {
            val data = (value.entityLinks[EntityLink(true, ARTIFACT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, ARTIFACT_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, ARTIFACT_CONNECTION_ID)] = value
        }
        changedProperty.add("artifact")
      }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData().entitySource = value
        changedProperty.add("entitySource")

      }

    override var providerType: String
      get() = getEntityData().providerType
      set(value) {
        checkModificationAllowed()
        getEntityData().providerType = value
        changedProperty.add("providerType")
      }

    override var propertiesXmlTag: String?
      get() = getEntityData().propertiesXmlTag
      set(value) {
        checkModificationAllowed()
        getEntityData().propertiesXmlTag = value
        changedProperty.add("propertiesXmlTag")
      }

    override fun getEntityData(): ArtifactPropertiesEntityData = result ?: super.getEntityData() as ArtifactPropertiesEntityData
    override fun getEntityClass(): Class<ArtifactPropertiesEntity> = ArtifactPropertiesEntity::class.java
  }
}

class ArtifactPropertiesEntityData : WorkspaceEntityData<ArtifactPropertiesEntity>() {
  lateinit var providerType: String
  var propertiesXmlTag: String? = null

  fun isProviderTypeInitialized(): Boolean = ::providerType.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<ArtifactPropertiesEntity> {
    val modifiable = ArtifactPropertiesEntityImpl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
      modifiable.entitySource = this.entitySource
    }
    modifiable.changedProperty.clear()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): ArtifactPropertiesEntity {
    val entity = ArtifactPropertiesEntityImpl()
    entity._providerType = providerType
    entity._propertiesXmlTag = propertiesXmlTag
    entity.entitySource = entitySource
    entity.snapshot = snapshot
    entity.id = createEntityId()
    return entity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ArtifactPropertiesEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as ArtifactPropertiesEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.providerType != other.providerType) return false
    if (this.propertiesXmlTag != other.propertiesXmlTag) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as ArtifactPropertiesEntityData

    if (this.providerType != other.providerType) return false
    if (this.propertiesXmlTag != other.propertiesXmlTag) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + providerType.hashCode()
    result = 31 * result + propertiesXmlTag.hashCode()
    return result
  }
}
