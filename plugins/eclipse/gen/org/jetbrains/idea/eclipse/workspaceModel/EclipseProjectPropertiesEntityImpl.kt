// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.config

import com.intellij.platform.workspace.jps.JpsFileDependentEntitySource
import com.intellij.platform.workspace.jps.JpsFileEntitySource
import com.intellij.platform.workspace.jps.JpsProjectConfigLocation
import com.intellij.platform.workspace.jps.JpsProjectFileEntitySource
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityInformation
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.UsedClassesCollector
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.extractOneToOneParent
import com.intellij.platform.workspace.storage.impl.updateOneToOneParentOfChild
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(2)
@GeneratedCodeImplVersion(2)
open class EclipseProjectPropertiesEntityImpl(val dataSource: EclipseProjectPropertiesEntityData) : EclipseProjectPropertiesEntity, WorkspaceEntityBase() {

  companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java,
                                                                          EclipseProjectPropertiesEntity::class.java,
                                                                          ConnectionId.ConnectionType.ONE_TO_ONE, false)

    val connections = listOf<ConnectionId>(
      MODULE_CONNECTION_ID,
    )

  }

  override val module: ModuleEntity
    get() = snapshot.extractOneToOneParent(MODULE_CONNECTION_ID, this)!!

  override val variablePaths: Map<String, String>
    get() = dataSource.variablePaths
  override val eclipseUrls: List<VirtualFileUrl>
    get() = dataSource.eclipseUrls

  override val unknownCons: List<String>
    get() = dataSource.unknownCons

  override val knownCons: List<String>
    get() = dataSource.knownCons

  override val forceConfigureJdk: Boolean get() = dataSource.forceConfigureJdk
  override val expectedModuleSourcePlace: Int get() = dataSource.expectedModuleSourcePlace
  override val srcPlace: Map<String, Int>
    get() = dataSource.srcPlace

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: EclipseProjectPropertiesEntityData?) : ModifiableWorkspaceEntityBase<EclipseProjectPropertiesEntity, EclipseProjectPropertiesEntityData>(
    result), EclipseProjectPropertiesEntity.Builder {
    constructor() : this(EclipseProjectPropertiesEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity EclipseProjectPropertiesEntity is already created in a different builder")
        }
      }

      this.diff = builder
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      index(this, "eclipseUrls", this.eclipseUrls)
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
        if (_diff.extractOneToOneParent<WorkspaceEntityBase>(MODULE_CONNECTION_ID, this) == null) {
          error("Field EclipseProjectPropertiesEntity#module should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null) {
          error("Field EclipseProjectPropertiesEntity#module should be initialized")
        }
      }
      if (!getEntityData().isVariablePathsInitialized()) {
        error("Field EclipseProjectPropertiesEntity#variablePaths should be initialized")
      }
      if (!getEntityData().isEclipseUrlsInitialized()) {
        error("Field EclipseProjectPropertiesEntity#eclipseUrls should be initialized")
      }
      if (!getEntityData().isUnknownConsInitialized()) {
        error("Field EclipseProjectPropertiesEntity#unknownCons should be initialized")
      }
      if (!getEntityData().isKnownConsInitialized()) {
        error("Field EclipseProjectPropertiesEntity#knownCons should be initialized")
      }
      if (!getEntityData().isSrcPlaceInitialized()) {
        error("Field EclipseProjectPropertiesEntity#srcPlace should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_eclipseUrls = getEntityData().eclipseUrls
      if (collection_eclipseUrls is MutableWorkspaceList<*>) {
        collection_eclipseUrls.cleanModificationUpdateAction()
      }
      val collection_unknownCons = getEntityData().unknownCons
      if (collection_unknownCons is MutableWorkspaceList<*>) {
        collection_unknownCons.cleanModificationUpdateAction()
      }
      val collection_knownCons = getEntityData().knownCons
      if (collection_knownCons is MutableWorkspaceList<*>) {
        collection_knownCons.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as EclipseProjectPropertiesEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.variablePaths != dataSource.variablePaths) this.variablePaths = dataSource.variablePaths.toMutableMap()
      if (this.eclipseUrls != dataSource.eclipseUrls) this.eclipseUrls = dataSource.eclipseUrls.toMutableList()
      if (this.unknownCons != dataSource.unknownCons) this.unknownCons = dataSource.unknownCons.toMutableList()
      if (this.knownCons != dataSource.knownCons) this.knownCons = dataSource.knownCons.toMutableList()
      if (this.forceConfigureJdk != dataSource.forceConfigureJdk) this.forceConfigureJdk = dataSource.forceConfigureJdk
      if (this.expectedModuleSourcePlace != dataSource.expectedModuleSourcePlace) this.expectedModuleSourcePlace = dataSource.expectedModuleSourcePlace
      if (this.srcPlace != dataSource.srcPlace) this.srcPlace = dataSource.srcPlace.toMutableMap()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var module: ModuleEntity
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToOneParent(MODULE_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                 MODULE_CONNECTION_ID)]!! as ModuleEntity
        }
        else {
          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneParentOfChild(MODULE_CONNECTION_ID, this, value)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] = value
        }
        changedProperty.add("module")
      }

    override var variablePaths: Map<String, String>
      get() = getEntityData().variablePaths
      set(value) {
        checkModificationAllowed()
        getEntityData(true).variablePaths = value
        changedProperty.add("variablePaths")
      }

    private val eclipseUrlsUpdater: (value: List<VirtualFileUrl>) -> Unit = { value ->
      val _diff = diff
      if (_diff != null) index(this, "eclipseUrls", value)
      changedProperty.add("eclipseUrls")
    }
    override var eclipseUrls: MutableList<VirtualFileUrl>
      get() {
        val collection_eclipseUrls = getEntityData().eclipseUrls
        if (collection_eclipseUrls !is MutableWorkspaceList) return collection_eclipseUrls
        if (diff == null || modifiable.get()) {
          collection_eclipseUrls.setModificationUpdateAction(eclipseUrlsUpdater)
        }
        else {
          collection_eclipseUrls.cleanModificationUpdateAction()
        }
        return collection_eclipseUrls
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).eclipseUrls = value
        eclipseUrlsUpdater.invoke(value)
      }

    private val unknownConsUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("unknownCons")
    }
    override var unknownCons: MutableList<String>
      get() {
        val collection_unknownCons = getEntityData().unknownCons
        if (collection_unknownCons !is MutableWorkspaceList) return collection_unknownCons
        if (diff == null || modifiable.get()) {
          collection_unknownCons.setModificationUpdateAction(unknownConsUpdater)
        }
        else {
          collection_unknownCons.cleanModificationUpdateAction()
        }
        return collection_unknownCons
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).unknownCons = value
        unknownConsUpdater.invoke(value)
      }

    private val knownConsUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("knownCons")
    }
    override var knownCons: MutableList<String>
      get() {
        val collection_knownCons = getEntityData().knownCons
        if (collection_knownCons !is MutableWorkspaceList) return collection_knownCons
        if (diff == null || modifiable.get()) {
          collection_knownCons.setModificationUpdateAction(knownConsUpdater)
        }
        else {
          collection_knownCons.cleanModificationUpdateAction()
        }
        return collection_knownCons
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).knownCons = value
        knownConsUpdater.invoke(value)
      }

    override var forceConfigureJdk: Boolean
      get() = getEntityData().forceConfigureJdk
      set(value) {
        checkModificationAllowed()
        getEntityData(true).forceConfigureJdk = value
        changedProperty.add("forceConfigureJdk")
      }

    override var expectedModuleSourcePlace: Int
      get() = getEntityData().expectedModuleSourcePlace
      set(value) {
        checkModificationAllowed()
        getEntityData(true).expectedModuleSourcePlace = value
        changedProperty.add("expectedModuleSourcePlace")
      }

    override var srcPlace: Map<String, Int>
      get() = getEntityData().srcPlace
      set(value) {
        checkModificationAllowed()
        getEntityData(true).srcPlace = value
        changedProperty.add("srcPlace")
      }

    override fun getEntityClass(): Class<EclipseProjectPropertiesEntity> = EclipseProjectPropertiesEntity::class.java
  }
}

class EclipseProjectPropertiesEntityData : WorkspaceEntityData<EclipseProjectPropertiesEntity>() {
  lateinit var variablePaths: Map<String, String>
  lateinit var eclipseUrls: MutableList<VirtualFileUrl>
  lateinit var unknownCons: MutableList<String>
  lateinit var knownCons: MutableList<String>
  var forceConfigureJdk: Boolean = false
  var expectedModuleSourcePlace: Int = 0
  lateinit var srcPlace: Map<String, Int>

  fun isVariablePathsInitialized(): Boolean = ::variablePaths.isInitialized
  fun isEclipseUrlsInitialized(): Boolean = ::eclipseUrls.isInitialized
  fun isUnknownConsInitialized(): Boolean = ::unknownCons.isInitialized
  fun isKnownConsInitialized(): Boolean = ::knownCons.isInitialized


  fun isSrcPlaceInitialized(): Boolean = ::srcPlace.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<EclipseProjectPropertiesEntity> {
    val modifiable = EclipseProjectPropertiesEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): EclipseProjectPropertiesEntity {
    return getCached(snapshot) {
      val entity = EclipseProjectPropertiesEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun clone(): EclipseProjectPropertiesEntityData {
    val clonedEntity = super.clone()
    clonedEntity as EclipseProjectPropertiesEntityData
    clonedEntity.eclipseUrls = clonedEntity.eclipseUrls.toMutableWorkspaceList()
    clonedEntity.unknownCons = clonedEntity.unknownCons.toMutableWorkspaceList()
    clonedEntity.knownCons = clonedEntity.knownCons.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return EclipseProjectPropertiesEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return EclipseProjectPropertiesEntity(variablePaths, eclipseUrls, unknownCons, knownCons, forceConfigureJdk, expectedModuleSourcePlace,
                                          srcPlace, entitySource) {
      parents.filterIsInstance<ModuleEntity>().singleOrNull()?.let { this.module = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ModuleEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as EclipseProjectPropertiesEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.variablePaths != other.variablePaths) return false
    if (this.eclipseUrls != other.eclipseUrls) return false
    if (this.unknownCons != other.unknownCons) return false
    if (this.knownCons != other.knownCons) return false
    if (this.forceConfigureJdk != other.forceConfigureJdk) return false
    if (this.expectedModuleSourcePlace != other.expectedModuleSourcePlace) return false
    if (this.srcPlace != other.srcPlace) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as EclipseProjectPropertiesEntityData

    if (this.variablePaths != other.variablePaths) return false
    if (this.eclipseUrls != other.eclipseUrls) return false
    if (this.unknownCons != other.unknownCons) return false
    if (this.knownCons != other.knownCons) return false
    if (this.forceConfigureJdk != other.forceConfigureJdk) return false
    if (this.expectedModuleSourcePlace != other.expectedModuleSourcePlace) return false
    if (this.srcPlace != other.srcPlace) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + variablePaths.hashCode()
    result = 31 * result + eclipseUrls.hashCode()
    result = 31 * result + unknownCons.hashCode()
    result = 31 * result + knownCons.hashCode()
    result = 31 * result + forceConfigureJdk.hashCode()
    result = 31 * result + expectedModuleSourcePlace.hashCode()
    result = 31 * result + srcPlace.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + variablePaths.hashCode()
    result = 31 * result + eclipseUrls.hashCode()
    result = 31 * result + unknownCons.hashCode()
    result = 31 * result + knownCons.hashCode()
    result = 31 * result + forceConfigureJdk.hashCode()
    result = 31 * result + expectedModuleSourcePlace.hashCode()
    result = 31 * result + srcPlace.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    this.knownCons?.let { collector.add(it::class.java) }
    this.eclipseUrls?.let { collector.add(it::class.java) }
    this.unknownCons?.let { collector.add(it::class.java) }
    this.variablePaths?.let { collector.add(it::class.java) }
    this.srcPlace?.let { collector.add(it::class.java) }
    collector.sameForAllEntities = false
  }
}
