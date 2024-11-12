// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys.CONTENT_ROOT
import com.intellij.openapi.externalSystem.model.project.ContentRootData
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
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
    val sourceRoots = getSourceRoots(sourceSet)
    addSourceRoots(sourceRoots)
  }

  fun addSourceRoots(sourceSetNode: DataNode<GradleSourceSetData>) {
    val sourceRoots = getSourceRoots(sourceSetNode)
    val contentRoots = getContentRoots(sourceSetNode)
    addSourceRoots(sourceRoots + contentRoots)
  }

  @VisibleForTesting
  @ApiStatus.Internal
  fun addSourceRoots(sourceRoots: Collection<Path>) {
    val contentRoots = HashSet<Path>()
    for (sourceRoot in sourceRoots) {
      contentRoots.addAll(resolveParentPaths(sourceRoot))
    }
    for (contentRoot in contentRoots) {
      val contentRootWeight = contentRootWeightMap.getOrDefault(contentRoot, 0)
      contentRootWeightMap[contentRoot] = contentRootWeight + 1
    }
  }

  fun resolveContentRoots(externalProject: ExternalProject, sourceSet: ExternalSourceSet): Set<Path> {
    val sourceRoots = getSourceRoots(sourceSet)
    return resolveContentRoots(externalProject, sourceRoots)
  }

  fun resolveContentRoots(externalProject: ExternalProject, sourceSetNode: DataNode<GradleSourceSetData>): Set<String> {
    val sourceRoots = getSourceRoots(sourceSetNode)
    val contentRoots = getContentRoots(sourceSetNode)
    return resolveContentRoots(externalProject, sourceRoots + contentRoots)
      .mapTo(HashSet()) { it.toCanonicalPath() }
  }

  @VisibleForTesting
  @ApiStatus.Internal
  fun resolveContentRoots(externalProject: ExternalProject, sourceRoots: Collection<Path>): Set<Path> {
    val projectRootPath = externalProject.projectDir.toPath()
    val buildRootPath = externalProject.buildDir.toPath()

    val contentRoots = NioPathPrefixTreeFactory.createSet()
    for (sourceRootPath in sourceRoots) {
      val contentRootPath = resolveContentRoot(projectRootPath, buildRootPath, sourceRootPath)
      contentRoots.add(contentRootPath)
    }
    return contentRoots.getRoots()
  }

  private fun resolveContentRoot(projectRootPath: Path, buildRootPath: Path, sourceRootPath: Path): Path {
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

  private fun getSourceRoots(sourceSet: ExternalSourceSet): Collection<Path> {
    return sourceSet.sources.values.flatMap { it.srcDirs.map(File::toPath) }
  }

  private fun getSourceRoots(sourceSetNode: DataNode<GradleSourceSetData>): Collection<Path> {
    val sourceRoots = HashSet<ContentRootData.SourceRoot>()
    for (contentRootNode in ExternalSystemApiUtil.findAll(sourceSetNode, CONTENT_ROOT)) {
      for (sourceRootType in ExternalSystemSourceType.entries) {
        sourceRoots.addAll(contentRootNode.data.getPaths(sourceRootType))
      }
    }
    return sourceRoots.map { sourceRoot ->
      Path.of(FileUtil.toSystemDependentName(sourceRoot.path))
    }
  }

  private fun getContentRoots(sourceSetNode: DataNode<GradleSourceSetData>): Collection<Path> {
    return ExternalSystemApiUtil.findAll(sourceSetNode, CONTENT_ROOT)
      .map { Path.of(FileUtil.toSystemDependentName(it.data.rootPath)) }
  }
}