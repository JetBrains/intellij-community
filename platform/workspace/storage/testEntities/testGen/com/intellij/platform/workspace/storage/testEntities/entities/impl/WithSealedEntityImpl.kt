// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.MySealedClass
import com.intellij.platform.workspace.storage.testEntities.entities.MySealedInterface
import com.intellij.platform.workspace.storage.testEntities.entities.WithSealedEntity

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class WithSealedEntityImpl(private val dataSource: WithSealedEntityData) : WithSealedEntity, WorkspaceEntityBase(dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val classes: List<MySealedClass>
    get() {
      readField("classes")
      return dataSource.classes
    }

  override val interfaces: List<MySealedInterface>
    get() {
      readField("interfaces")
      return dataSource.interfaces
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: WithSealedEntityData?) : ModifiableWorkspaceEntityBase<WithSealedEntity, WithSealedEntityData>(result),
                                                          WithSealedEntity.Builder {
    internal constructor() : this(WithSealedEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity WithSealedEntity is already created in a different builder")
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
      if (!getEntityData().isClassesInitialized()) {
        error("Field WithSealedEntity#classes should be initialized")
      }
      if (!getEntityData().isInterfacesInitialized()) {
        error("Field WithSealedEntity#interfaces should be initialized")
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
      val collection_interfaces = getEntityData().interfaces
      if (collection_interfaces is MutableWorkspaceList<*>) {
        collection_interfaces.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as WithSealedEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.classes != dataSource.classes) this.classes = dataSource.classes.toMutableList()
      if (this.interfaces != dataSource.interfaces) this.interfaces = dataSource.interfaces.toMutableList()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    private val classesUpdater: (value: List<MySealedClass>) -> Unit = { value ->

      changedProperty.add("classes")
    }
    override var classes: MutableList<MySealedClass>
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

    private val interfacesUpdater: (value: List<MySealedInterface>) -> Unit = { value ->

      changedProperty.add("interfaces")
    }
    override var interfaces: MutableList<MySealedInterface>
      get() {
        val collection_interfaces = getEntityData().interfaces
        if (collection_interfaces !is MutableWorkspaceList) return collection_interfaces
        if (diff == null || modifiable.get()) {
          collection_interfaces.setModificationUpdateAction(interfacesUpdater)
        }
        else {
          collection_interfaces.cleanModificationUpdateAction()
        }
        return collection_interfaces
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).interfaces = value
        interfacesUpdater.invoke(value)
      }

    override fun getEntityClass(): Class<WithSealedEntity> = WithSealedEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class WithSealedEntityData : WorkspaceEntityData<WithSealedEntity>() {
  lateinit var classes: MutableList<MySealedClass>
  lateinit var interfaces: MutableList<MySealedInterface>

  internal fun isClassesInitialized(): Boolean = ::classes.isInitialized
  internal fun isInterfacesInitialized(): Boolean = ::interfaces.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<WithSealedEntity> {
    val modifiable = WithSealedEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): WithSealedEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = WithSealedEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.WithSealedEntity"
    ) as EntityMetadata
  }

  override fun clone(): WithSealedEntityData {
    val clonedEntity = super.clone()
    clonedEntity as WithSealedEntityData
    clonedEntity.classes = clonedEntity.classes.toMutableWorkspaceList()
    clonedEntity.interfaces = clonedEntity.interfaces.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return WithSealedEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return WithSealedEntity(classes, interfaces, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as WithSealedEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.classes != other.classes) return false
    if (this.interfaces != other.interfaces) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as WithSealedEntityData

    if (this.classes != other.classes) return false
    if (this.interfaces != other.interfaces) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + classes.hashCode()
    result = 31 * result + interfaces.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + classes.hashCode()
    result = 31 * result + interfaces.hashCode()
    return result
  }
}
