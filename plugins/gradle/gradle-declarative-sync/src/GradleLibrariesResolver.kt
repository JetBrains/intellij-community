// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.declarativeSync

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.model.ProjectBuildModelImpl
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import java.nio.file.Path

/**
 * Adds library roots that can be found in GRADLE_USER_HOME cache to the workspace model
 * it works only with direct library dependencies for now
 */
internal class GradleLibrariesResolver {

  data class LibDepData(val group: String, val name: String, val version: String) {
    constructor(artifact: ArtifactDependencyModel): this(
      artifact.group().toString(),
      artifact.name().toString(),
      if(artifact.version().valueType == GradlePropertyModel.ValueType.NONE) "" else artifact.version().toString()
    )
    fun compactNotation(): String {
      return if(version == "") "$group:$name" else "$group:$name:$version"
    }
  }

  fun resolveAndAddLibraries(
    project: Project,
    storage: MutableEntityStorage,
    context: ProjectResolverContext,
    entitySource: EntitySource,
    projectBuildModel: ProjectBuildModelImpl,
  ): Map<LibDepData, List<LibDepData>> {
    val originalDependencies = projectBuildModel.allIncludedBuildModels.flatMap { it.javaApplication().dependencies().artifacts() }
      .plus(projectBuildModel.allIncludedBuildModels.flatMap { it.javaApplication().testing().dependencies().artifacts() })
      .map { LibDepData(it) }.distinct()

    val result = hashMapOf<LibDepData, MutableList<LibDepData>>()
    result.putAll(originalDependencies.map { Pair(it, mutableListOf<LibDepData>()) })
    val gradleHomeCache = getGradleCacheVirtualFileUrl(project, context)
    if(gradleHomeCache != null) {

      val libraryRootFoundDependencies = if(Registry.`is`("gradle.declarative.preimport.add.transitive.library.dependencies", false))
        findTransitiveDependencies(originalDependencies, gradleHomeCache, result)
      else originalDependencies.map { Pair(it, findLibraryRootDirectory(gradleHomeCache, it)) }.filter { it.second != null }

      libraryRootFoundDependencies.map {
        addLibraryRoot(project, it, entitySource, storage)
      }
    }
    return result
  }

  private fun addLibraryRoot(
    project: Project,
    pair: Pair<LibDepData, VirtualFile?>,
    entitySource: EntitySource,
    storage: MutableEntityStorage,
  ) {
    val libRoot = findLibraryRootJar(pair.second!!, project.workspaceModel.getVirtualFileUrlManager())
    if (libRoot != null) {
      val libEntity = LibraryEntity("Gradle: " + pair.first.compactNotation(), LibraryTableId.ProjectLibraryTableId,
                                    listOf(libRoot), entitySource) {
        typeId = LibraryTypeId("java-imported")
      }
      storage addEntity libEntity
      storage addEntity LibraryPropertiesEntity(entitySource) {
        propertiesXmlTag = "<properties groupId=\"${pair.first.group}\" artifactId=\"${pair.first.name}\"" +
                           " version=\"${pair.first.version}\" baseVersion=\"${pair.first.version}\" />"
        library = libEntity
      }
    }
  }

  private fun findTransitiveDependencies(
    originalDependencies: List<LibDepData>,
    gradleHomeCache: VirtualFileUrl,
    result: HashMap<LibDepData, MutableList<LibDepData>>,
  ): MutableList<Pair<LibDepData, VirtualFile>> {
    val dependencyDependencies = hashMapOf<LibDepData, List<LibDepData>>()

    val newDependencies: ArrayDeque<LibDepData> = ArrayDeque(originalDependencies)
    val libraryRootFoundDependencies = mutableListOf<Pair<LibDepData, VirtualFile>>() // all dependencies for which their library root directory exists
    val consideredDependencies = hashSetOf<LibDepData>() // all dependencies that were considered
    while (newDependencies.isNotEmpty()) {
      val dep = newDependencies.removeFirst()
      if (consideredDependencies.contains(dep)) continue
      consideredDependencies.add(dep)
      val libraryRoot = findLibraryRootDirectory(gradleHomeCache, dep)
      if (libraryRoot == null) continue
      libraryRootFoundDependencies.add(Pair(dep, libraryRoot))
      val metadataFile = findLibraryRootGMM(libraryRoot)
      if (metadataFile == null) continue
      val metadataDependencies = getDependenciesGMM(metadataFile)
      if (!dependencyDependencies.containsKey(dep)) {
        dependencyDependencies[dep] = metadataDependencies
      }
      newDependencies.addAll(metadataDependencies.minus(consideredDependencies))
    }

    for (rootDep in result.keys) {
      val rootDepNewDependencies: ArrayDeque<LibDepData> = ArrayDeque(dependencyDependencies[rootDep] ?: emptyList())
      val rootDepConsideredDependencies = hashSetOf<LibDepData>()
      while (rootDepNewDependencies.isNotEmpty()) {
        val dep = rootDepNewDependencies.removeFirst()
        if (rootDepConsideredDependencies.contains(dep)) continue
        rootDepConsideredDependencies.add(dep)
        result[rootDep]?.add(dep)
        rootDepNewDependencies.addAll(dependencyDependencies[dep] ?: emptyList())
      }
    }
    return libraryRootFoundDependencies
  }

  private fun getGradleCacheVirtualFileUrl(project: Project, context: ProjectResolverContext): VirtualFileUrl? {
    val gh = context.settings.gradleHome
    if(gh == null) return null
    var gradleHome = Path.of(gh)
    while(!gradleHome.endsWith(".gradle")) {
      if(gradleHome.parent == null) return null
      gradleHome = gradleHome.parent
    }
    return gradleHome.resolve("caches/modules-2/files-2.1").toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager())
  }

  private fun findLibraryRootDirectory(gradleHomeCache: VirtualFileUrl, library: LibDepData): VirtualFile? {
    val group = library.group
    val name = library.name
    val version = library.version
    val libraryRoot = if(version != "") gradleHomeCache.append(group).append(name).append(version).virtualFile
      else null // TODO find newest? version
    if(libraryRoot == null || !libraryRoot.isDirectory) return null
    return libraryRoot
  }

  private fun findLibraryRootJar(libraryRootVirtualFile: VirtualFile, virtualFileUrlManager: VirtualFileUrlManager): LibraryRoot? {
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


  private fun findLibraryRootGMM(libraryRootVirtualFile: VirtualFile): VirtualFile? {
    val subDirs = VfsUtil.getChildren(libraryRootVirtualFile)
    for(subDir in subDirs) {
      for(file in subDir.children) {
        if(file.isFile && file.name.endsWith(".module")) {
          return file
        }
      }
    }
    return null
  }

  private fun getDependenciesGMM(file: VirtualFile): List<LibDepData> {
    val data = mutableListOf<LibDepData>()
    ObjectMapper().readTree(file.inputStream).let {
      val variants = it["variants"]
      if(variants != null) {
        for(variant in variants) {
          //TODO actually handle variants (compile, runtime)
          //val attributes = variant["attributes"]
          val dependencies = variant["dependencies"]
          if(dependencies != null) {
            for (dependency in dependencies) {
              val group = dependency["group"].asText()
              val name = dependency["module"].asText()
              var version = ""
              val versionObj = dependency["version"]
              if(versionObj != null) {
                val requires = versionObj["requires"]
                if(requires != null) {
                  version = requires.asText()
                } else {
                  // TODO implement prefers, strictly and rejects
                }
              }
              data.add(LibDepData(group, name, version))
            }
          }
        }
      }
    }
    return data
  }
}