// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities

import com.intellij.openapi.util.NlsSafe
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.EntityLink
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.UsedClassesCollector
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.extractOneToManyChildren
import com.intellij.workspaceModel.storage.impl.extractOneToManyParent
import com.intellij.workspaceModel.storage.impl.extractOneToOneChild
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyParentOfChild
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.annotations.NonNls
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class SourceRootEntityImpl(val dataSource: SourceRootEntityData) : SourceRootEntity, WorkspaceEntityBase() {

  companion object {
    internal val CONTENTROOT_CONNECTION_ID: ConnectionId = ConnectionId.create(ContentRootEntity::class.java, SourceRootEntity::class.java,
                                                                               ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val CUSTOMSOURCEROOTPROPERTIES_CONNECTION_ID: ConnectionId = ConnectionId.create(SourceRootEntity::class.java,
                                                                                              CustomSourceRootPropertiesEntity::class.java,
                                                                                              ConnectionId.ConnectionType.ONE_TO_ONE, false)
    internal val JAVASOURCEROOTS_CONNECTION_ID: ConnectionId = ConnectionId.create(SourceRootEntity::class.java,
                                                                                   JavaSourceRootPropertiesEntity::class.java,
                                                                                   ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val JAVARESOURCEROOTS_CONNECTION_ID: ConnectionId = ConnectionId.create(SourceRootEntity::class.java,
                                                                                     JavaResourceRootPropertiesEntity::class.java,
                                                                                     ConnectionId.ConnectionType.ONE_TO_MANY, false)

    val connections = listOf<ConnectionId>(
      CONTENTROOT_CONNECTION_ID,
      CUSTOMSOURCEROOTPROPERTIES_CONNECTION_ID,
      JAVASOURCEROOTS_CONNECTION_ID,
      JAVARESOURCEROOTS_CONNECTION_ID,
    )

  }

  override val contentRoot: ContentRootEntity
    get() = snapshot.extractOneToManyParent(CONTENTROOT_CONNECTION_ID, this)!!

  override val url: VirtualFileUrl
    get() = dataSource.url

  override val rootType: String
    get() = dataSource.rootType

  override val customSourceRootProperties: CustomSourceRootPropertiesEntity?
    get() = snapshot.extractOneToOneChild(CUSTOMSOURCEROOTPROPERTIES_CONNECTION_ID, this)

  override val javaSourceRoots: List<JavaSourceRootPropertiesEntity>
    get() = snapshot.extractOneToManyChildren<JavaSourceRootPropertiesEntity>(JAVASOURCEROOTS_CONNECTION_ID, this)!!.toList()

  override val javaResourceRoots: List<JavaResourceRootPropertiesEntity>
    get() = snapshot.extractOneToManyChildren<JavaResourceRootPropertiesEntity>(JAVARESOURCEROOTS_CONNECTION_ID, this)!!.toList()

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: SourceRootEntityData?) : ModifiableWorkspaceEntityBase<SourceRootEntity, SourceRootEntityData>(
    result), SourceRootEntity.Builder {
    constructor() : this(SourceRootEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity SourceRootEntity is already created in a different builder")
        }
      }

      this.diff = builder
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      index(this, "url", this.url)
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
        if (_diff.extractOneToManyParent<WorkspaceEntityBase>(CONTENTROOT_CONNECTION_ID, this) == null) {
          error("Field SourceRootEntity#contentRoot should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)] == null) {
          error("Field SourceRootEntity#contentRoot should be initialized")
        }
      }
      if (!getEntityData().isUrlInitialized()) {
        error("Field SourceRootEntity#url should be initialized")
      }
      if (!getEntityData().isRootTypeInitialized()) {
        error("Field SourceRootEntity#rootType should be initialized")
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(JAVASOURCEROOTS_CONNECTION_ID, this) == null) {
          error("Field SourceRootEntity#javaSourceRoots should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, JAVASOURCEROOTS_CONNECTION_ID)] == null) {
          error("Field SourceRootEntity#javaSourceRoots should be initialized")
        }
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(JAVARESOURCEROOTS_CONNECTION_ID, this) == null) {
          error("Field SourceRootEntity#javaResourceRoots should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, JAVARESOURCEROOTS_CONNECTION_ID)] == null) {
          error("Field SourceRootEntity#javaResourceRoots should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as SourceRootEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.url != dataSource.url) this.url = dataSource.url
      if (this.rootType != dataSource.rootType) this.rootType = dataSource.rootType
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var contentRoot: ContentRootEntity
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyParent(CONTENTROOT_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                       CONTENTROOT_CONNECTION_ID)]!! as ContentRootEntity
        }
        else {
          this.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)]!! as ContentRootEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, CONTENTROOT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, CONTENTROOT_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(CONTENTROOT_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, CONTENTROOT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, CONTENTROOT_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, CONTENTROOT_CONNECTION_ID)] = value
        }
        changedProperty.add("contentRoot")
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

    override var rootType: String
      get() = getEntityData().rootType
      set(value) {
        checkModificationAllowed()
        getEntityData(true).rootType = value
        changedProperty.add("rootType")
      }

    override var customSourceRootProperties: CustomSourceRootPropertiesEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToOneChild(CUSTOMSOURCEROOTPROPERTIES_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true,
                                                                                                                    CUSTOMSOURCEROOTPROPERTIES_CONNECTION_ID)] as? CustomSourceRootPropertiesEntity
        }
        else {
          this.entityLinks[EntityLink(true, CUSTOMSOURCEROOTPROPERTIES_CONNECTION_ID)] as? CustomSourceRootPropertiesEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, CUSTOMSOURCEROOTPROPERTIES_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(CUSTOMSOURCEROOTPROPERTIES_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, CUSTOMSOURCEROOTPROPERTIES_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true, CUSTOMSOURCEROOTPROPERTIES_CONNECTION_ID)] = value
        }
        changedProperty.add("customSourceRootProperties")
      }

    // List of non-abstract referenced types
    var _javaSourceRoots: List<JavaSourceRootPropertiesEntity>? = emptyList()
    override var javaSourceRoots: List<JavaSourceRootPropertiesEntity>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyChildren<JavaSourceRootPropertiesEntity>(JAVASOURCEROOTS_CONNECTION_ID,
                                                                         this)!!.toList() + (this.entityLinks[EntityLink(true,
                                                                                                                         JAVASOURCEROOTS_CONNECTION_ID)] as? List<JavaSourceRootPropertiesEntity>
                                                                                             ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, JAVASOURCEROOTS_CONNECTION_ID)] as? List<JavaSourceRootPropertiesEntity> ?: emptyList()
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
                item_value.entityLinks[EntityLink(false, JAVASOURCEROOTS_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable

              _diff.addEntity(item_value)
            }
          }
          _diff.updateOneToManyChildrenOfParent(JAVASOURCEROOTS_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, JAVASOURCEROOTS_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, JAVASOURCEROOTS_CONNECTION_ID)] = value
        }
        changedProperty.add("javaSourceRoots")
      }

    // List of non-abstract referenced types
    var _javaResourceRoots: List<JavaResourceRootPropertiesEntity>? = emptyList()
    override var javaResourceRoots: List<JavaResourceRootPropertiesEntity>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyChildren<JavaResourceRootPropertiesEntity>(JAVARESOURCEROOTS_CONNECTION_ID,
                                                                           this)!!.toList() + (this.entityLinks[EntityLink(true,
                                                                                                                           JAVARESOURCEROOTS_CONNECTION_ID)] as? List<JavaResourceRootPropertiesEntity>
                                                                                               ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, JAVARESOURCEROOTS_CONNECTION_ID)] as? List<JavaResourceRootPropertiesEntity> ?: emptyList()
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
                item_value.entityLinks[EntityLink(false, JAVARESOURCEROOTS_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable

              _diff.addEntity(item_value)
            }
          }
          _diff.updateOneToManyChildrenOfParent(JAVARESOURCEROOTS_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, JAVARESOURCEROOTS_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, JAVARESOURCEROOTS_CONNECTION_ID)] = value
        }
        changedProperty.add("javaResourceRoots")
      }

    override fun getEntityClass(): Class<SourceRootEntity> = SourceRootEntity::class.java
  }
}

class SourceRootEntityData : WorkspaceEntityData<SourceRootEntity>() {
  lateinit var url: VirtualFileUrl
  lateinit var rootType: String

  fun isUrlInitialized(): Boolean = ::url.isInitialized
  fun isRootTypeInitialized(): Boolean = ::rootType.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<SourceRootEntity> {
    val modifiable = SourceRootEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): SourceRootEntity {
    return getCached(snapshot) {
      val entity = SourceRootEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SourceRootEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return SourceRootEntity(url, rootType, entitySource) {
      this.contentRoot = parents.filterIsInstance<ContentRootEntity>().single()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ContentRootEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as SourceRootEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.url != other.url) return false
    if (this.rootType != other.rootType) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as SourceRootEntityData

    if (this.url != other.url) return false
    if (this.rootType != other.rootType) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + url.hashCode()
    result = 31 * result + rootType.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + url.hashCode()
    result = 31 * result + rootType.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    this.url?.let { collector.add(it::class.java) }
    collector.sameForAllEntities = false
  }
}
