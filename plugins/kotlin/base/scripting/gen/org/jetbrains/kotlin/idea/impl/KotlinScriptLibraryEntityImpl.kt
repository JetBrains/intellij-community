// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.kotlin.idea.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.KotlinScriptLibraryEntityId

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class KotlinScriptLibraryEntityImpl(private val dataSource: KotlinScriptLibraryEntityData) : KotlinScriptLibraryEntity, WorkspaceEntityBase(
  dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val symbolicId: KotlinScriptLibraryEntityId = super.symbolicId

  override val classes: List<VirtualFileUrl>
    get() {
      readField("classes")
      return dataSource.classes
    }

  override val sources: List<VirtualFileUrl>
    get() {
      readField("sources")
      return dataSource.sources
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: KotlinScriptLibraryEntityData?) : ModifiableWorkspaceEntityBase<KotlinScriptLibraryEntity, KotlinScriptLibraryEntityData>(
    result), KotlinScriptLibraryEntity.Builder {
    internal constructor() : this(KotlinScriptLibraryEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity KotlinScriptLibraryEntity is already created in a different builder")
        }
      }

      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      index(this, "classes", this.classes)
      index(this, "sources", this.sources)
      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isClassesInitialized()) {
        error("Field KotlinScriptLibraryEntity#classes should be initialized")
      }
      if (!getEntityData().isSourcesInitialized()) {
        error("Field KotlinScriptLibraryEntity#sources should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_classes = getEntityData().classes
      if (collection_classes is MutableWorkspaceList<*>) {
        collection_classes.cleanModificationUpdateAction()
      }
      val collection_sources = getEntityData().sources
      if (collection_sources is MutableWorkspaceList<*>) {
        collection_sources.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as KotlinScriptLibraryEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.classes != dataSource.classes) this.classes = dataSource.classes.toMutableList()
      if (this.sources != dataSource.sources) this.sources = dataSource.sources.toMutableList()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    private val classesUpdater: (value: List<VirtualFileUrl>) -> Unit = { value ->
      val _diff = diff
      if (_diff != null) index(this, "classes", value)
      changedProperty.add("classes")
    }
    override var classes: MutableList<VirtualFileUrl>
      get() {
        val collection_classes = getEntityData().classes
        if (collection_classes !is MutableWorkspaceList) return collection_classes
        if (diff == null || modifiable.get()) {
          collection_classes.setModificationUpdateAction(classesUpdater)
        }
        else {
          collection_classes.cleanModificationUpdateAction()
        }
        return collection_classes
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).classes = value
        classesUpdater.invoke(value)
      }

    private val sourcesUpdater: (value: List<VirtualFileUrl>) -> Unit = { value ->
      val _diff = diff
      if (_diff != null) index(this, "sources", value)
      changedProperty.add("sources")
    }
    override var sources: MutableList<VirtualFileUrl>
      get() {
        val collection_sources = getEntityData().sources
        if (collection_sources !is MutableWorkspaceList) return collection_sources
        if (diff == null || modifiable.get()) {
          collection_sources.setModificationUpdateAction(sourcesUpdater)
        }
        else {
          collection_sources.cleanModificationUpdateAction()
        }
        return collection_sources
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).sources = value
        sourcesUpdater.invoke(value)
      }

    override fun getEntityClass(): Class<KotlinScriptLibraryEntity> = KotlinScriptLibraryEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class KotlinScriptLibraryEntityData : WorkspaceEntityData<KotlinScriptLibraryEntity>() {
  lateinit var classes: MutableList<VirtualFileUrl>
  lateinit var sources: MutableList<VirtualFileUrl>

  internal fun isClassesInitialized(): Boolean = ::classes.isInitialized
  internal fun isSourcesInitialized(): Boolean = ::sources.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<KotlinScriptLibraryEntity> {
    val modifiable = KotlinScriptLibraryEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): KotlinScriptLibraryEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = KotlinScriptLibraryEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("org.jetbrains.kotlin.idea.KotlinScriptLibraryEntity") as EntityMetadata
  }

  override fun clone(): KotlinScriptLibraryEntityData {
    val clonedEntity = super.clone()
    clonedEntity as KotlinScriptLibraryEntityData
    clonedEntity.classes = clonedEntity.classes.toMutableWorkspaceList()
    clonedEntity.sources = clonedEntity.sources.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return KotlinScriptLibraryEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return KotlinScriptLibraryEntity(classes, sources, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as KotlinScriptLibraryEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.classes != other.classes) return false
    if (this.sources != other.sources) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as KotlinScriptLibraryEntityData

    if (this.classes != other.classes) return false
    if (this.sources != other.sources) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + classes.hashCode()
    result = 31 * result + sources.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + classes.hashCode()
    result = 31 * result + sources.hashCode()
    return result
  }
}
