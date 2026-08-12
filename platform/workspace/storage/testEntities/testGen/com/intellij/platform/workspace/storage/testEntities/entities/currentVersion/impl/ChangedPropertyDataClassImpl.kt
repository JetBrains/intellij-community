// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.impl

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
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedPropertyDataClass
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedPropertyDataClassBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SpecialDataClass

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ChangedPropertyDataClassImpl(private val dataSource: ChangedPropertyDataClassData) : ChangedPropertyDataClass,
                                                                                                    WorkspaceEntityBase(dataSource) {

  override val text: String
    get() {
      readField("text")
      return dataSource.text
    }
  override val propertyToChange: SpecialDataClass
    get() {
      readField("propertyToChange")
      return dataSource.propertyToChange
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return emptyList()
  }

  internal class Builder(result: ChangedPropertyDataClassData?) :
    ModifiableWorkspaceEntityBase<ChangedPropertyDataClass, ChangedPropertyDataClassData>(result), ChangedPropertyDataClassBuilder {
    internal constructor() : this(ChangedPropertyDataClassData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isTextInitialized()) {
        error("Field ChangedPropertyDataClass#text should be initialized")
      }
      if (!getEntityData().isPropertyToChangeInitialized()) {
        error("Field ChangedPropertyDataClass#propertyToChange should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return emptyList()
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ChangedPropertyDataClass
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.text != dataSource.text) this.text = dataSource.text
      if (this.propertyToChange != dataSource.propertyToChange) this.propertyToChange = dataSource.propertyToChange
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var text: String
      get() = getEntityData().text
      set(value) {
        checkModificationAllowed()
        getEntityData(true).text = value
        changedProperty.add("text")
      }
    override var propertyToChange: SpecialDataClass
      get() = getEntityData().propertyToChange
      set(value) {
        checkModificationAllowed()
        getEntityData(true).propertyToChange = value
        changedProperty.add("propertyToChange")
      }

    override fun getEntityClass(): Class<ChangedPropertyDataClass> = ChangedPropertyDataClass::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ChangedPropertyDataClassData : WorkspaceEntityData<ChangedPropertyDataClass>() {
  lateinit var text: String
  lateinit var propertyToChange: SpecialDataClass
  internal fun isTextInitialized(): Boolean = ::text.isInitialized
  internal fun isPropertyToChangeInitialized(): Boolean = ::propertyToChange.isInitialized
  override fun newInstance(): ChangedPropertyDataClass = ChangedPropertyDataClassImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<ChangedPropertyDataClass, *> = ChangedPropertyDataClassImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedPropertyDataClass") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ChangedPropertyDataClass::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ChangedPropertyDataClass(text, propertyToChange, entitySource)
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ChangedPropertyDataClassData
    if (this.entitySource != other.entitySource) return false
    if (this.text != other.text) return false
    if (this.propertyToChange != other.propertyToChange) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as ChangedPropertyDataClassData
    if (this.text != other.text) return false
    if (this.propertyToChange != other.propertyToChange) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + text.hashCode()
    result = 31 * result + propertyToChange.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + text.hashCode()
    result = 31 * result + propertyToChange.hashCode()
    return result
  }
}
