// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.modifyLibraryEntity
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.util.PathUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.base.platforms.detectLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.isKlibLibraryRootForPlatform
import org.jetbrains.kotlin.idea.base.platforms.platform
import org.jetbrains.kotlin.idea.base.util.caching.getChanges
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
@Service(Service.Level.PROJECT)
internal class KotlinForwardDeclarationsModelChangeService(private val project: Project, cs: CoroutineScope) {
    init {
        cs.launch {
            WorkspaceModel.getInstance(project).eventLog.collect { event ->
                val fwdDeclarationChanges = event.getChanges<KotlinForwardDeclarationsWorkspaceEntity>()
                cleanUp(fwdDeclarationChanges)

                val libraryChanges = event.getChanges<LibraryEntity>().ifEmpty { return@collect }

                val nativeKlibs: Map<LibraryEntity, KLibRoot> =
                    libraryChanges.toNativeKLibs().ifEmpty { return@collect }
                val workspaceModel = WorkspaceModel.getInstance(project)
                val createEntityStorageChanges = createEntityStorageChanges(workspaceModel, nativeKlibs)

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

    private fun cleanUp(fwdDeclarationChanges: List<EntityChange<KotlinForwardDeclarationsWorkspaceEntity>>) {
        val roots = fwdDeclarationChanges.flatMap { it.oldEntity?.forwardDeclarationRoots.orEmpty() }.map { File(it.presentableUrl) }
        KotlinForwardDeclarationsFileGenerator.cleanUp(roots)
    }

    private fun createEntityStorageChanges(
        workspaceModel: WorkspaceModel,
        nativeKlibs: Map<LibraryEntity, KLibRoot>
    ): Map<LibraryEntity, KotlinForwardDeclarationsWorkspaceEntityBuilder> {
        val virtualFileUrlManager = workspaceModel.getVirtualFileUrlManager()
        return buildMap {
            for ((libraryEntity, klib) in nativeKlibs) {
                val path = KotlinForwardDeclarationsFileGenerator.generateForwardDeclarationFiles(klib)
                val virtualFileUrl = path?.toVirtualFileUrl(virtualFileUrlManager) ?: continue

                val entity = KotlinForwardDeclarationsWorkspaceEntity(
                    setOf(virtualFileUrl),
                    KotlinFwdWorkspaceEntitySource
                )

                put(libraryEntity, entity)
            }
        }
    }

    private fun List<EntityChange<LibraryEntity>>.toNativeKLibs(): Map<LibraryEntity, KLibRoot> {
        val libraryEntityChanges = this
        return buildMap {
            for (entityChange in libraryEntityChanges) {
                val newLibraryEntity: LibraryEntity = entityChange.newEntity ?: continue
                val nativeRootsAfterChange = newLibraryEntity.getClassRootsIfNative()

                for (classRoot in nativeRootsAfterChange) {
                    val path = PathUtil.getLocalPath(classRoot) ?: continue
                    put(newLibraryEntity, KLibRoot(path))
                }
            }
        }
    }

    private fun LibraryEntity.getClassRootsIfNative(): List<VirtualFile> {
        if (detectLibraryKind(this, project)?.platform?.idePlatformKind != NativeIdePlatformKind) return emptyList()

        return roots.asSequence()
            .filter { it.type == LibraryRootTypeId.COMPILED }
            .mapNotNull { it.url.virtualFile }
            .filter { it.isKlibLibraryRootForPlatform(NativeIdePlatformKind.defaultPlatform) }
            .toList()
    }
}

/**
 * Request [KotlinForwardDeclarationsModelChangeService] on startup to start receiving workspace model update events.
 */
internal class KotlinForwardDeclarationsStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<KotlinForwardDeclarationsModelChangeService>()
    }
}