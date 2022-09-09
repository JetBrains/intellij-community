// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiProviders

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.libraries.Library
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.*
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.project.structure.*
import org.jetbrains.kotlin.analysis.providers.KtModuleStateTracker
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo
import org.jetbrains.kotlin.idea.base.util.Frontend10ApiUsage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@OptIn(Frontend10ApiUsage::class)
class KotlinModuleStateTrackerProvider(project: Project) : Disposable {
    init {
        val busConnection = project.messageBus.connect(this)
        WorkspaceModelTopics.getInstance(project).subscribeImmediately(busConnection, ModelChangeListener())
        busConnection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, JdkListener())
    }

    private val libraryCache = ConcurrentHashMap<Library, ModuleStateTrackerImpl>()
    private val sourceModuleCache = ConcurrentHashMap<Module, ModuleStateTrackerImpl>()
    private val sdkCache = ConcurrentHashMap<Sdk, ModuleStateTrackerImpl>()

    fun getModuleStateTrackerFor(module: KtModule): KtModuleStateTracker {
        return when (module) {
            is KtBuiltinsModule -> ModuleStateTrackerImpl()
            is KtLibraryModule -> {
                val libraryInfo = module.moduleInfo as LibraryInfo
                libraryInfo.checkValidity()
                return libraryCache.computeIfAbsent(libraryInfo.library) { ModuleStateTrackerImpl() }
            }

            is KtSdkModule -> {
                val sdkInfo = module.moduleInfo as SdkInfo
                sdkInfo.checkValidity()
                val sdk = sdkInfo.sdk
                return sdkCache.computeIfAbsent(sdk) { ModuleStateTrackerImpl() }
            }

            is KtSourceModule -> {
                val sourceModule = module.moduleInfo as ModuleSourceInfo
                sourceModule.checkValidity()
                return sourceModuleCache.computeIfAbsent(sourceModule.module) { ModuleStateTrackerImpl() }
            }

            is KtLibrarySourceModule -> getModuleStateTrackerFor(module.binaryLibrary)
            is KtNotUnderContentRootModule -> ModuleStateTrackerImpl() // TODO need proper cache?
        }
    }

    private inner class JdkListener : ProjectJdkTable.Listener {
        override fun jdkRemoved(jdk: Sdk) {
            sdkCache.remove(jdk)?.invalidate()
        }
    }

    private inner class ModelChangeListener : WorkspaceModelChangeListener {
        override fun beforeChanged(event: VersionedStorageChange) {
            handleLibraryChanges(event)
            handleModuleChanges(event)
            handleContentRootInModuleChanges(event)
        }

        private fun handleContentRootInModuleChanges(event: VersionedStorageChange) {
            for (changedModule in event.getChangedModules()) {
                sourceModuleCache[changedModule]?.incModificationCount()
            }
        }

        private fun VersionedStorageChange.getChangedModules(): Set<Module> = buildSet {
            getChanges(ContentRootEntity::class.java).mapNotNullTo(this) {
                getChangedModule(it.oldEntity, it.newEntity)
            }

            getChanges(SourceRootEntity::class.java).mapNotNullTo(this) {
                getChangedModule(it.oldEntity?.contentRoot, it.newEntity?.contentRoot)
            }

            getChanges(JavaSourceRootEntity::class.java).mapNotNullTo(this) {
                getChangedModule(it.oldEntity?.sourceRoot?.contentRoot, it.newEntity?.sourceRoot?.contentRoot)
            }
        }

        private fun VersionedStorageChange.getChangedModule(
            contentRootBefore: ContentRootEntity?,
            contentRootAfter: ContentRootEntity?
        ): Module? {
            val oldModule = contentRootBefore?.module?.findModule(storageBefore)
            val newModule = contentRootAfter?.module?.findModule(storageAfter)
            if (newModule != null && oldModule != null) {
                check(oldModule == newModule) {
                    "$oldModule should be equal to $newModule for ${EntityChange.Replaced::class.java}"
                }
            }
            return oldModule ?: newModule
        }

        private fun handleLibraryChanges(event: VersionedStorageChange) {
            val libraryEntities = event.getChanges(LibraryEntity::class.java).ifEmpty { return }
            for (change in libraryEntities) {
                when (change) {
                    is EntityChange.Added -> {}
                    is EntityChange.Removed -> {
                        change.oldEntity.findLibraryBridge(event.storageBefore)?.let { library ->
                            libraryCache.remove(library)?.invalidate()
                        }
                    }

                    is EntityChange.Replaced -> {
                        val changedLibrary = change.getReplacedEntity(event, LibraryEntity::findLibraryBridge) ?: continue
                        libraryCache[changedLibrary]?.incModificationCount()
                    }
                }
            }
        }

        private fun handleModuleChanges(event: VersionedStorageChange) {
            val moduleEntities = event.getChanges(ModuleEntity::class.java).ifEmpty { return }
            for (change in moduleEntities) {
                when (change) {
                    is EntityChange.Added -> {}
                    is EntityChange.Removed -> {
                        change.oldEntity.findModule(event.storageBefore)?.let { module ->
                            sourceModuleCache.remove(module)?.invalidate()
                        }
                    }

                    is EntityChange.Replaced -> {
                        val changedModule = change.getReplacedEntity(event, ModuleEntity::findModule) ?: continue
                        sourceModuleCache[changedModule]?.incModificationCount()
                    }
                }
            }
        }
    }

    private fun <C : WorkspaceEntity, E> EntityChange.Replaced<C>.getReplacedEntity(
        event: VersionedStorageChange,
        get: (C, EntityStorage) -> E
    ): E {
        val old = get(oldEntity, event.storageBefore)
        val new = get(newEntity, event.storageAfter)
        check(old == new) {
            "$old should be equal to $new for ${EntityChange.Replaced::class.java}"
        }
        return new
    }

    @TestOnly
    fun incrementModificationCountForAllModules() {
        libraryCache.forEach { _, tracker -> tracker.incModificationCount() }
        sourceModuleCache.forEach { _, tracker -> tracker.incModificationCount() }
        sdkCache.forEach { _, tracker -> tracker.incModificationCount() }
    }

    override fun dispose() {}

    companion object {
        fun getInstance(project: Project): KotlinModuleStateTrackerProvider =
            project.getService(KotlinModuleStateTrackerProvider::class.java)
    }

}

private class ModuleStateTrackerImpl : KtModuleStateTracker {
    private val modificationCount = AtomicLong()

    @Volatile
    private var _isValid = true

    fun incModificationCount() {
        modificationCount.incrementAndGet()
    }

    fun invalidate() {
        _isValid = false
    }

    override val isValid: Boolean get() = _isValid
    override val rootModificationCount: Long get() = modificationCount.get()
}