// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.ucache.impl

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceSet
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.io.Serializable
import org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptId
import org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryId
import org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryRoot

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class KotlinScriptLibraryEntityImpl(private val dataSource: KotlinScriptLibraryEntityData) : KotlinScriptLibraryEntity, WorkspaceEntityBase(
  dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val symbolicId: KotlinScriptLibraryId = super.symbolicId

  override val name: String
    get() {
      readField("name")
      return dataSource.name
    }

  override val roots: List<KotlinScriptLibraryRoot>
    get() {
      readField("roots")
      return dataSource.roots
    }

  override val indexSourceRoots: Boolean
    get() {
      readField("indexSourceRoots")
      return dataSource.indexSourceRoots
    }
  override val usedInScripts: Set<KotlinScriptId>
    get() {
      readField("usedInScripts")
      return dataSource.usedInScripts
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

      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isNameInitialized()) {
        error("Field KotlinScriptLibraryEntity#name should be initialized")
      }
      if (!getEntityData().isRootsInitialized()) {
        error("Field KotlinScriptLibraryEntity#roots should be initialized")
      }
      if (!getEntityData().isUsedInScriptsInitialized()) {
        error("Field KotlinScriptLibraryEntity#usedInScripts should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_roots = getEntityData().roots
      if (collection_roots is MutableWorkspaceList<*>) {
        collection_roots.cleanModificationUpdateAction()
      }
      val collection_usedInScripts = getEntityData().usedInScripts
      if (collection_usedInScripts is MutableWorkspaceSet<*>) {
        collection_usedInScripts.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as KotlinScriptLibraryEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.roots != dataSource.roots) this.roots = dataSource.roots.toMutableList()
      if (this.indexSourceRoots != dataSource.indexSourceRoots) this.indexSourceRoots = dataSource.indexSourceRoots
      if (this.usedInScripts != dataSource.usedInScripts) this.usedInScripts = dataSource.usedInScripts.toMutableSet()
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

    private val rootsUpdater: (value: List<KotlinScriptLibraryRoot>) -> Unit = { value ->

      changedProperty.add("roots")
    }
    override var roots: MutableList<KotlinScriptLibraryRoot>
      get() {
        val collection_roots = getEntityData().roots
        if (collection_roots !is MutableWorkspaceList) return collection_roots
        if (diff == null || modifiable.get()) {
          collection_roots.setModificationUpdateAction(rootsUpdater)
        }
        else {
          collection_roots.cleanModificationUpdateAction()
        }
        return collection_roots
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).roots = value
        rootsUpdater.invoke(value)
      }

    override var indexSourceRoots: Boolean
      get() = getEntityData().indexSourceRoots
      set(value) {
        checkModificationAllowed()
        getEntityData(true).indexSourceRoots = value
        changedProperty.add("indexSourceRoots")
      }

    private val usedInScriptsUpdater: (value: Set<KotlinScriptId>) -> Unit = { value ->

      changedProperty.add("usedInScripts")
    }
    override var usedInScripts: MutableSet<KotlinScriptId>
      get() {
        val collection_usedInScripts = getEntityData().usedInScripts
        if (collection_usedInScripts !is MutableWorkspaceSet) return collection_usedInScripts
        if (diff == null || modifiable.get()) {
          collection_usedInScripts.setModificationUpdateAction(usedInScriptsUpdater)
        }
        else {
          collection_usedInScripts.cleanModificationUpdateAction()
        }
        return collection_usedInScripts
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).usedInScripts = value
        usedInScriptsUpdater.invoke(value)
      }

    override fun getEntityClass(): Class<KotlinScriptLibraryEntity> = KotlinScriptLibraryEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class KotlinScriptLibraryEntityData : WorkspaceEntityData<KotlinScriptLibraryEntity>(), SoftLinkable {
  lateinit var name: String
  lateinit var roots: MutableList<KotlinScriptLibraryRoot>
  var indexSourceRoots: Boolean = false
  lateinit var usedInScripts: MutableSet<KotlinScriptId>

  internal fun isNameInitialized(): Boolean = ::name.isInitialized
  internal fun isRootsInitialized(): Boolean = ::roots.isInitialized

  internal fun isUsedInScriptsInitialized(): Boolean = ::usedInScripts.isInitialized

  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    for (item in roots) {
    }
    for (item in usedInScripts) {
      result.add(item)
    }
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    for (item in roots) {
    }
    for (item in usedInScripts) {
      index.index(this, item)
    }
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    // TODO verify logic
    val mutablePreviousSet = HashSet(prev)
    for (item in roots) {
    }
    for (item in usedInScripts) {
      val removedItem_item = mutablePreviousSet.remove(item)
      if (!removedItem_item) {
        index.index(this, item)
      }
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
    }
  }

  override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
    var changed = false
    val usedInScripts_data = usedInScripts.map {
      val it_data = if (it == oldLink) {
        changed = true
        newLink as KotlinScriptId
      }
      else {
        null
      }
      if (it_data != null) {
        it_data
      }
      else {
        it
      }
    }
    if (usedInScripts_data != null) {
      usedInScripts = usedInScripts_data as MutableSet<KotlinScriptId>
    }
    return changed
  }

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
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryEntity") as EntityMetadata
  }

  override fun clone(): KotlinScriptLibraryEntityData {
    val clonedEntity = super.clone()
    clonedEntity as KotlinScriptLibraryEntityData
    clonedEntity.roots = clonedEntity.roots.toMutableWorkspaceList()
    clonedEntity.usedInScripts = clonedEntity.usedInScripts.toMutableWorkspaceSet()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return KotlinScriptLibraryEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return KotlinScriptLibraryEntity(name, roots, indexSourceRoots, usedInScripts, entitySource) {
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
    if (this.name != other.name) return false
    if (this.roots != other.roots) return false
    if (this.indexSourceRoots != other.indexSourceRoots) return false
    if (this.usedInScripts != other.usedInScripts) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as KotlinScriptLibraryEntityData

    if (this.name != other.name) return false
    if (this.roots != other.roots) return false
    if (this.indexSourceRoots != other.indexSourceRoots) return false
    if (this.usedInScripts != other.usedInScripts) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + roots.hashCode()
    result = 31 * result + indexSourceRoots.hashCode()
    result = 31 * result + usedInScripts.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + roots.hashCode()
    result = 31 * result + indexSourceRoots.hashCode()
    result = 31 * result + usedInScripts.hashCode()
    return result
  }
}
