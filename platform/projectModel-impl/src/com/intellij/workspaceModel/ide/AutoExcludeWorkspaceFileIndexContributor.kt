// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.eel.provider.withPrefetchedStats
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.core.fileIndex.DependencyDescription
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path

/**
 * Exclude worktrees created by AI agents.
 */
internal class AutoExcludeWorkspaceFileIndexContributor : WorkspaceFileIndexContributor<ProjectRootEntity> {
  override val entityClass: Class<ProjectRootEntity>
    get() = ProjectRootEntity::class.java

  override fun registerFileSets(entity: ProjectRootEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    val directories = Registry.get("ide.workspace.model.relative.paths.to.exclude.automatically").asString()
      .split(";").map { it.trim() }.filter { it.isNotEmpty() && it != "." }

    registerExcludedRoots(directories.map { entity.root.append(it) }, entity, registrar)
  }
}


internal class AutoExcludeInContentRootsWorkspaceFileIndexContributor : WorkspaceFileIndexContributor<ContentRootEntity> {
  override val entityClass: Class<ContentRootEntity>
    get() = ContentRootEntity::class.java

  override val dependenciesOnOtherEntities: List<DependencyDescription<ContentRootEntity>>
    get() = listOf(DependencyDescription.OnParent(ModuleEntity::class.java) { it.contentRoots.asSequence() })

  override fun registerFileSets(entity: ContentRootEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    val directories = Registry.get("ide.workspace.model.relative.paths.to.exclude.automatically").asString()
      .split(";").map { it.trim() }.filter { it.isNotEmpty() && it != "." }

    if (entity.module.type?.name != "JAVA_MODULE") { // There can be too many java modules in a project. Also, java projects should be covered by ProjectRootEntity
      registerExcludedRoots(directories.map { entity.url.append(it) }, entity, registrar)
    }
  }
}

/**
 * A remote VFS stats every component of a URL it does not hold, one RPC at a time.
 * One batched stat of the URLs answers these lookups from the cache.
 */
private fun registerExcludedRoots(urls: List<VirtualFileUrl>, entity: WorkspaceEntity, registrar: WorkspaceFileSetRegistrar) {
  withPrefetchedStats(urls.mapNotNull { it.toLocalFileSystemPathOrNull() }) {
    for (url in urls) {
      registrar.registerExcludedRoot(url, entity)
    }
  }
}

/** The nio path of a `file://` URL. Another protocol, such as the `temp://` of a light test, is not a remote path and gives null. */
@ApiStatus.Internal
@VisibleForTesting
fun VirtualFileUrl.toLocalFileSystemPathOrNull(): Path? {
  if (VirtualFileManager.extractProtocol(url) != StandardFileSystems.FILE_PROTOCOL) return null
  return VirtualFileManager.extractPath(url).toNioPathOrNull()
}
