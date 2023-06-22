// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiProviders

import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.util.io.URLUtil
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.WorkspaceEntity
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.providers.KotlinGlobalModificationService
import org.jetbrains.kotlin.analysis.providers.analysisMessageBus
import org.jetbrains.kotlin.analysis.providers.topics.KotlinTopics
import org.jetbrains.kotlin.idea.base.projectStructure.getBinaryAndSourceModuleInfos
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.productionOrTestSourceModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.toKtModule
import org.jetbrains.kotlin.idea.util.AbstractSingleFileModuleAfterFileEventListener
import org.jetbrains.kotlin.idea.util.AbstractSingleFileModuleBeforeFileEventListener

open class KotlinModuleStateModificationService(val project: Project) : Disposable {
    protected open fun mayBuiltinsHaveChanged(events: List<VFileEvent>): Boolean { return false }

    private fun invalidateSourceModule(module: Module, isRemoval: Boolean = false) {
        // A test source `KtModule` will be invalidated together with its production source `KtModule` because it is a direct dependent from
        // friend dependencies. See `IdeKotlinModuleDependentsProvider`.
        module.productionOrTestSourceModuleInfo?.let { project.publishModuleStateModification(it.toKtModule(), isRemoval) }
    }

    private fun invalidateLibraryModule(library: Library, isRemoval: Boolean = false) {
        invalidateByModuleInfos(library.getBinaryAndSourceModuleInfos(project), isRemoval)
    }

    private fun invalidateByModuleInfos(moduleInfos: Iterable<IdeaModuleInfo>, isRemoval: Boolean) {
        moduleInfos.forEach { moduleInfo ->
            project.publishModuleStateModification(moduleInfo.toKtModule(), isRemoval)
        }
    }

    /**
     * Publishes a module state modification event for a script or not-under-content-root [KtModule] whose file has been moved.
     *
     * Even though the event is processed *after* the file has been moved, the [KtModule]s before and after the move are equal via their
     * respective module infos, because:
     *
     *  - In the case of `NotUnderContentRootModuleInfo`, the file pointer remains the same after the move.
     *  - In the case of `ScriptModuleInfo`, the virtual file and script definition remain the same after the move.
     *
     * A global out-of-block modification event will be published by `FirIdeOutOfBlockPsiTreeChangePreprocessor` after a Kotlin file is
     * moved, but we still need this listener to publish a module state modification event specifically.
     */
    class SingleFileModuleMoveListener(private val project: Project) : AbstractSingleFileModuleAfterFileEventListener(project) {
        override fun isRelevantEvent(event: VFileEvent, file: VirtualFile): Boolean = event is VFileMoveEvent

        override fun processEvent(event: VFileEvent, module: KtModule) {
            project.publishModuleStateModification(module, isRemoval = false)
        }
    }

    /**
     * Publishes a module state modification event for a script or not-under-content-root [KtModule] whose file has been deleted.
     *
     * This listener processes events *before* the file is deleted, because getting a PSI file (and in turn the PSI file's [KtModule]) for a
     * virtual file which has already been deleted is not feasible.
     *
     * A global out-of-block modification event will be published by `FirIdeOutOfBlockPsiTreeChangePreprocessor` after a Kotlin file is
     * deleted, but we still need this listener to publish a module state modification event specifically.
     */
    class SingleFileModuleDeletionListener(private val project: Project) : AbstractSingleFileModuleBeforeFileEventListener(project) {
        override fun isRelevantEvent(event: VFileEvent, file: VirtualFile): Boolean = event is VFileDeleteEvent

        override fun processEvent(event: VFileEvent, module: KtModule) {
            project.publishModuleStateModification(module, isRemoval = true)
        }
    }

    class LibraryUpdatesListener(private val project: Project) : BulkFileListener {
        val index = ProjectRootManager.getInstance(project).fileIndex

        private val moduleStateModificationService: KotlinModuleStateModificationService get() = getInstance(project)

        override fun after(events: List<VFileEvent>) {
            if (moduleStateModificationService.mayBuiltinsHaveChanged(events)) {
                KotlinGlobalModificationService.getInstance(project).publishGlobalModuleStateModification()
                return
            }

            events.mapNotNull { event ->
                val file = when (event) {
                    //for all other events workspace model should do the job
                    is VFileContentChangeEvent -> event.file
                    else -> return@mapNotNull null
                }
                if (file.extension != "jar") return@mapNotNull null  //react only on jars
                val jarRoot = StandardFileSystems.jar().findFileByPath(file.path + URLUtil.JAR_SEPARATOR) ?: return@mapNotNull null
                (index.getOrderEntriesForFile(jarRoot).firstOrNull { it is LibraryOrderEntry } as? LibraryOrderEntry)?.library
            }.distinct().forEach { moduleStateModificationService.invalidateLibraryModule(it) }
        }
    }

    class JdkListener(private val project: Project) : ProjectJdkTable.Listener {
        override fun jdkRemoved(jdk: Sdk) {
            // Most modules will depend on an SDK, so its removal constitutes global module state modification. We cannot be more
            // fine-grained here because `KtSdkModules`s aren't supported by `IdeKotlinModuleDependentsProvider`, so invalidation based on
            // a module-level modification event may not work as expected with a `KtSdkModule`.
            KotlinGlobalModificationService.getInstance(project).publishGlobalModuleStateModification()
        }
    }

    class ModelChangeListener(private val project: Project) : WorkspaceModelChangeListener {
        private val moduleStateModificationService: KotlinModuleStateModificationService get() = getInstance(project)

        override fun beforeChanged(event: VersionedStorageChange) {
            handleLibraryChanges(event)
            handleModuleChanges(event)
            handleContentRootInModuleChanges(event)
        }

        private fun handleContentRootInModuleChanges(event: VersionedStorageChange) {
            for (changedModule in event.getChangedModules()) {
                moduleStateModificationService.invalidateSourceModule(changedModule)
            }
        }

        private fun VersionedStorageChange.getChangedModules(): Set<Module> = buildSet {
            getChanges(ContentRootEntity::class.java).mapNotNullTo(this) {
                getChangedModule(it.oldEntity, it.newEntity)
            }

            getChanges(SourceRootEntity::class.java).mapNotNullTo(this) {
                getChangedModule(it.oldEntity?.contentRoot, it.newEntity?.contentRoot)
            }

            getChanges(JavaSourceRootPropertiesEntity::class.java).mapNotNullTo(this) {
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
                        change.oldEntity
                          .findLibraryBridge(event.storageBefore)
                          ?.let { moduleStateModificationService.invalidateLibraryModule(it, isRemoval = true) }
                    }

                    is EntityChange.Replaced -> {
                        val changedLibrary = change.getReplacedEntity(event, LibraryEntity::findLibraryBridge) ?: continue
                        moduleStateModificationService.invalidateLibraryModule(changedLibrary)
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
                            moduleStateModificationService.invalidateSourceModule(module, isRemoval = true)
                        }
                    }

                    is EntityChange.Replaced -> {
                        val changedModule = change.getReplacedEntity(event, ModuleEntity::findModule) ?: continue
                        moduleStateModificationService.invalidateSourceModule(changedModule)
                    }
                }
            }
        }
    }

    override fun dispose() {}

    companion object {
        fun getInstance(project: Project): KotlinModuleStateModificationService =
            project.getService(KotlinModuleStateModificationService::class.java)
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

private fun Project.publishModuleStateModification(module: KtModule, isRemoval: Boolean) {
    analysisMessageBus.syncPublisher(KotlinTopics.MODULE_STATE_MODIFICATION).onModification(module, isRemoval)
}
