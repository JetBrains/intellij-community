// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations.impl

import com.intellij.platform.workspace.jps.entities.LibraryEntity
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
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceSet
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.platform.workspace.storage.impl.extractOneToOneParent
import com.intellij.platform.workspace.storage.impl.updateOneToOneParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations.KotlinForwardDeclarationsWorkspaceEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class KotlinForwardDeclarationsWorkspaceEntityImpl(private val dataSource: KotlinForwardDeclarationsWorkspaceEntityData) :
  KotlinForwardDeclarationsWorkspaceEntity, WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val LIBRARY_CONNECTION_ID: ConnectionId = ConnectionId.create(
      LibraryEntity::class.java, KotlinForwardDeclarationsWorkspaceEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false
    )

    private val connections = listOf<ConnectionId>(
      LIBRARY_CONNECTION_ID,
    )

  }

  override val forwardDeclarationRoots: Set<VirtualFileUrl>
    get() {
      readField("forwardDeclarationRoots")
      return dataSource.forwardDeclarationRoots
    }

  override val library: LibraryEntity
    get() = snapshot.extractOneToOneParent(LIBRARY_CONNECTION_ID, this)!!

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: KotlinForwardDeclarationsWorkspaceEntityData?) :
    ModifiableWorkspaceEntityBase<KotlinForwardDeclarationsWorkspaceEntity, KotlinForwardDeclarationsWorkspaceEntityData>(result),
    KotlinForwardDeclarationsWorkspaceEntity.Builder {
    internal constructor() : this(KotlinForwardDeclarationsWorkspaceEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity KotlinForwardDeclarationsWorkspaceEntity is already created in a different builder")
        }
      }

      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      index(this, "forwardDeclarationRoots", this.forwardDeclarationRoots)
      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isForwardDeclarationRootsInitialized()) {
        error("Field KotlinForwardDeclarationsWorkspaceEntity#forwardDeclarationRoots should be initialized")
      }
      if (_diff != null) {
        if (_diff.extractOneToOneParent<WorkspaceEntityBase>(LIBRARY_CONNECTION_ID, this) == null) {
          error("Field KotlinForwardDeclarationsWorkspaceEntity#library should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, LIBRARY_CONNECTION_ID)] == null) {
          error("Field KotlinForwardDeclarationsWorkspaceEntity#library should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_forwardDeclarationRoots = getEntityData().forwardDeclarationRoots
      if (collection_forwardDeclarationRoots is MutableWorkspaceSet<*>) {
        collection_forwardDeclarationRoots.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as KotlinForwardDeclarationsWorkspaceEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.forwardDeclarationRoots != dataSource.forwardDeclarationRoots) this.forwardDeclarationRoots =
        dataSource.forwardDeclarationRoots.toMutableSet()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    private val forwardDeclarationRootsUpdater: (value: Set<VirtualFileUrl>) -> Unit = { value ->
      val _diff = diff
      if (_diff != null) index(this, "forwardDeclarationRoots", value)
      changedProperty.add("forwardDeclarationRoots")
    }
    override var forwardDeclarationRoots: MutableSet<VirtualFileUrl>
      get() {
        val collection_forwardDeclarationRoots = getEntityData().forwardDeclarationRoots
        if (collection_forwardDeclarationRoots !is MutableWorkspaceSet) return collection_forwardDeclarationRoots
        if (diff == null || modifiable.get()) {
          collection_forwardDeclarationRoots.setModificationUpdateAction(forwardDeclarationRootsUpdater)
        }
        else {
          collection_forwardDeclarationRoots.cleanModificationUpdateAction()
        }
        return collection_forwardDeclarationRoots
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).forwardDeclarationRoots = value
        forwardDeclarationRootsUpdater.invoke(value)
      }

    override var library: LibraryEntity.Builder
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(LIBRARY_CONNECTION_ID, this) as? LibraryEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, LIBRARY_CONNECTION_ID)]!! as LibraryEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, LIBRARY_CONNECTION_ID)]!! as LibraryEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, LIBRARY_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneParentOfChild(LIBRARY_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, LIBRARY_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, LIBRARY_CONNECTION_ID)] = value
        }
        changedProperty.add("library")
      }

    override fun getEntityClass(): Class<KotlinForwardDeclarationsWorkspaceEntity> = KotlinForwardDeclarationsWorkspaceEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class KotlinForwardDeclarationsWorkspaceEntityData : WorkspaceEntityData<KotlinForwardDeclarationsWorkspaceEntity>() {
  lateinit var forwardDeclarationRoots: MutableSet<VirtualFileUrl>

  internal fun isForwardDeclarationRootsInitialized(): Boolean = ::forwardDeclarationRoots.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<KotlinForwardDeclarationsWorkspaceEntity> {
    val modifiable = KotlinForwardDeclarationsWorkspaceEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): KotlinForwardDeclarationsWorkspaceEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = KotlinForwardDeclarationsWorkspaceEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations.KotlinForwardDeclarationsWorkspaceEntity"
    ) as EntityMetadata
  }

  override fun clone(): KotlinForwardDeclarationsWorkspaceEntityData {
    val clonedEntity = super.clone()
    clonedEntity as KotlinForwardDeclarationsWorkspaceEntityData
    clonedEntity.forwardDeclarationRoots = clonedEntity.forwardDeclarationRoots.toMutableWorkspaceSet()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return KotlinForwardDeclarationsWorkspaceEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return KotlinForwardDeclarationsWorkspaceEntity(forwardDeclarationRoots, entitySource) {
      parents.filterIsInstance<LibraryEntity.Builder>().singleOrNull()?.let { this.library = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(LibraryEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as KotlinForwardDeclarationsWorkspaceEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.forwardDeclarationRoots != other.forwardDeclarationRoots) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as KotlinForwardDeclarationsWorkspaceEntityData

    if (this.forwardDeclarationRoots != other.forwardDeclarationRoots) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + forwardDeclarationRoots.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + forwardDeclarationRoots.hashCode()
    return result
  }
}
