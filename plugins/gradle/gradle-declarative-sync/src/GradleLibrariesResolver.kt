// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.declarativeSync

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.model.ProjectBuildModelImpl
import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncProjectConfigurator.project
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleDeclarativeEntitySource
import java.nio.file.Path

/**
 * Adds library roots that can be found in GRADLE_USER_HOME cache to the workspace model
 * it works only with direct library dependencies for now
 */
class GradleLibrariesResolver {
  suspend fun resolveAndAddLibraries(storage: MutableEntityStorage, context: ProjectResolverContext,
                                    entitySource: GradleDeclarativeEntitySource, projectBuildModel: ProjectBuildModelImpl) {
    val gradleHomeCache = getGradleCacheVirtualFileUrl(context)
    if(gradleHomeCache != null) {
      val allDependencies = projectBuildModel.allIncludedBuildModels.flatMap { it.javaApplication().dependencies().artifacts() }.toSet()
        .plus(projectBuildModel.allIncludedBuildModels.flatMap { it.javaApplication().testing().dependencies().artifacts() }.toSet())
        .map { Pair(it, findLibraryRootVirtualFile(gradleHomeCache, it)) }
        .filter { it.second != null }.toSet() // discard libraries that weren't found in the cache

      // TODO library roots have .module (actually json format) files that contain their dependencies
      // TODO search the dependency tree for all dependencies
      //val queue: Queue<Pair<ArtifactDependencyModel, VirtualFile?>> = java.util.ArrayDeque(allDependencies)

      allDependencies.map {
        val libRoot = findLibraryRoot(it.second!!, context.project().workspaceModel.getVirtualFileUrlManager())
        if(libRoot != null) {
          val libEntity = LibraryEntity("Gradle: " + it.first.compactNotation(), LibraryTableId.ProjectLibraryTableId,
                                        listOf(libRoot), entitySource) {
            typeId = LibraryTypeId("java-imported")
          }
          storage addEntity libEntity
          storage addEntity LibraryPropertiesEntity(entitySource) {
            propertiesXmlTag = "<properties groupId=\"${it.first.group()}\" artifactId=\"${it.first.name()}\"" +
                               " version=\"${it.first.version()}\" baseVersion=\"${it.first.version()}\" />"
            library = libEntity
          }
        }
      }
    }
  }

  private suspend fun getGradleCacheVirtualFileUrl(context: ProjectResolverContext): VirtualFileUrl? {
    val gh = context.settings.gradleHome
    if(gh == null) return null
    var gradleHome = Path.of(gh)
    while(!gradleHome.endsWith(".gradle")) {
      if(gradleHome.parent == null) return null
      gradleHome = gradleHome.parent
    }
    return gradleHome.resolve("caches/modules-2/files-2.1").toVirtualFileUrl(context.project().workspaceModel.getVirtualFileUrlManager())
  }

  private fun findLibraryRootVirtualFile(gradleHomeCache: VirtualFileUrl, library: ArtifactDependencyModel): VirtualFile? {
    val group = library.group().toString()
    val name = library.name().toString()
    val version = library.version().toString()
    val libraryRoot = gradleHomeCache.append(group).append(name).append(version).virtualFile
    if(libraryRoot == null || !libraryRoot.isDirectory) return null
    return libraryRoot
  }

  private fun findLibraryRoot(libraryRootVirtualFile: VirtualFile, virtualFileUrlManager: VirtualFileUrlManager): LibraryRoot? {
    val subDirs = VfsUtil.getChildren(libraryRootVirtualFile)
    for(subDir in subDirs) {
      for(file in subDir.children) {
        if(file.isFile && file.fileType == ArchiveFileType.INSTANCE) {
          return LibraryRoot(virtualFileUrlManager.getOrCreateFromUrl(VfsUtil.getUrlForLibraryRoot(file.toNioPath())), LibraryRootTypeId("CLASSES"))
        }
      }
    }
    return null
  }
}