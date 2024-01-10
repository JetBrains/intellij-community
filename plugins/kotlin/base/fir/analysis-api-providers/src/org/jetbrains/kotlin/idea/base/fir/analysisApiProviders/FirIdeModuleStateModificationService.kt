// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiProviders

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ProjectFileIndex
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
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryTableId.GlobalLibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.io.URLUtil
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.providers.KotlinGlobalModificationService
import org.jetbrains.kotlin.analysis.providers.analysisMessageBus
import org.jetbrains.kotlin.analysis.providers.topics.KotlinModuleStateModificationKind
import org.jetbrains.kotlin.analysis.providers.topics.KotlinTopics
import org.jetbrains.kotlin.idea.base.projectStructure.getBinaryAndSourceModuleInfos
import org.jetbrains.kotlin.idea.base.projectStructure.toKtModule
import org.jetbrains.kotlin.idea.base.util.caching.getChanges
import org.jetbrains.kotlin.idea.base.util.caching.newEntity
import org.jetbrains.kotlin.idea.facet.isKotlinFacet
import org.jetbrains.kotlin.idea.util.AbstractSingleFileModuleBeforeFileEventListener
import org.jetbrains.kotlin.idea.util.toKtModulesForModificationEvents
import org.jetbrains.kotlin.utils.alwaysTrue
import java.util.regex.Pattern

private val STDLIB_PATTERN = Pattern.compile("kotlin-stdlib-(\\d*)\\.(\\d*)\\.(\\d*)\\.jar")

@Service(Service.Level.PROJECT)
class FirIdeModuleStateModificationService(val project: Project) : Disposable {
    /**
     * Publishes a module state modification event for a script or not-under-content-root [KtModule] whose file is being moved or deleted.
     *
     * This listener processes events *before* the file is moved/deleted due to the following reasons:
     *
     *  1. Move: The file may be moved outside the project's content root. The listener cannot react to such files.
     *  2. Deletion: Getting a PSI file (and in turn the PSI file's [KtModule]) for a virtual file which has been deleted is not feasible.
     *
     * A global out-of-block modification event will be published by `FirIdeOutOfBlockPsiTreeChangePreprocessor` when a Kotlin file is
     * moved, but we still need this listener to publish a module state modification event specifically.
     */
    internal class SingleFileModuleModificationListener(private val project: Project) : AbstractSingleFileModuleBeforeFileEventListener(project) {
        override fun isRelevantEvent(event: VFileEvent, file: VirtualFile): Boolean = event is VFileMoveEvent || event is VFileDeleteEvent

        override fun processEvent(event: VFileEvent, module: KtModule) {
            val modificationKind = when (event) {
                is VFileDeleteEvent -> KotlinModuleStateModificationKind.REMOVAL
                else -> KotlinModuleStateModificationKind.UPDATE
            }
            module.publishModuleStateModification(modificationKind)
        }
    }

    internal class LibraryUpdatesListener(private val project: Project) : BulkFileListener {
        private val fileIndex by lazy(LazyThreadSafetyMode.PUBLICATION) {
            ProjectRootManager.getInstance(project).fileIndex
        }

        override fun before(events: List<VFileEvent>) {
            if (mayBuiltinsHaveChanged(events)) {
                KotlinGlobalModificationService.getInstance(project).publishGlobalModuleStateModification()
                return
            }

            events.mapNotNull { event ->
                val file = when (event) {
                    //for all other events workspace model should do the job
                    is VFileContentChangeEvent -> event.file
                    else -> return@mapNotNull null
                }
                if (!file.extension.equals("jar", ignoreCase = true)) return@mapNotNull null  //react only on jars
                val jarRoot = StandardFileSystems.jar().findFileByPath(file.path + URLUtil.JAR_SEPARATOR) ?: return@mapNotNull null
                (fileIndex.getOrderEntriesForFile(jarRoot).firstOrNull { it is LibraryOrderEntry } as? LibraryOrderEntry)?.library
            }.distinct().forEach { it.publishModuleStateModification(project) }
        }

        private fun mayBuiltinsHaveChanged(events: List<VFileEvent>): Boolean {
            return events.find { event ->
                event is VFileContentChangeEvent && STDLIB_PATTERN.matcher(event.file.name).matches()
            } != null
        }
    }

    internal class JdkListener(private val project: Project) : ProjectJdkTable.Listener {
        override fun jdkRemoved(jdk: Sdk) {
            // Most modules will depend on an SDK, so its removal constitutes global module state modification. We cannot be more
            // fine-grained here because `KtSdkModules`s aren't supported by `IdeKotlinModuleDependentsProvider`, so invalidation based on
            // a module-level modification event may not work as expected with a `KtSdkModule`.
            KotlinGlobalModificationService.getInstance(project).publishGlobalModuleStateModification()
        }
    }

    internal class NonWorkspaceModuleRootListener(private val project: Project) : ModuleRootListener {
        override fun beforeRootsChange(event: ModuleRootEvent) {
            if (event.isCausedByWorkspaceModelChangesOnly) return

            // The cases described in `isCausedByWorkspaceModelChangesOnly` are rare enough to publish global module state modification
            // events for simplicity. `NonWorkspaceModuleRootListener` can eventually be removed once the APIs described in
            // `isCausedByWorkspaceModelChangesOnly` are removed.
            KotlinGlobalModificationService.getInstance(project).publishGlobalModuleStateModification()
        }
    }

    internal class FileDocumentListener(private val project: Project) : FileDocumentManagerListener {
        override fun fileWithNoDocumentChanged(file: VirtualFile) {
            // `FileDocumentManagerListener` may receive events from other projects via `FileDocumentManagerImpl`'s `AsyncFileListener`.
            if (!project.isInitialized || !ProjectFileIndex.getInstance(project).isInContent(file)) {
                return
            }

            // Workspace model changes are already handled by `FirIdeModuleStateModificationService`, so we shouldn't publish a module state
            // modification event when the `workspace.xml` is changed by the IDE.
            if (file == project.workspaceFile) {
                return
            }

            // "No document" means no pomModel change. If the file's PSI was not loaded, then a modification event would be published via
            // the `PsiTreeChangeEvent.PROP_UNLOADED_PSI` event. If the PSI was loaded, then `FileManagerImpl.reloadPsiAfterTextChange` is
            // fired which doesn't provide any explicit changes, so we need to publish a global modification event because the file's
            // content may have been changed externally.
            KotlinGlobalModificationService.getInstance(project).publishGlobalModuleStateModification()
        }
    }

    internal class MyDynamicPluginListener(private val project: Project) : DynamicPluginListener {
        override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
            runWriteAction {
                KotlinGlobalModificationService.getInstance(project).publishGlobalModuleStateModification()
            }
        }

        override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
            runWriteAction {
                KotlinGlobalModificationService.getInstance(project).publishGlobalModuleStateModification()
            }
        }
    }

    @ApiStatus.Internal
    fun beforeWorkspaceModelChanged(event: VersionedStorageChange) {
        handleLibraryChanges(event)

        /**
         * We keep track of the already invalidated modules because we don't need [handleChangesInsideModule] to publish another
         * module state modification event for the same module.
         */
        val alreadyInvalidatedModules = handleModuleChanges(event)
        handleChangesInsideModule(event, alreadyInvalidatedModules)
    }

    /**
     * Handel changes inside a module structure.
     * All such changes are treated as [UPDATE][KotlinModuleStateModificationKind.UPDATE]
     *
     * Some examples: changes in content roots, or in the module facet.
     */
    private fun handleChangesInsideModule(event: VersionedStorageChange, alreadyInvalidatedModules: Set<Module>) {
        for (changedModule in event.getChangesInsideModules()) {
            if (changedModule in alreadyInvalidatedModules) continue
            changedModule.publishModuleStateModification()
        }
    }

    private fun VersionedStorageChange.getChangesInsideModules(): Set<Module> = buildSet {
        contentRootChanges(this)
        facetChanges(this)
    }

    private fun VersionedStorageChange.contentRootChanges(modules: MutableSet<Module>) {
        getChanges<ContentRootEntity>().mapNotNullTo(modules) {
            getChangedModule(it.oldEntity, it.newEntity)
        }

        getChanges<SourceRootEntity>().mapNotNullTo(modules) {
            getChangedModule(it.oldEntity?.contentRoot, it.newEntity?.contentRoot)
        }

        getChanges<JavaSourceRootPropertiesEntity>().mapNotNullTo(modules) {
            getChangedModule(it.oldEntity?.sourceRoot?.contentRoot, it.newEntity?.sourceRoot?.contentRoot)
        }
    }

    private fun VersionedStorageChange.getChangedModule(
        contentRootBefore: ContentRootEntity?,
        contentRootAfter: ContentRootEntity?
    ): Module? = getChangedModule(
        contentRootBefore,
        contentRootAfter,
        moduleSelector = ContentRootEntity::module,
    )

    private fun VersionedStorageChange.facetChanges(modules: MutableSet<Module>) {
        getChanges<FacetEntity>().mapNotNullTo(modules) {
            getChangedModule(
                oldEntity = it.oldEntity,
                newEntity = it.newEntity,
                entityFilter = FacetEntity::isKotlinFacet,
                moduleSelector = FacetEntity::module,
            )
        }
    }

    private fun <T : WorkspaceEntity> VersionedStorageChange.getChangedModule(
        oldEntity: T?,
        newEntity: T?,
        entityFilter: (T) -> Boolean = alwaysTrue<T>(),
        moduleSelector: (T) -> ModuleEntity?,
    ): Module? {
        val oldModule = oldEntity?.takeIf(entityFilter)?.let(moduleSelector)?.findModule(storageBefore)
        val newModule = newEntity?.takeIf(entityFilter)?.let(moduleSelector)?.findModule(storageAfter)
        if (newModule != null && oldModule != null) {
            check(oldModule == newModule) {
                "$oldModule should be equal to $newModule for ${EntityChange.Replaced::class.java}"
            }
        }

        return oldModule ?: newModule
    }

    private fun handleLibraryChanges(event: VersionedStorageChange) {
        val libraryEntities = event.getChanges<LibraryEntity>().ifEmpty { return }
        for (change in libraryEntities) {
            when (change) {
                is EntityChange.Added -> {}
                is EntityChange.Removed -> {
                    change.oldEntity
                        .takeIf { it.tableId !is GlobalLibraryTableId }
                        ?.findLibraryBridge(event.storageBefore)
                        ?.let { it.publishModuleStateModification(project, KotlinModuleStateModificationKind.REMOVAL) }
                }

                is EntityChange.Replaced -> {
                    change.newEntity()
                        ?.takeIf { it.tableId !is GlobalLibraryTableId }
                        ?.findLibraryBridge(event.storageAfter)?.let {
                        it.publishModuleStateModification(project)
                    }
                }
            }
        }
    }

    /**
     * Invalidates removed and replaced [Module]s and returns the set of these invalidated modules.
     */
    private fun handleModuleChanges(event: VersionedStorageChange): Set<Module> {
        val moduleEntities = event.getChanges<ModuleEntity>()
        val moduleSettingChanges: List<EntityChange<JavaModuleSettingsEntity>> = event.getChanges<JavaModuleSettingsEntity>()

        fun <T : WorkspaceEntity> MutableSet<Module>.processEntities(changes: List<EntityChange<T>>, toModule: (T) -> ModuleEntity?) {
            for (change: EntityChange<T> in changes) {
                when (change) {
                    is EntityChange.Added -> {}
                    is EntityChange.Removed -> {
                        toModule(change.oldEntity)?.findModule(event.storageBefore)?.let { module ->
                            module.publishModuleStateModification(KotlinModuleStateModificationKind.REMOVAL)
                            add(module)
                        }
                    }

                    is EntityChange.Replaced -> {
                        toModule(change.newEntity)?.findModule(event.storageAfter)?.let { module ->
                            module.publishModuleStateModification()
                            add(module)
                        }
                    }
                }
            }
        }

        return buildSet {
            processEntities(moduleEntities) { it }
            processEntities(moduleSettingChanges) { it.module }
        }
    }

    override fun dispose() {}

    companion object {
        fun getInstance(project: Project): FirIdeModuleStateModificationService =
            project.getService(FirIdeModuleStateModificationService::class.java)
    }
}

private fun Module.publishModuleStateModification(
    modificationKind: KotlinModuleStateModificationKind = KotlinModuleStateModificationKind.UPDATE,
) {
    toKtModulesForModificationEvents().forEach { ktModule ->
        ktModule.publishModuleStateModification(modificationKind)
    }
}

private fun Library.publishModuleStateModification(
    project: Project,
    modificationKind: KotlinModuleStateModificationKind = KotlinModuleStateModificationKind.UPDATE,
) {
    getBinaryAndSourceModuleInfos(project).forEach { moduleInfo ->
        moduleInfo.toKtModule().publishModuleStateModification(modificationKind)
    }
}

private fun KtModule.publishModuleStateModification(modificationKind: KotlinModuleStateModificationKind) {
    ThreadingAssertions.assertWriteAccess()

    project.analysisMessageBus.syncPublisher(KotlinTopics.MODULE_STATE_MODIFICATION).onModification(this, modificationKind)
}
