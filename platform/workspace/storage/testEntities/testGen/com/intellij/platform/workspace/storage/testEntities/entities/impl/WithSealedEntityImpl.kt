// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.storage.testEntities.entities.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.MySealedClass
import com.intellij.platform.workspace.storage.testEntities.entities.MySealedInterface
import com.intellij.platform.workspace.storage.testEntities.entities.WithSealedEntity
import com.intellij.platform.workspace.storage.testEntities.entities.WithSealedEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class WithSealedEntityImpl(private val dataSource: WithSealedEntityData) : WithSealedEntity, WorkspaceEntityBase(dataSource) {

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
    return emptyList()
  }

  internal class Builder(result: WithSealedEntityData?) : ModifiableWorkspaceEntityBase<WithSealedEntity, WithSealedEntityData>(result),
                                                          WithSealedEntityBuilder {
    internal constructor() : this(WithSealedEntityData())

    override fun checkInitialization() {
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
      return emptyList()
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
  override fun newInstance(): WithSealedEntity = WithSealedEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<WithSealedEntity, *> = WithSealedEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.WithSealedEntity") as EntityMetadata
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

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return WithSealedEntity(classes, interfaces, entitySource)
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
