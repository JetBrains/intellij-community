// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:OptIn(EntityStorageInstrumentationApi::class)

package org.jetbrains.kotlin.idea.core.script.k2.definitions.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionTemplateEntity
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionTemplateEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ScriptDefinitionTemplateEntityImpl(private val dataSource: ScriptDefinitionTemplateEntityData) :
    ScriptDefinitionTemplateEntity, WorkspaceEntityBase(dataSource) {

    private companion object {

        private val connections = listOf<ConnectionId>()

    }

    override val templateFqns: List<String>
        get() {
            readField("templateFqns")
            return dataSource.templateFqns
        }
    override val classpath: List<String>
        get() {
            readField("classpath")
            return dataSource.classpath
        }

    override val entitySource: EntitySource
        get() {
            readField("entitySource")
            return dataSource.entitySource
        }

    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }


    internal class Builder(result: ScriptDefinitionTemplateEntityData?) :
        ModifiableWorkspaceEntityBase<ScriptDefinitionTemplateEntity, ScriptDefinitionTemplateEntityData>(result),
        ScriptDefinitionTemplateEntityBuilder {
        internal constructor() : this(ScriptDefinitionTemplateEntityData())

        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                } else {
                    error("Entity ScriptDefinitionTemplateEntity is already created in a different builder")
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
            if (!getEntityData().isTemplateFqnsInitialized()) {
                error("Field ScriptDefinitionTemplateEntity#templateFqns should be initialized")
            }
            if (!getEntityData().isClasspathInitialized()) {
                error("Field ScriptDefinitionTemplateEntity#classpath should be initialized")
            }
        }

        override fun connectionIdList(): List<ConnectionId> {
            return connections
        }

        override fun afterModification() {
            val collection_templateFqns = getEntityData().templateFqns
            if (collection_templateFqns is MutableWorkspaceList<*>) {
                collection_templateFqns.cleanModificationUpdateAction()
            }
            val collection_classpath = getEntityData().classpath
            if (collection_classpath is MutableWorkspaceList<*>) {
                collection_classpath.cleanModificationUpdateAction()
            }
        }

        // Relabeling code, move information from dataSource to this builder
        override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
            dataSource as ScriptDefinitionTemplateEntity
            if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
            if (this.templateFqns != dataSource.templateFqns) this.templateFqns = dataSource.templateFqns.toMutableList()
            if (this.classpath != dataSource.classpath) this.classpath = dataSource.classpath.toMutableList()
            updateChildToParentReferences(parents)
        }


        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData(true).entitySource = value
                changedProperty.add("entitySource")

            }
        private val templateFqnsUpdater: (value: List<String>) -> Unit = { value ->

            changedProperty.add("templateFqns")
        }
        override var templateFqns: MutableList<String>
            get() {
                val collection_templateFqns = getEntityData().templateFqns
                if (collection_templateFqns !is MutableWorkspaceList) return collection_templateFqns
                if (diff == null || modifiable.get()) {
                    collection_templateFqns.setModificationUpdateAction(templateFqnsUpdater)
                } else {
                    collection_templateFqns.cleanModificationUpdateAction()
                }
                return collection_templateFqns
            }
            set(value) {
                checkModificationAllowed()
                getEntityData(true).templateFqns = value
                templateFqnsUpdater.invoke(value)
            }
        private val classpathUpdater: (value: List<String>) -> Unit = { value ->

            changedProperty.add("classpath")
        }
        override var classpath: MutableList<String>
            get() {
                val collection_classpath = getEntityData().classpath
                if (collection_classpath !is MutableWorkspaceList) return collection_classpath
                if (diff == null || modifiable.get()) {
                    collection_classpath.setModificationUpdateAction(classpathUpdater)
                } else {
                    collection_classpath.cleanModificationUpdateAction()
                }
                return collection_classpath
            }
            set(value) {
                checkModificationAllowed()
                getEntityData(true).classpath = value
                classpathUpdater.invoke(value)
            }

        override fun getEntityClass(): Class<ScriptDefinitionTemplateEntity> = ScriptDefinitionTemplateEntity::class.java
    }

}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ScriptDefinitionTemplateEntityData : WorkspaceEntityData<ScriptDefinitionTemplateEntity>() {
    lateinit var templateFqns: MutableList<String>
    lateinit var classpath: MutableList<String>

    internal fun isTemplateFqnsInitialized(): Boolean = ::templateFqns.isInitialized
    internal fun isClasspathInitialized(): Boolean = ::classpath.isInitialized

    override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<ScriptDefinitionTemplateEntity> {
        val modifiable = ScriptDefinitionTemplateEntityImpl.Builder(null)
        modifiable.diff = diff
        modifiable.id = createEntityId()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorageInstrumentation): ScriptDefinitionTemplateEntity {
        val entityId = createEntityId()
        return snapshot.initializeEntity(entityId) {
            val entity = ScriptDefinitionTemplateEntityImpl(this)
            entity.snapshot = snapshot
            entity.id = entityId
            entity
        }
    }

    override fun getMetadata(): EntityMetadata {
        return MetadataStorageImpl.getMetadataByTypeFqn("org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionTemplateEntity") as EntityMetadata
    }

    override fun clone(): ScriptDefinitionTemplateEntityData {
        val clonedEntity = super.clone()
        clonedEntity as ScriptDefinitionTemplateEntityData
        clonedEntity.templateFqns = clonedEntity.templateFqns.toMutableWorkspaceList()
        clonedEntity.classpath = clonedEntity.classpath.toMutableWorkspaceList()
        return clonedEntity
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return ScriptDefinitionTemplateEntity::class.java
    }

    override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
        return ScriptDefinitionTemplateEntity(templateFqns, classpath, entitySource)
    }

    override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
        val res = mutableListOf<Class<out WorkspaceEntity>>()
        return res
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this.javaClass != other.javaClass) return false
        other as ScriptDefinitionTemplateEntityData
        if (this.entitySource != other.entitySource) return false
        if (this.templateFqns != other.templateFqns) return false
        if (this.classpath != other.classpath) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this.javaClass != other.javaClass) return false
        other as ScriptDefinitionTemplateEntityData
        if (this.templateFqns != other.templateFqns) return false
        if (this.classpath != other.classpath) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + templateFqns.hashCode()
        result = 31 * result + classpath.hashCode()
        return result
    }

    override fun hashCodeIgnoringEntitySource(): Int {
        var result = javaClass.hashCode()
        result = 31 * result + templateFqns.hashCode()
        result = 31 * result + classpath.hashCode()
        return result
    }
}
