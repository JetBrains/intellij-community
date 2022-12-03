// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.SymbolicEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityWithSymbolicId
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.UsedClassesCollector
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.containers.MutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import java.io.Serializable
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class KotlinScriptLibraryEntityImpl(val dataSource: KotlinScriptLibraryEntityData) : KotlinScriptLibraryEntity, WorkspaceEntityBase() {

  companion object {


    val connections = listOf<ConnectionId>(
    )

  }

  override val name: String
    get() = dataSource.name

  override val roots: List<KotlinScriptLibraryRoot>
    get() = dataSource.roots

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: KotlinScriptLibraryEntityData?) : ModifiableWorkspaceEntityBase<KotlinScriptLibraryEntity, KotlinScriptLibraryEntityData>(
    result), KotlinScriptLibraryEntity.Builder {
    constructor() : this(KotlinScriptLibraryEntityData())

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
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()

      // DON'T_REMOVE_AT_CODE_GENERATION: start (see IDEA-305887)
      indexLibraryRoots(roots)
      // DON'T_REMOVE_AT_CODE_GENERATION: end

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
      if (!getEntityData().isNameInitialized()) {
        error("Field KotlinScriptLibraryEntity#name should be initialized")
      }
      if (!getEntityData().isRootsInitialized()) {
        error("Field KotlinScriptLibraryEntity#roots should be initialized")
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
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as KotlinScriptLibraryEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.roots != dataSource.roots) this.roots = dataSource.roots.toMutableList()
      updateChildToParentReferences(parents)
    }

    // DON'T_REMOVE_AT_CODE_GENERATION: start (see IDEA-305887)
    private fun indexLibraryRoots(libraryRoots: List<KotlinScriptLibraryRoot>) {
        val jarDirectories = mutableSetOf<VirtualFileUrl>()
        val libraryRootList = libraryRoots.map {
            it.url
        }.toHashSet()
        index(this, "roots", libraryRootList)
        indexJarDirectories(this, jarDirectories)
    }
    // DON'T_REMOVE_AT_CODE_GENERATION: end

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

    override fun getEntityClass(): Class<KotlinScriptLibraryEntity> = KotlinScriptLibraryEntity::class.java
  }
}

class KotlinScriptLibraryEntityData : WorkspaceEntityData.WithCalculableSymbolicId<KotlinScriptLibraryEntity>() {
  lateinit var name: String
  lateinit var roots: MutableList<KotlinScriptLibraryRoot>

  fun isNameInitialized(): Boolean = ::name.isInitialized
  fun isRootsInitialized(): Boolean = ::roots.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<KotlinScriptLibraryEntity> {
    val modifiable = KotlinScriptLibraryEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): KotlinScriptLibraryEntity {
    return getCached(snapshot) {
      val entity = KotlinScriptLibraryEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun clone(): KotlinScriptLibraryEntityData {
    val clonedEntity = super.clone()
    clonedEntity as KotlinScriptLibraryEntityData
    clonedEntity.roots = clonedEntity.roots.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun symbolicId(): SymbolicEntityId<*> {
    return KotlinScriptLibraryId(name)
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return KotlinScriptLibraryEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return KotlinScriptLibraryEntity(name, roots, entitySource) {
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
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as KotlinScriptLibraryEntityData

    if (this.name != other.name) return false
    if (this.roots != other.roots) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + roots.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + roots.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.add(KotlinScriptLibraryRootTypeId::class.java)
    collector.add(KotlinScriptLibraryRoot::class.java)
    this.roots?.let { collector.add(it::class.java) }
    collector.sameForAllEntities = false
  }
}
