// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.sources

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.jarFinder.InternetAttachSourceProvider.attachSourceJar
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.future.await
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.execution.target.maybeGetLocalValue
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.service.cache.GradleLocalCacheHelper
import org.jetbrains.plugins.gradle.util.GradleArtifactDownloader
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.isValidJar
import java.nio.file.Path
import java.util.*

object GradleLibrarySourcesDownloader {

  suspend fun canDownloadSources(project: Project, file: VirtualFile): Boolean {
    if (!file.isClassFile()) {
      return false
    }
    val entries = findLibraryEntriesForFile(project, file)
    if (entries.isEmpty()) {
      return false
    }
    return canDownloadSources(entries)
  }

  fun canDownloadSources(orderEntries: List<LibraryOrderEntry>): Boolean {
    return findAssociatedGradleModule(orderEntries) != null
  }

  suspend fun download(project: Project, file: VirtualFile): Path? {
    if (!file.isClassFile()) {
      return null
    }
    val libraryEntries = findLibraryEntriesForFile(project, file)
    return download(project, libraryEntries)
  }

  suspend fun download(project: Project, orderEntries: List<LibraryOrderEntry>): Path? {
    if (orderEntries.isEmpty()) {
      return null
    }
    val module = findAssociatedGradleModule(orderEntries) ?: return null
    val gradleModuleData = CachedModuleDataFinder.getGradleModuleData(module) ?: return null
    val externalProjectPath = gradleModuleData.directoryToRunTask
    val libraryOrderEntry = orderEntries.first()
    val sourceArtifactNotation = libraryOrderEntry.getArtifactCoordinates()?.let { "$it:sources" } ?: return null
    val cachedSourcesPath = lookupSourcesPathFromCache(libraryOrderEntry, sourceArtifactNotation, project, externalProjectPath)
    if (cachedSourcesPath != null && isValidJar(cachedSourcesPath)) {
      attachSources(cachedSourcesPath, orderEntries)
      return cachedSourcesPath
    }
    val path = GradleArtifactDownloader.downloadArtifact(
      project,
      GradleBundle.message("gradle.action.download.sources"),
      sourceArtifactNotation,
      externalProjectPath
    ).await()
    attachSources(path, orderEntries)
    return path
  }

  private suspend fun attachSources(sourcesJar: Path, orderEntries: List<LibraryOrderEntry>) {
    edtWriteAction {
      val libraries = HashSet<Library>()
      orderEntries.forEach {
        ContainerUtil.addIfNotNull(libraries, it.library)
      }
      attachSourceJar(sourcesJar, libraries)
    }
  }

  private fun lookupSourcesPathFromCache(
    libraryOrderEntry: LibraryOrderEntry,
    sourceArtifactNotation: String,
    project: Project,
    projectPath: String,
  ): Path? {
    val rootFiles = libraryOrderEntry.getRootFiles(OrderRootType.CLASSES)
    if (rootFiles.size == 0) {
      return null
    }
    val buildLayoutParameters = GradleInstallationManager.getInstance().guessBuildLayoutParameters(project, projectPath)
    val gradleUserHome = buildLayoutParameters.gradleUserHomePath.maybeGetLocalValue()
    if (gradleUserHome == null) {
      return null
    }
    val filePath = Path.of(rootFiles[0].path)
    if (!filePath.startsWith(gradleUserHome)) {
      return null
    }
    val coordinates = getLibraryUnifiedCoordinates(sourceArtifactNotation)
    if (coordinates == null) {
      return null
    }
    val localArtifacts = GradleLocalCacheHelper.findArtifactComponents(
      coordinates,
      gradleUserHome,
      EnumSet.of(LibraryPathType.SOURCE)
    )
    return localArtifacts[LibraryPathType.SOURCE]?.firstOrNull()
  }

  private fun findAssociatedGradleModule(libraryOrderEntries: List<LibraryOrderEntry>): Module? {
    return libraryOrderEntries
      .filter { !it.isModuleLevel }
      .map { it.ownerModule }
      .find { ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, it) }
  }

  private fun VirtualFile.isClassFile(): Boolean {
    val registry = FileTypeRegistry.getInstance()
    return registry.isFileOfType(this, JavaClassFileType.INSTANCE)
  }

  private suspend fun findLibraryEntriesForFile(project: Project, file: VirtualFile): List<LibraryOrderEntry> {
    return readAction {
      val index = ProjectFileIndex.getInstance(project)
      return@readAction index.getOrderEntriesForFile(file)
        .filterIsInstance<LibraryOrderEntry>()
        .toList()
    }
  }
}
