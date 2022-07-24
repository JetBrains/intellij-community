// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiProviders

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
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
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
internal class KotlinModuleStateTrackerProvider(project: Project) : Disposable {
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
            is KtLibraryModule -> {
                val libraryInfo = module.moduleInfo as LibraryInfo
                libraryInfo.checkValidity()
                return libraryCache.computeIfAbsent(libraryInfo.library) { ModuleStateTrackerImpl() }
            }

            is KtSdkModule -> {
                val sdkInfo = module.moduleInfo as SdkInfo
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
            is KtBuiltinsModule -> ModuleStateTrackerImpl()
        }
    }

    private inner class JdkListener : ProjectJdkTable.Listener {
        override fun jdkRemoved(jdk: Sdk) {
            sdkCache[jdk]?.invalidate()
            sdkCache.remove(jdk)
        }
    }

    private inner class ModelChangeListener : WorkspaceModelChangeListener {
        override fun beforeChanged(event: VersionedStorageChange) {
            handleLibraryChanges(event)
            handleModuleRootChanges(event)
        }

        private fun handleLibraryChanges(event: VersionedStorageChange) {
            val libraryEntities = event.getChanges(LibraryEntity::class.java)
            if (libraryEntities.isEmpty()) return
            for (change in libraryEntities) {
                when (change) {
                    is EntityChange.Added -> {}
                    is EntityChange.Removed -> {
                        change.oldEntity.findLibraryBridge(event.storageBefore)?.let { library ->
                            libraryCache[library]?.invalidate()
                            libraryCache.remove(library)
                        }
                    }

                    is EntityChange.Replaced -> {
                        setOfNotNull(
                            change.oldEntity.findLibraryBridge(event.storageBefore),
                            change.newEntity.findLibraryBridge(event.storageBefore)
                        ).forEach { libraryCache[it]?.incModificationCount() }
                    }
                }
            }
        }

        private fun handleModuleRootChanges(event: VersionedStorageChange) {
            val moduleEntities = event.getChanges(ModuleEntity::class.java)
            if (moduleEntities.isEmpty()) return
            for (change in moduleEntities) {
                when (change) {
                    is EntityChange.Added -> {}
                    is EntityChange.Removed -> {
                        change.oldEntity.findModule(event.storageBefore)?.let { module ->
                            sourceModuleCache[module]?.invalidate()
                            sourceModuleCache.remove(module)
                        }
                    }

                    is EntityChange.Replaced -> {
                        setOfNotNull(
                            change.oldEntity.findModule(event.storageBefore),
                            change.newEntity.findModule(event.storageBefore)
                        ).forEach { sourceModuleCache[it]?.incModificationCount() }
                    }
                }
            }
        }
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