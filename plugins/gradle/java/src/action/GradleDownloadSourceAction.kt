// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.action

import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.codeInsight.AttachSourcesProvider.AttachSourcesAction
import com.intellij.jarFinder.InternetAttachSourceProvider.attachSourceJar
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.ActionCallback
import com.intellij.psi.PsiFile
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.execution.target.maybeGetLocalValue
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.service.cache.GradleLocalCacheHelper
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleDependencySourceDownloader
import org.jetbrains.plugins.gradle.util.isValidJar
import java.io.File
import java.nio.file.Path
import java.util.*
import java.util.function.Predicate
import java.util.function.Supplier

class GradleDownloadSourceAction(
  private val orderEntries: List<LibraryOrderEntry>,
  private val psiFile: PsiFile,
  private val gradleModuleProvider: Supplier<Map<LibraryOrderEntry, Module>>
) : AttachSourcesAction {

  companion object {
    private const val ANDROID_LIBRARY_SUFFIX = "@aar"

    @JvmStatic
    @VisibleForTesting
    fun getSourceArtifactNotation(
      artifactCoordinates: String,
      artifactIdChecker: Predicate<String>
    ): String {
      return artifactCoordinates.split(":").let {
        when {
          it.size == 4 && artifactIdChecker.test(it[1]) -> it[0] + ":" + it[1] + ":" + it[3]
          it.size == 5 -> it[0] + ":" + it[1] + ":" + it[4]
          else -> artifactCoordinates
        }
      }.let {
        it.removeSuffix(ANDROID_LIBRARY_SUFFIX) + ":sources"
      }
    }
  }

  override fun getName(): String = GradleBundle.message("gradle.action.download.sources")

  override fun getBusyText(): String = GradleBundle.message("gradle.action.download.sources.busy.text")

  override fun perform(orderEntriesContainingFile: List<LibraryOrderEntry?>): ActionCallback {
    val gradleModules = gradleModuleProvider.get()
    if (gradleModules.isEmpty()) {
      return ActionCallback.REJECTED
    }
    val (libraryOrderEntry, module) = gradleModules.entries.first()
    val gradleModuleData = CachedModuleDataFinder.getGradleModuleData(module)
    if (gradleModuleData == null) {
      return ActionCallback.REJECTED
    }
    val externalProjectPath = gradleModuleData.directoryToRunTask
    val libraryName = libraryOrderEntry.getLibraryName()
    if (libraryName == null) {
      return ActionCallback.REJECTED
    }
    val artifactCoordinates = libraryName.removePrefix("${GradleConstants.SYSTEM_ID.getReadableName()}: ")
    if (libraryName == artifactCoordinates) {
      return ActionCallback.REJECTED
    }
    val sourceArtifactNotation = getSourceArtifactNotation(artifactCoordinates) { idCandidate ->
      isArtifactId(idCandidate, libraryOrderEntry)
    }
    val project = psiFile.project
    val cachedSourcesPath = lookupSourcesPathFromCache(libraryOrderEntry, sourceArtifactNotation, project, externalProjectPath)
    if (cachedSourcesPath != null && isValidJar(cachedSourcesPath)) {
      attachSources(cachedSourcesPath.toFile(), orderEntries)
      return ActionCallback.DONE
    }
    val executionResult = ActionCallback()
    GradleDependencySourceDownloader.downloadSources(project, name, sourceArtifactNotation, externalProjectPath)
      .whenComplete { path, error ->
        if (error != null) {
          executionResult.setRejected()
        }
        else {
          attachSources(path, orderEntries)
          executionResult.setDone()
        }
    }
    return executionResult
  }

  private fun attachSources(sourcesJar: File, orderEntries: List<LibraryOrderEntry>) = ApplicationManager.getApplication()
    .invokeLater {
      val libraries = HashSet<Library>()
      orderEntries.forEach {
        ContainerUtil.addIfNotNull(libraries, it.library)
      }
      attachSourceJar(sourcesJar, libraries)
    }

  private fun lookupSourcesPathFromCache(libraryOrderEntry: LibraryOrderEntry,
                                         sourceArtifactNotation: String,
                                         project: Project,
                                         projectPath: String): Path? {
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

  private fun getLibraryUnifiedCoordinates(sourceArtifactNotation: String): UnifiedCoordinates? =
    sourceArtifactNotation.replace(ANDROID_LIBRARY_SUFFIX, "")
      .split(":")
      .let { if (it.size < 3) null else UnifiedCoordinates(it[0], it[1], it[2]) }

  private fun isArtifactId(artifactIdCandidate: String, libraryOrderEntry: LibraryOrderEntry): Boolean {
    val rootFiles = libraryOrderEntry.getRootFiles(OrderRootType.CLASSES)
    return rootFiles.size == 0 || rootFiles.any { file -> file.getName().startsWith(artifactIdCandidate) }
  }
}