// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform

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
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.jps.entities.LibraryTableId.GlobalLibraryTableId
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.io.URLUtil
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModuleStateModificationKind
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalModuleStateModificationEvent
import org.jetbrains.kotlin.analysis.api.platform.modification.publishModuleStateModificationEvent
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltinsVirtualFileProviderBaseImpl
import org.jetbrains.kotlin.idea.base.projectStructure.toKaLibraryModules
import org.jetbrains.kotlin.idea.base.util.caching.SdkEntityChangeListener
import org.jetbrains.kotlin.idea.base.util.caching.getChanges
import org.jetbrains.kotlin.idea.base.util.caching.newEntity
import org.jetbrains.kotlin.idea.facet.isKotlinFacet
import org.jetbrains.kotlin.idea.util.AbstractSingleFileModuleBeforeFileEventListener
import org.jetbrains.kotlin.idea.util.toKaModulesForModificationEvents
import org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsEntity
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.alwaysTrue
import java.net.URL
import java.util.regex.Pattern

private val STDLIB_PATTERN = Pattern.compile("kotlin-stdlib-(\\d*)\\.(\\d*)\\.(\\d*)\\.jar")

@Service(Service.Level.PROJECT)
class FirIdeModuleStateModificationService(val project: Project) : Disposable {
    internal class BuiltinsFileDocumentListener(private val project: Project) : FileDocumentManagerListener {
        private val builtinsFiles: Set<String> by lazy {
            val result = mutableSetOf<String>()
            object : BuiltinsVirtualFileProviderBaseImpl() {
                override fun findVirtualFile(url: URL): VirtualFile {
                    result.addIfNotNull(URLUtil.splitJarUrl(url.file)?.first)
                    return LightVirtualFile()
                }
            }.getBuiltinVirtualFiles()
            result
        }

        override fun fileWithNoDocumentChanged(file: VirtualFile) {
            // Builtins sessions are created based on `BuiltInsVirtualFileProvider.getInstance().getBuiltInVirtualFiles()`
            // Those files are located inside IDE distribution and are outside the project.
            // So if those files are changed on the disk e.g., after update from sources, VFS watcher detects this:
            // `onContentReloaded` event is emitted.
            // Stub index for those files is rebuild (see `BuiltInsIndexableSetContributor`).
            // To ensure that no cached psi with stale stubs/virtual files,
            // it's required to clear caches manually,
            // otherwise opening file which referred the old builtins would let to PIEAE exceptions
            val jarPath = URLUtil.splitJarUrl(file.path)?.first
            //MAYBE RC: try (file.fileSystem as ArchiveFileSystem).getLocalByEntry(file) instead of expensive
            //          file.path building and splitting
            if (jarPath != null && jarPath in builtinsFiles) {
                runWriteAction {
                    PsiManager.getInstance(project).dropPsiCaches()
                    project.publishGlobalModuleStateModificationEvent()
                }
            }
        }
    }

    /**
     * Publishes a module state modification event for a script or not-under-content-root [KaModule] whose file is being moved or deleted.
     *
     * This listener processes events *before* the file is moved/deleted due to the following reasons:
     *
     *  1. Move: The file may be moved outside the project's content root. The listener cannot react to such files.
     *  2. Deletion: Getting a PSI file (and in turn the PSI file's [KaModule]) for a virtual file which has been deleted is not feasible.
     *
     * A global out-of-block modification event will be published by `FirIdeOutOfBlockPsiTreeChangePreprocessor` when a Kotlin file is
     * moved, but we still need this listener to publish a module state modification event specifically.
     */
    internal class SingleFileModuleModificationListener(project: Project) : AbstractSingleFileModuleBeforeFileEventListener(project) {
        override fun isRelevantEvent(event: VFileEvent, file: VirtualFile): Boolean = event is VFileMoveEvent || event is VFileDeleteEvent

        override fun processEvent(event: VFileEvent, module: KaModule) {
            val modificationKind = when (event) {
                is VFileDeleteEvent -> KotlinModuleStateModificationKind.REMOVAL
                else -> KotlinModuleStateModificationKind.UPDATE
            }
            module.publishModuleStateModificationEvent(modificationKind)
        }
    }

    internal class LibraryUpdatesListener(private val project: Project) : BulkFileListener {
        private val fileIndex by lazy(LazyThreadSafetyMode.PUBLICATION) {
            ProjectRootManager.getInstance(project).fileIndex
        }

        override fun before(events: List<VFileEvent>) {
            if (!project.isInitialized) return

            if (mayBuiltinsHaveChanged(events)) {
                project.publishGlobalModuleStateModificationEvent()
                return
            }

            val workspaceModel = project.workspaceModel

            val affectedLibraries = events.flatMapTo(mutableSetOf()) { event ->
                val file = when (event) {
                    //for all other events workspace model should do the job
                    is VFileContentChangeEvent -> event.file
                    else -> return@flatMapTo emptyList()
                }

                if (!file.extension.equals("jar", ignoreCase = true)) return@flatMapTo emptyList()  //react only on jars
                val jarRoot = StandardFileSystems.jar().findFileByPath(file.path + URLUtil.JAR_SEPARATOR) ?: return@flatMapTo emptyList()
                val url = jarRoot.toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager())
                workspaceModel.currentSnapshot.getVirtualFileUrlIndex().findEntitiesByUrl(url).filterIsInstance<LibraryEntity>().toList()
            }

            for (library in affectedLibraries) {
                library.publishModuleStateModificationEvent(project)
            }
        }

        private fun mayBuiltinsHaveChanged(events: List<VFileEvent>): Boolean {
            return events.find { event ->
                event is VFileContentChangeEvent && STDLIB_PATTERN.matcher(event.file.name).matches()
            } != null
        }
    }

    internal class SdkChangeListener(project: Project): SdkEntityChangeListener(project) {
        override fun entitiesChanged(outdated: List<Sdk>) {
            // Most modules will depend on an SDK, so its removal constitutes global module state modification. We cannot be more
            // fine-grained here because SDK modules aren't supported by `IdeKotlinModuleDependentsProvider`, so invalidation based on a
            // module-level modification event may not work as expected with an SDK module.
            project.publishGlobalModuleStateModificationEvent()
        }
    }

    internal class NonWorkspaceModuleRootListener(private val project: Project) : ModuleRootListener {
        override fun rootsChanged(event: ModuleRootEvent) {
            if (event.isCausedByWorkspaceModelChangesOnly) return

            // The cases described in `isCausedByWorkspaceModelChangesOnly` are rare enough to publish global module state modification
            // events for simplicity. `NonWorkspaceModuleRootListener` can eventually be removed once the APIs described in
            // `isCausedByWorkspaceModelChangesOnly` are removed.
            project.publishGlobalModuleStateModificationEvent()
        }
    }

    internal class ProjectFileDocumentListener(private val project: Project) : FileDocumentManagerListener {
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
            project.publishGlobalModuleStateModificationEvent()
        }
    }

    internal class MyDynamicPluginListener(private val project: Project) : DynamicPluginListener {
        override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
            runWriteAction {
                project.publishGlobalModuleStateModificationEvent()
            }
        }

        override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
            runWriteAction {
                project.publishGlobalModuleStateModificationEvent()
            }
        }
    }

    internal class GeneralWorkspaceModelChangeListener(private val project: Project) : WorkspaceModelChangeListener {
        override fun beforeChanged(event: VersionedStorageChange) {
            getInstance(project).beforeWorkspaceModelChanged(event)
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
            changedModule.publishModuleStateModificationEvent()
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

        getChanges<KotlinSettingsEntity>().mapNotNullTo(modules) {
            getChangedModule(
                oldEntity = it.oldEntity,
                newEntity = it.newEntity,
                entityFilter = KotlinSettingsEntity::isKotlinFacet,
                moduleSelector = KotlinSettingsEntity::module,
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
                        ?.publishModuleStateModificationEvent(project, KotlinModuleStateModificationKind.REMOVAL)
                }

                is EntityChange.Replaced -> {
                    change.newEntity()
                        ?.takeIf { it.tableId !is GlobalLibraryTableId }
                        ?.publishModuleStateModificationEvent(project)
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
                            module.publishModuleStateModificationEvent(KotlinModuleStateModificationKind.REMOVAL)
                            add(module)
                        }
                    }

                    is EntityChange.Replaced -> {
                        toModule(change.newEntity)?.findModule(event.storageAfter)?.let { module ->
                            module.publishModuleStateModificationEvent()
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

private fun Module.publishModuleStateModificationEvent(
    modificationKind: KotlinModuleStateModificationKind = KotlinModuleStateModificationKind.UPDATE,
) {
    toKaModulesForModificationEvents().forEach { kaModule ->
        kaModule.publishModuleStateModificationEvent(modificationKind)
    }
}

private fun LibraryEntity.publishModuleStateModificationEvent(
    project: Project,
    modificationKind: KotlinModuleStateModificationKind = KotlinModuleStateModificationKind.UPDATE,
) {
    toKaLibraryModules(project).forEach { module ->
        module.publishModuleStateModificationEvent(modificationKind)
        module.librarySources?.publishModuleStateModificationEvent(modificationKind)
    }
}
