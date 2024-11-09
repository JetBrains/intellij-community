// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ContentRootData
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.IExternalSystemSourceType
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioPathPrefixTreeFactory
import com.intellij.openapi.util.io.toCanonicalPath
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.ExternalSourceSet
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import java.io.File
import java.nio.file.Path

class GradleContentRootIndex {

  private val contentRootWeightMap = HashMap<Path, Int>()

  fun addSourceRoots(sourceSet: ExternalSourceSet) {
    val sources = getSources(sourceSet)
    addSourceRoots(sources)
  }

  fun addSourceRoots(sourceSetNode: DataNode<GradleSourceSetData>) {
    val sources = getSources(sourceSetNode)
    addSourceRoots(sources)
  }

  @VisibleForTesting
  @ApiStatus.Internal
  fun addSourceRoots(sources: Map<out IExternalSystemSourceType, Collection<Path>>) {
    val contentRootPaths = HashSet<Path>()
    for (sourceRootPaths in sources.values) {
      for (sourceRootPath in sourceRootPaths) {
        contentRootPaths.addAll(resolveParentPaths(sourceRootPath))
      }
    }
    for (contentRootPath in contentRootPaths) {
      val contentRootWeight = contentRootWeightMap.getOrDefault(contentRootPath, 0)
      contentRootWeightMap[contentRootPath] = contentRootWeight + 1
    }
  }

  fun resolveContentRoots(externalProject: ExternalProject, sourceSet: ExternalSourceSet): Set<Path> {
    val sources = getSources(sourceSet)
    return resolveContentRoots(externalProject, sources)
  }

  fun resolveContentRoots(externalProject: ExternalProject, sourceSetNode: DataNode<GradleSourceSetData>): Set<String> {
    val sources = getSources(sourceSetNode)
    val contentRoots = resolveContentRoots(externalProject, sources)
    return contentRoots.mapTo(HashSet()) { it.toCanonicalPath() }
  }

  @VisibleForTesting
  @ApiStatus.Internal
  fun resolveContentRoots(
    externalProject: ExternalProject,
    sources: Map<out IExternalSystemSourceType, Collection<Path>>,
  ): Set<Path> {

    val projectRootPath = externalProject.projectDir.toPath()
    val buildRootPath = externalProject.buildDir.toPath()

    val contentRootPaths = NioPathPrefixTreeFactory.createSet()
    for (sourceDirectorySet in sources.values) {
      for (sourceRootPath in sourceDirectorySet) {
        val contentRootPath = resolveContentRoot(projectRootPath, buildRootPath, sourceRootPath)
        contentRootPaths.add(contentRootPath)
      }
    }
    return contentRootPaths.getRoots()
  }

  private fun resolveContentRoot(projectRootPath: Path, buildRootPath: Path, sourceRootPath: Path): Path {
    if (!sourceRootPath.startsWith(projectRootPath)) {
      return sourceRootPath
    }
    if (sourceRootPath.startsWith(buildRootPath)) {
      return sourceRootPath
    }
    val contentRootPath = sourceRootPath.parent
    if (contentRootPath == null || contentRootPath == projectRootPath) {
      return sourceRootPath
    }
    val contentRootWeight = contentRootWeightMap[contentRootPath]
    if (contentRootWeight == null || contentRootWeight > 1) {
      return sourceRootPath
    }
    return contentRootPath
  }

  private fun resolveParentPaths(path: Path): List<Path> {
    val result = ArrayList<Path>()
    var parentPath: Path? = path
    while (parentPath != null) {
      result.add(parentPath)
      parentPath = parentPath.parent
    }
    return result
  }

  private fun getSources(
    sourceSet: ExternalSourceSet,
  ): Map<IExternalSystemSourceType, Collection<Path>> {
    return sourceSet.sources.mapValues { it.value.srcDirs.map(File::toPath) }
  }

  private fun getSourceRoots(
    sourceSetNode: DataNode<GradleSourceSetData>,
  ): Map<IExternalSystemSourceType, Collection<ContentRootData.SourceRoot>> {
    val sources = HashMap<IExternalSystemSourceType, HashSet<ContentRootData.SourceRoot>>()
    for (contentRootNode in ExternalSystemApiUtil.findAll(sourceSetNode, ProjectKeys.CONTENT_ROOT)) {
      for (sourceRootType in ExternalSystemSourceType.entries) {
        sources.computeIfAbsent(sourceRootType) { HashSet() }
          .addAll(contentRootNode.data.getPaths(sourceRootType))
      }
    }
    return sources
  }

  private fun getSources(
    sourceSetNode: DataNode<GradleSourceSetData>,
  ): Map<IExternalSystemSourceType, Collection<Path>> {
    return getSourceRoots(sourceSetNode).mapValues { (_, sourceRoots) ->
      sourceRoots.map { sourceRoot ->
        Path.of(FileUtil.toSystemDependentName(sourceRoot.path))
      }
    }
  }
}