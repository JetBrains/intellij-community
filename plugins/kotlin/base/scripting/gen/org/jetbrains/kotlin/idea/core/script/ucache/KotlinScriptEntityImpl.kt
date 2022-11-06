// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.SymbolicEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.EntityLink
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.UsedClassesCollector
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.extractOneToManyChildren
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class KotlinScriptEntityImpl(val dataSource: KotlinScriptEntityData) : KotlinScriptEntity, WorkspaceEntityBase() {

  companion object {
    internal val DEPENDENCIES_CONNECTION_ID: ConnectionId = ConnectionId.create(KotlinScriptEntity::class.java, LibraryEntity::class.java,
                                                                                ConnectionId.ConnectionType.ONE_TO_MANY, true)

    val connections = listOf<ConnectionId>(
      DEPENDENCIES_CONNECTION_ID,
    )

  }

  override val path: String
    get() = dataSource.path

  override val dependencies: List<LibraryEntity>
    get() = snapshot.extractOneToManyChildren<LibraryEntity>(DEPENDENCIES_CONNECTION_ID, this)!!.toList()

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: KotlinScriptEntityData?) : ModifiableWorkspaceEntityBase<KotlinScriptEntity, KotlinScriptEntityData>(
    result), KotlinScriptEntity.Builder {
    constructor() : this(KotlinScriptEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity KotlinScriptEntity is already created in a different builder")
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
      if (!getEntityData().isPathInitialized()) {
        error("Field KotlinScriptEntity#path should be initialized")
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(DEPENDENCIES_CONNECTION_ID, this) == null) {
          error("Field KotlinScriptEntity#dependencies should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, DEPENDENCIES_CONNECTION_ID)] == null) {
          error("Field KotlinScriptEntity#dependencies should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as KotlinScriptEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.path != dataSource.path) this.path = dataSource.path
      if (parents != null) {
      }
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var path: String
      get() = getEntityData().path
      set(value) {
        checkModificationAllowed()
        getEntityData(true).path = value
        changedProperty.add("path")
      }

    // List of non-abstract referenced types
    var _dependencies: List<LibraryEntity>? = emptyList()
    override var dependencies: List<LibraryEntity>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyChildren<LibraryEntity>(DEPENDENCIES_CONNECTION_ID, this)!!.toList() + (this.entityLinks[EntityLink(true,
                                                                                                                                    DEPENDENCIES_CONNECTION_ID)] as? List<LibraryEntity>
                                                                                                        ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, DEPENDENCIES_CONNECTION_ID)] as? List<LibraryEntity> ?: emptyList()
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
                item_value.entityLinks[EntityLink(false, DEPENDENCIES_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable

              _diff.addEntity(item_value)
            }
          }
          _diff.updateOneToManyChildrenOfParent(DEPENDENCIES_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, DEPENDENCIES_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, DEPENDENCIES_CONNECTION_ID)] = value
        }
        changedProperty.add("dependencies")
      }

    override fun getEntityClass(): Class<KotlinScriptEntity> = KotlinScriptEntity::class.java
  }
}

class KotlinScriptEntityData : WorkspaceEntityData.WithCalculableSymbolicId<KotlinScriptEntity>() {
  lateinit var path: String

  fun isPathInitialized(): Boolean = ::path.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<KotlinScriptEntity> {
    val modifiable = KotlinScriptEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): KotlinScriptEntity {
    return getCached(snapshot) {
      val entity = KotlinScriptEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun symbolicId(): SymbolicEntityId<*> {
    return ScriptId(path)
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return KotlinScriptEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return KotlinScriptEntity(path, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as KotlinScriptEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.path != other.path) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as KotlinScriptEntityData

    if (this.path != other.path) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + path.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + path.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.sameForAllEntities = true
  }
}
