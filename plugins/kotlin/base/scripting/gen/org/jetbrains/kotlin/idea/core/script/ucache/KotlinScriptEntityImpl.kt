// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.SymbolicEntityId
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.SoftLinkable
import com.intellij.workspaceModel.storage.impl.UsedClassesCollector
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.containers.MutableWorkspaceSet
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.workspaceModel.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class KotlinScriptEntityImpl(val dataSource: KotlinScriptEntityData) : KotlinScriptEntity, WorkspaceEntityBase() {

    companion object {


        val connections = listOf<ConnectionId>(
        )

    }

    override val path: String
        get() = dataSource.path

    override val dependencies: Set<KotlinScriptLibraryId>
        get() = dataSource.dependencies

    override val entitySource: EntitySource
        get() = dataSource.entitySource

    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }

    class Builder(result: KotlinScriptEntityData?) : ModifiableWorkspaceEntityBase<KotlinScriptEntity, KotlinScriptEntityData>(
        result), KotlinScriptEntity.Builder {
        constructor() : this(KotlinScriptEntityData())

        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                }
                else {
                    error("Entity KotlinScriptEntity is already created in a different builder")
                }
            }

            this.diff = builder
            this.snapshot = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
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
            if (!getEntityData().isPathInitialized()) {
                error("Field KotlinScriptEntity#path should be initialized")
            }
            if (!getEntityData().isDependenciesInitialized()) {
                error("Field KotlinScriptEntity#dependencies should be initialized")
            }
        }

        override fun connectionIdList(): List<ConnectionId> {
            return connections
        }

        override fun afterModification() {
            val collection_dependencies = getEntityData().dependencies
            if (collection_dependencies is MutableWorkspaceSet<*>) {
                collection_dependencies.cleanModificationUpdateAction()
            }
        }

        // Relabeling code, move information from dataSource to this builder
        override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
            dataSource as KotlinScriptEntity
            if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
            if (this.path != dataSource.path) this.path = dataSource.path
            if (this.dependencies != dataSource.dependencies) this.dependencies = dataSource.dependencies.toMutableSet()
            updateChildToParentReferences(parents)
        }


        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData(true).entitySource = value
                changedProperty.add("entitySource")

            }

        override var path: String
            get() = getEntityData().path
            set(value) {
                checkModificationAllowed()
                getEntityData(true).path = value
                changedProperty.add("path")
            }

        private val dependenciesUpdater: (value: Set<KotlinScriptLibraryId>) -> Unit = { value ->

            changedProperty.add("dependencies")
        }
        override var dependencies: MutableSet<KotlinScriptLibraryId>
            get() {
                val collection_dependencies = getEntityData().dependencies
                if (collection_dependencies !is MutableWorkspaceSet) return collection_dependencies
                if (diff == null || modifiable.get()) {
                    collection_dependencies.setModificationUpdateAction(dependenciesUpdater)
                }
                else {
                    collection_dependencies.cleanModificationUpdateAction()
                }
                return collection_dependencies
            }
            set(value) {
                checkModificationAllowed()
                getEntityData(true).dependencies = value
                dependenciesUpdater.invoke(value)
            }

        override fun getEntityClass(): Class<KotlinScriptEntity> = KotlinScriptEntity::class.java
    }
}

class KotlinScriptEntityData : WorkspaceEntityData.WithCalculableSymbolicId<KotlinScriptEntity>(), SoftLinkable {
    lateinit var path: String
    lateinit var dependencies: MutableSet<KotlinScriptLibraryId>

    fun isPathInitialized(): Boolean = ::path.isInitialized
    fun isDependenciesInitialized(): Boolean = ::dependencies.isInitialized

    override fun getLinks(): Set<SymbolicEntityId<*>> {
        val result = HashSet<SymbolicEntityId<*>>()
        for (item in dependencies) {
            result.add(item)
        }
        return result
    }

    override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
        for (item in dependencies) {
            index.index(this, item)
        }
    }

    override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
        // TODO verify logic
        val mutablePreviousSet = HashSet(prev)
        for (item in dependencies) {
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
        val dependencies_data = dependencies.map {
            val it_data = if (it == oldLink) {
                changed = true
                newLink as KotlinScriptLibraryId
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
        if (dependencies_data != null) {
            dependencies = dependencies_data as MutableSet<KotlinScriptLibraryId>
        }
        return changed
    }

    override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<KotlinScriptEntity> {
        val modifiable = KotlinScriptEntityImpl.Builder(null)
        modifiable.diff = diff
        modifiable.snapshot = diff
        modifiable.id = createEntityId()
        return modifiable
    }

    override fun createEntity(snapshot: EntityStorage): KotlinScriptEntity {
        return getCached(snapshot) {
            val entity = KotlinScriptEntityImpl(this)
            entity.snapshot = snapshot
            entity.id = createEntityId()
            entity
        }
    }

    override fun clone(): KotlinScriptEntityData {
        val clonedEntity = super.clone()
        clonedEntity as KotlinScriptEntityData
        clonedEntity.dependencies = clonedEntity.dependencies.toMutableWorkspaceSet()
        return clonedEntity
    }

    override fun symbolicId(): SymbolicEntityId<*> {
        return KotlinScriptId(path)
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return KotlinScriptEntity::class.java
    }

    override fun serialize(ser: EntityInformation.Serializer) {
    }

    override fun deserialize(de: EntityInformation.Deserializer) {
    }

    override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
        return KotlinScriptEntity(path, dependencies, entitySource) {
        }
    }

    override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
        val res = mutableListOf<Class<out WorkspaceEntity>>()
        return res
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this.javaClass != other.javaClass) return false

        other as KotlinScriptEntityData

        if (this.entitySource != other.entitySource) return false
        if (this.path != other.path) return false
        if (this.dependencies != other.dependencies) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this.javaClass != other.javaClass) return false

        other as KotlinScriptEntityData

        if (this.path != other.path) return false
        if (this.dependencies != other.dependencies) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + path.hashCode()
        result = 31 * result + dependencies.hashCode()
        return result
    }

    override fun hashCodeIgnoringEntitySource(): Int {
        var result = javaClass.hashCode()
        result = 31 * result + path.hashCode()
        result = 31 * result + dependencies.hashCode()
        return result
    }

    override fun collectClassUsagesData(collector: UsedClassesCollector) {
        collector.add(KotlinScriptLibraryId::class.java)
        this.dependencies?.let { collector.add(it::class.java) }
        collector.sameForAllEntities = false
    }
}
