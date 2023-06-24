// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.openapi.util.NlsSafe
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
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.UsedClassesCollector
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.extractOneToManyChildren
import com.intellij.platform.workspace.storage.impl.extractOneToOneChild
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.platform.workspace.storage.impl.updateOneToOneChildOfParent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.io.Serializable
import org.jetbrains.annotations.NonNls

@GeneratedCodeApiVersion(2)
@GeneratedCodeImplVersion(2)
open class LibraryEntityImpl(val dataSource: LibraryEntityData) : LibraryEntity, WorkspaceEntityBase() {

  companion object {
    internal val EXCLUDEDROOTS_CONNECTION_ID: ConnectionId = ConnectionId.create(LibraryEntity::class.java, ExcludeUrlEntity::class.java,
                                                                                 ConnectionId.ConnectionType.ONE_TO_MANY, true)
    internal val SDK_CONNECTION_ID: ConnectionId = ConnectionId.create(LibraryEntity::class.java, SdkEntity::class.java,
                                                                       ConnectionId.ConnectionType.ONE_TO_ONE, false)
    internal val LIBRARYPROPERTIES_CONNECTION_ID: ConnectionId = ConnectionId.create(LibraryEntity::class.java,
                                                                                     LibraryPropertiesEntity::class.java,
                                                                                     ConnectionId.ConnectionType.ONE_TO_ONE, false)

    val connections = listOf<ConnectionId>(
      EXCLUDEDROOTS_CONNECTION_ID,
      SDK_CONNECTION_ID,
      LIBRARYPROPERTIES_CONNECTION_ID,
    )

  }

  override val name: String
    get() = dataSource.name

  override val tableId: LibraryTableId
    get() = dataSource.tableId

  override val roots: List<LibraryRoot>
    get() = dataSource.roots

  override val excludedRoots: List<ExcludeUrlEntity>
    get() = snapshot.extractOneToManyChildren<ExcludeUrlEntity>(EXCLUDEDROOTS_CONNECTION_ID, this)!!.toList()

  override val sdk: SdkEntity?
    get() = snapshot.extractOneToOneChild(SDK_CONNECTION_ID, this)

  override val libraryProperties: LibraryPropertiesEntity?
    get() = snapshot.extractOneToOneChild(LIBRARYPROPERTIES_CONNECTION_ID, this)

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: LibraryEntityData?) : ModifiableWorkspaceEntityBase<LibraryEntity, LibraryEntityData>(
    result), LibraryEntity.Builder {
    constructor() : this(LibraryEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity LibraryEntity is already created in a different builder")
        }
      }

      this.diff = builder
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      indexLibraryRoots(roots)
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
        error("Field LibraryEntity#name should be initialized")
      }
      if (!getEntityData().isTableIdInitialized()) {
        error("Field LibraryEntity#tableId should be initialized")
      }
      if (!getEntityData().isRootsInitialized()) {
        error("Field LibraryEntity#roots should be initialized")
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(EXCLUDEDROOTS_CONNECTION_ID, this) == null) {
          error("Field LibraryEntity#excludedRoots should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, EXCLUDEDROOTS_CONNECTION_ID)] == null) {
          error("Field LibraryEntity#excludedRoots should be initialized")
        }
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
      dataSource as LibraryEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.tableId != dataSource.tableId) this.tableId = dataSource.tableId
      if (this.roots != dataSource.roots) this.roots = dataSource.roots.toMutableList()
      updateChildToParentReferences(parents)
    }

    private fun indexLibraryRoots(libraryRoots: List<LibraryRoot>) {
      val jarDirectories = mutableSetOf<VirtualFileUrl>()
      val libraryRootList = libraryRoots.map {
        if (it.inclusionOptions != LibraryRoot.InclusionOptions.ROOT_ITSELF) {
          jarDirectories.add(it.url)
        }
        it.url
      }.toHashSet()
      index(this, "roots", libraryRootList)
      indexJarDirectories(this, jarDirectories)
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

    override var tableId: LibraryTableId
      get() = getEntityData().tableId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).tableId = value
        changedProperty.add("tableId")

      }

    private val rootsUpdater: (value: List<LibraryRoot>) -> Unit = { value ->

      val _diff = diff
      if (_diff != null) {
        indexLibraryRoots(value)
      }

      changedProperty.add("roots")
    }
    override var roots: MutableList<LibraryRoot>
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

    // List of non-abstract referenced types
    var _excludedRoots: List<ExcludeUrlEntity>? = emptyList()
    override var excludedRoots: List<ExcludeUrlEntity>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyChildren<ExcludeUrlEntity>(EXCLUDEDROOTS_CONNECTION_ID, this)!!.toList() + (this.entityLinks[EntityLink(
            true, EXCLUDEDROOTS_CONNECTION_ID)] as? List<ExcludeUrlEntity> ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, EXCLUDEDROOTS_CONNECTION_ID)] as? List<ExcludeUrlEntity> ?: emptyList()
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
                item_value.entityLinks[EntityLink(false, EXCLUDEDROOTS_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable

              _diff.addEntity(item_value)
            }
          }
          _diff.updateOneToManyChildrenOfParent(EXCLUDEDROOTS_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, EXCLUDEDROOTS_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
          }

          this.entityLinks[EntityLink(true, EXCLUDEDROOTS_CONNECTION_ID)] = value
        }
        changedProperty.add("excludedRoots")
      }

    override var sdk: SdkEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToOneChild(SDK_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true, SDK_CONNECTION_ID)] as? SdkEntity
        }
        else {
          this.entityLinks[EntityLink(true, SDK_CONNECTION_ID)] as? SdkEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, SDK_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(SDK_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, SDK_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true, SDK_CONNECTION_ID)] = value
        }
        changedProperty.add("sdk")
      }

    override var libraryProperties: LibraryPropertiesEntity?
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToOneChild(LIBRARYPROPERTIES_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(true,
                                                                                                           LIBRARYPROPERTIES_CONNECTION_ID)] as? LibraryPropertiesEntity
        }
        else {
          this.entityLinks[EntityLink(true, LIBRARYPROPERTIES_CONNECTION_ID)] as? LibraryPropertiesEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, LIBRARYPROPERTIES_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneChildOfParent(LIBRARYPROPERTIES_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(false, LIBRARYPROPERTIES_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(true, LIBRARYPROPERTIES_CONNECTION_ID)] = value
        }
        changedProperty.add("libraryProperties")
      }

    override fun getEntityClass(): Class<LibraryEntity> = LibraryEntity::class.java
  }
}

class LibraryEntityData : WorkspaceEntityData.WithCalculableSymbolicId<LibraryEntity>(), SoftLinkable {
  lateinit var name: String
  lateinit var tableId: LibraryTableId
  lateinit var roots: MutableList<LibraryRoot>

  fun isNameInitialized(): Boolean = ::name.isInitialized
  fun isTableIdInitialized(): Boolean = ::tableId.isInitialized
  fun isRootsInitialized(): Boolean = ::roots.isInitialized

  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    val _tableId = tableId
    when (_tableId) {
      is LibraryTableId.GlobalLibraryTableId -> {
      }
      is LibraryTableId.ModuleLibraryTableId -> {
        result.add(_tableId.moduleId)
      }
      is LibraryTableId.ProjectLibraryTableId -> {
      }
    }
    for (item in roots) {
    }
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    val _tableId = tableId
    when (_tableId) {
      is LibraryTableId.GlobalLibraryTableId -> {
      }
      is LibraryTableId.ModuleLibraryTableId -> {
        index.index(this, _tableId.moduleId)
      }
      is LibraryTableId.ProjectLibraryTableId -> {
      }
    }
    for (item in roots) {
    }
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    // TODO verify logic
    val mutablePreviousSet = HashSet(prev)
    val _tableId = tableId
    when (_tableId) {
      is LibraryTableId.GlobalLibraryTableId -> {
      }
      is LibraryTableId.ModuleLibraryTableId -> {
        val removedItem__tableId_moduleId = mutablePreviousSet.remove(_tableId.moduleId)
        if (!removedItem__tableId_moduleId) {
          index.index(this, _tableId.moduleId)
        }
      }
      is LibraryTableId.ProjectLibraryTableId -> {
      }
    }
    for (item in roots) {
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
    }
  }

  override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
    var changed = false
    val _tableId = tableId
    val res_tableId = when (_tableId) {
      is LibraryTableId.GlobalLibraryTableId -> {
        _tableId
      }
      is LibraryTableId.ModuleLibraryTableId -> {
        val _tableId_moduleId_data = if (_tableId.moduleId == oldLink) {
          changed = true
          newLink as ModuleId
        }
        else {
          null
        }
        var _tableId_data = _tableId
        if (_tableId_moduleId_data != null) {
          _tableId_data = _tableId_data.copy(moduleId = _tableId_moduleId_data)
        }
        _tableId_data
      }
      is LibraryTableId.ProjectLibraryTableId -> {
        _tableId
      }
    }
    if (res_tableId != null) {
      tableId = res_tableId
    }
    return changed
  }

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<LibraryEntity> {
    val modifiable = LibraryEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): LibraryEntity {
    return getCached(snapshot) {
      val entity = LibraryEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun clone(): LibraryEntityData {
    val clonedEntity = super.clone()
    clonedEntity as LibraryEntityData
    clonedEntity.roots = clonedEntity.roots.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun symbolicId(): SymbolicEntityId<*> {
    return LibraryId(name, tableId)
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return LibraryEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return LibraryEntity(name, tableId, roots, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as LibraryEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.name != other.name) return false
    if (this.tableId != other.tableId) return false
    if (this.roots != other.roots) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as LibraryEntityData

    if (this.name != other.name) return false
    if (this.tableId != other.tableId) return false
    if (this.roots != other.roots) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + tableId.hashCode()
    result = 31 * result + roots.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + tableId.hashCode()
    result = 31 * result + roots.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.add(LibraryRoot::class.java)
    collector.add(LibraryTableId.ModuleLibraryTableId::class.java)
    collector.add(LibraryTableId::class.java)
    collector.add(LibraryTableId.GlobalLibraryTableId::class.java)
    collector.add(LibraryRootTypeId::class.java)
    collector.add(ModuleId::class.java)
    collector.add(LibraryRoot.InclusionOptions::class.java)
    collector.addObject(LibraryTableId.ProjectLibraryTableId::class.java)
    this.roots?.let { collector.add(it::class.java) }
    collector.sameForAllEntities = false
  }
}
