// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.modifyLibraryEntity
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.util.PathUtil
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.base.platforms.detectLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.isKlibLibraryRootForPlatform
import org.jetbrains.kotlin.idea.base.platforms.platform
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.NativeKlibLibraryInfo
import org.jetbrains.kotlin.idea.base.util.caching.getChanges
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.impl.NativeIdePlatformKind
import java.io.File

/**
 * Service for background generation of K/N forward declaration files on workspace model updates.
 * Detected updates in relevant KLIB libraries trigger regeneration of synthetic Kotlin files.
 * Generated roots are stored inside a child [KotlinForwardDeclarationsWorkspaceEntity] of the affected library.
 *
 * @see [KotlinForwardDeclarationsFileGenerator]
 */
@Suppress("LightServiceMigrationCode") // K2-only service
internal class KotlinForwardDeclarationsModelChangeService(private val project: Project, cs: CoroutineScope) {
    init {
        if (shouldRunForwardDeclarationServices()) {
            cs.launch {
                WorkspaceModel.getInstance(project).eventLog.collect { event ->
                    val fwdDeclarationChanges = event.getChanges<KotlinForwardDeclarationsWorkspaceEntity>()
                    cleanUp(fwdDeclarationChanges)

                    val libraryChanges = event.getChanges<LibraryEntity>().ifEmpty { return@collect }

                    val nativeKlibLibraryInfos: Map<LibraryEntity, NativeKlibLibraryInfo> =
                        libraryChanges.toNativeKLibraryInfos(event.storageAfter).ifEmpty { return@collect }
                    val workspaceModel = WorkspaceModel.getInstance(project)
                    val createEntityStorageChanges = createEntityStorageChanges(workspaceModel, nativeKlibLibraryInfos)

                    cs.launch {
                        workspaceModel.update("Kotlin Forward Declarations workspace model update") { storage ->
                            createEntityStorageChanges.forEach { (libraryEntity, builder) ->

                                // a hack to bypass workspace model issues; without the extra check entity updates lead to recursion
                                if (libraryEntity.kotlinForwardDeclarationsWorkspaceEntity == null) {
                                    storage.modifyLibraryEntity(libraryEntity) {
                                        this.kotlinForwardDeclarationsWorkspaceEntity = builder
                                    }
                                    storage.addEntity(builder)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun cleanUp(fwdDeclarationChanges: List<EntityChange<KotlinForwardDeclarationsWorkspaceEntity>>) {
        val roots = fwdDeclarationChanges.flatMap { it.oldEntity?.forwardDeclarationRoots.orEmpty() }.map { File(it.presentableUrl) }
        KotlinForwardDeclarationsFileGenerator.cleanUp(roots)
    }

    private fun createEntityStorageChanges(
        workspaceModel: WorkspaceModel,
        nativeKlibLibraryInfos: Map<LibraryEntity, NativeKlibLibraryInfo>
    ): Map<LibraryEntity, KotlinForwardDeclarationsWorkspaceEntity.Builder> {
        val virtualFileUrlManager = workspaceModel.getVirtualFileUrlManager()
        return buildMap {
            for ((libraryEntity, klibInfo) in nativeKlibLibraryInfos) {
                val path = KotlinForwardDeclarationsFileGenerator.generateForwardDeclarationFiles(klibInfo)
                val virtualFileUrl = path?.toVirtualFileUrl(virtualFileUrlManager) ?: continue

                val entity = KotlinForwardDeclarationsWorkspaceEntity(
                    setOf(virtualFileUrl),
                    KotlinFwdWorkspaceEntitySource
                )

                put(libraryEntity, entity)
            }
        }
    }

    private fun List<EntityChange<LibraryEntity>>.toNativeKLibraryInfos(
        storageAfter: ImmutableEntityStorage
    ): Map<LibraryEntity, NativeKlibLibraryInfo> {
        val libraryEntityChanges = this
        return buildMap {
            for (entityChange in libraryEntityChanges) {
                val newLibraryEntity: LibraryEntity = entityChange.newEntity ?: continue
                val library = newLibraryEntity.findLibraryBridge(storageAfter) as? LibraryEx ?: continue
                val nativeRootsAfterChange = library.getClassRootsIfNative()

                for (classRoot in nativeRootsAfterChange) {
                    val path = PathUtil.getLocalPath(classRoot) ?: continue
                    put(newLibraryEntity, NativeKlibLibraryInfo(project, library, path))
                }
            }
        }
    }

    private fun LibraryEx.getClassRootsIfNative(): List<VirtualFile> {
        if (detectLibraryKind(this, project)?.platform?.idePlatformKind != NativeIdePlatformKind) return emptyList()

        return getFiles(OrderRootType.CLASSES).filter {
            it.isKlibLibraryRootForPlatform(NativeIdePlatformKind.defaultPlatform)
        }
    }
}

/**
 * Request [KotlinForwardDeclarationsModelChangeService] on startup to start receiving workspace model update events.
 */
internal class KotlinForwardDeclarationsStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (!shouldRunForwardDeclarationServices()) return

        project.service<KotlinForwardDeclarationsModelChangeService>()
    }
}

// We can't set the the `kotlin.k2.kmp.enabled` registry property before the Kotlin plugin is loaded.
// Without the property, the forward declaration services are no-ops.
// In tests, it is hard (if at all possible) to catch the moment between Kotlin plugin loading and startup activities launching.
// This is why the switch is disabled in the test environment.
// Since changing the property requires an IDE reload, this problem doesn't exist in the production environment.
private fun shouldRunForwardDeclarationServices(): Boolean =
    Registry.`is`("kotlin.k2.kmp.enabled") || isUnitTestMode()
