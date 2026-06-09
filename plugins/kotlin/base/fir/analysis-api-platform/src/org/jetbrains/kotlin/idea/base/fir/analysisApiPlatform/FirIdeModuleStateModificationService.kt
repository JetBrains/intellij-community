// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteActionListener
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryTableId.GlobalLibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.io.URLUtil
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.platform.KotlinAnalysisInWriteActionListener
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModuleStateModificationKind
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalModuleStateModificationEvent
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalSourceModuleStateModificationEvent
import org.jetbrains.kotlin.analysis.api.platform.modification.publishModuleStateModificationEvent
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltinsVirtualFileProviderBaseImpl
import org.jetbrains.kotlin.idea.base.util.caching.SdkEntityChangeListener
import org.jetbrains.kotlin.idea.base.util.caching.getChanges
import org.jetbrains.kotlin.idea.facet.isKotlinFacet
import org.jetbrains.kotlin.idea.util.AbstractSingleFileModuleBeforeFileEventListener
import org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsEntity
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.alwaysTrue
import java.net.URL
import java.util.regex.Pattern

private val STDLIB_PATTERN = Pattern.compile("kotlin-stdlib-(\\d*)\\.(\\d*)\\.(\\d*)\\.jar")

/**
 * Publishes [modification events][org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationEvent]s for various kinds of
 * project structure changes. For example, workspace model changes, library file updates, or plugin loading/unloading.
 *
 * ### Avoidance of module-level modification events
 *
 * We generally *avoid* module-level modification events for performance reasons: A module modification event triggers the computation of
 * dependents, which can become very expensive when many modules are affected (such as on project sync).
 *
 * The only advantage of a module-level event over a global event is that fewer analysis caches are invalidated, allowing for faster
 * re-analysis. This is not very relevant to project structure changes: When the user is actively working in the IDE, project structure
 * changes are rare, so the potential performance benefit is minimal. At the same time, during heavy operations like project sync,
 * module-level modification events can lead to freezes (see KT-82449).
 *
 * An exception is [SingleFileModuleModificationListener], which publishes module-level events for single-file modules that don't have
 * dependents.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class FirIdeModuleStateModificationService(val project: Project) : Disposable {
    /**
     * The event publishing state of [ProjectFileDocumentListener]. This is stored in the modification service because listeners should be
     * stateless.
     *
     * The state is only accessed during write actions, so it is not used concurrently. However, it might be accessed from multiple threads
     * in sequence, so it should still be volatile.
     */
    @Volatile
    private var projectFileEventPublishingState = EventPublishingState.NONE

    init {
        ApplicationManagerEx.getApplicationEx().addWriteActionListener(EventPublishingStateResetWriteActionListener(), this)
    }

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
     * A global out-of-block modification event will be published by `FirIdeOutOfBlockModificationService` when a Kotlin file is moved, but
     * we still need this listener to publish a module state modification event specifically.
     */
    internal class SingleFileModuleModificationListener(project: Project) : AbstractSingleFileModuleBeforeFileEventListener(project) {
        override fun isRelevantEvent(event: VFileEvent, file: VirtualFile): Boolean = event is VFileMoveEvent || event is VFileDeleteEvent

        override fun processEvent(event: VFileEvent, module: KaModule) {
            val modificationKind = when (event) {
                is VFileDeleteEvent -> KotlinModuleStateModificationKind.REMOVAL
                else -> KotlinModuleStateModificationKind.UPDATE
            }

            // Despite the recommendation to publish global modification events, a module-level modification event is fine in this case, as
            // single-file modules don't have dependents. For invalidation performance, dependents calculation is the crucial factor.
            module.publishModuleStateModificationEvent(modificationKind)
        }
    }

    internal class LibraryUpdatesListener(private val project: Project) : BulkFileListener {
        override fun before(events: List<VFileEvent>) {
            if (!project.isInitialized) return

            if (mayBuiltinsHaveChanged(events)) {
                project.publishGlobalModuleStateModificationEvent()
                return
            }

            val hasAffectedJarFiles = events.any { event ->
                val file = when (event) {
                    // For all other events, the workspace model should do the job.
                    is VFileContentChangeEvent -> event.file
                    else -> return@any false
                }

                // React only on jars.
                file.extension.equals("jar", ignoreCase = true)
            }

            if (hasAffectedJarFiles) {
                project.publishGlobalModuleStateModificationEvent()
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

    /**
     * To avoid performance problems associated with excessive publishing of global modification events, the listener only publishes a
     * modification event when necessary and ignores further events.
     */
    internal class ProjectFileDocumentListener(private val project: Project) : FileDocumentManagerListener {
        override fun fileWithNoDocumentChanged(file: VirtualFile) {
            val modificationService = getInstance(project)
            if (modificationService.projectFileEventPublishingState == EventPublishingState.GLOBAL_EVENT_PUBLISHED) return

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

            modificationService.projectFileEventPublishingState = EventPublishingState.GLOBAL_EVENT_PUBLISHED
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
        if (hasLibraryChanges(event)) {
            project.publishGlobalModuleStateModificationEvent()

            // Since we've already published a full global module state modification event, we don't need to publish another one for
            // modules.
            return
        }

        if (hasModuleChanges(event)) {
            project.publishGlobalSourceModuleStateModificationEvent()
        }
    }

    private fun hasLibraryChanges(event: VersionedStorageChange): Boolean =
        event.getChanges<LibraryEntity>().any { change ->
            when (change) {
                is EntityChange.Added -> false
                is EntityChange.Removed -> change.oldEntity.tableId !is GlobalLibraryTableId
                is EntityChange.Replaced -> change.newEntity.tableId !is GlobalLibraryTableId
            }
        }

    private fun hasModuleChanges(event: VersionedStorageChange): Boolean =
        hasModuleEntityChanges(event) || hasContentRootChanges(event) || hasFacetChanges(event)

    private fun hasModuleEntityChanges(event: VersionedStorageChange): Boolean {
        fun <T : WorkspaceEntity> hasRelevantChange(
            changes: List<EntityChange<T>>,
            toModule: (T) -> ModuleEntity?,
        ): Boolean = changes.any { change ->
            when (change) {
                is EntityChange.Added -> false
                is EntityChange.Removed -> toModule(change.oldEntity)?.findModule(event.storageBefore) != null
                is EntityChange.Replaced -> toModule(change.newEntity)?.findModule(event.storageAfter) != null
            }
        }

        return hasRelevantChange(event.getChanges<ModuleEntity>()) { it } ||
                hasRelevantChange(event.getChanges<JavaModuleSettingsEntity>()) { it.module }
    }

    private fun hasContentRootChanges(event: VersionedStorageChange): Boolean =
        hasContentRootEntityChanges(event) ||
                hasSourceRootEntityChanges(event) ||
                hasJavaSourceRootPropertiesEntityChanges(event)

    private fun hasContentRootEntityChanges(event: VersionedStorageChange): Boolean =
        event.getChanges<ContentRootEntity>().any {
            event.hasChangedModule(it.oldEntity, it.newEntity)
        }

    private fun hasSourceRootEntityChanges(event: VersionedStorageChange): Boolean =
        event.getChanges<SourceRootEntity>().any {
            event.hasChangedModule(it.oldEntity?.contentRoot, it.newEntity?.contentRoot)
        }

    private fun hasJavaSourceRootPropertiesEntityChanges(event: VersionedStorageChange): Boolean =
        event.getChanges<JavaSourceRootPropertiesEntity>().any {
            event.hasChangedModule(it.oldEntity?.sourceRoot?.contentRoot, it.newEntity?.sourceRoot?.contentRoot)
        }

    private fun VersionedStorageChange.hasChangedModule(
        contentRootBefore: ContentRootEntity?,
        contentRootAfter: ContentRootEntity?,
    ): Boolean = hasChangedModule(
        contentRootBefore,
        contentRootAfter,
        moduleSelector = ContentRootEntity::module,
    )

    private fun hasFacetChanges(event: VersionedStorageChange): Boolean =
        hasFacetEntityChanges(event) || hasKotlinSettingsEntityChanges(event)

    private fun hasFacetEntityChanges(event: VersionedStorageChange): Boolean =
        event.getChanges<FacetEntity>().any {
            event.hasChangedModule(
                oldEntity = it.oldEntity,
                newEntity = it.newEntity,
                entityFilter = FacetEntity::isKotlinFacet,
                moduleSelector = FacetEntity::module,
            )
        }

    private fun hasKotlinSettingsEntityChanges(event: VersionedStorageChange): Boolean =
        event.getChanges<KotlinSettingsEntity>().any {
            event.hasChangedModule(
                oldEntity = it.oldEntity,
                newEntity = it.newEntity,
                entityFilter = KotlinSettingsEntity::isKotlinFacet,
                moduleSelector = KotlinSettingsEntity::module,
            )
        }

    private fun <T : WorkspaceEntity> VersionedStorageChange.hasChangedModule(
        oldEntity: T?,
        newEntity: T?,
        entityFilter: (T) -> Boolean = alwaysTrue(),
        moduleSelector: (T) -> ModuleEntity?,
    ): Boolean {
        val oldModule = oldEntity?.takeIf(entityFilter)?.let(moduleSelector)?.findModule(storageBefore)
        if (oldModule != null) return true

        val newModule = newEntity?.takeIf(entityFilter)?.let(moduleSelector)?.findModule(storageAfter)
        return newModule != null
    }

    /**
     * Resets the [projectFileEventPublishingState] at write action boundaries so that each write action starts with a clean state.
     */
    private inner class EventPublishingStateResetWriteActionListener : WriteActionListener {
        /**
         * Tracks the depth of the current write action so that states are only reset when entering/exiting the outermost write action.
         *
         * The counter is only accessed during write actions, so it is not used concurrently. However, it might be accessed from multiple
         * threads in sequence, so it should still be volatile.
         */
        @Volatile
        private var writeActionDepth = 0

        override fun writeActionStarted(action: Class<*>) {
            if (writeActionDepth == 0) {
                // Defensive cleanup: Ensure we start a write action with a clean state in case something went wrong previously.
                projectFileEventPublishingState = EventPublishingState.NONE
            }

            writeActionDepth += 1
        }

        override fun writeActionFinished(action: Class<*>) {
            writeActionDepth -= 1

            if (writeActionDepth == 0) {
                projectFileEventPublishingState = EventPublishingState.NONE
            }
        }
    }

    /**
     * Resets the [projectFileEventPublishingState] around `analyze` calls within a write action. After `analyze` populates caches, further
     * modifications in the same write action need to be able to invalidate those caches again.
     */
    internal class EventPublishingStateResetAnalysisInWriteActionListener(
        private val project: Project,
    ) : KotlinAnalysisInWriteActionListener {
        private val modificationService: FirIdeModuleStateModificationService
            get() = getInstance(project)

        override fun onEnteringAnalysisInWriteAction() {
            // Defensive: Reset state when entering analysis in case modifications happen during the `analyze` call.
            modificationService.projectFileEventPublishingState = EventPublishingState.NONE
        }

        override fun afterLeavingAnalysisInWriteAction() {
            // Primary: Caches may have been filled during `analyze`, so further modifications need to be able to invalidate caches again.
            modificationService.projectFileEventPublishingState = EventPublishingState.NONE
        }
    }

    override fun dispose() {}

    companion object {
        fun getInstance(project: Project): FirIdeModuleStateModificationService =
            project.getService(FirIdeModuleStateModificationService::class.java)
    }
}

/**
 * Tracks which level of global modification event has been published in the current write action.
 *
 * @see FirIdeModuleStateModificationService.projectFileEventPublishingState
 */
private enum class EventPublishingState {
    /**
     * No global modification event has been published in the current write action.
     */
    NONE,

    /**
     * A global (full) module state modification event has been published in the current write action.
     */
    GLOBAL_EVENT_PUBLISHED,
}
