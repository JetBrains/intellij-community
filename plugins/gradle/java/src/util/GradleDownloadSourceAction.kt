// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.codeInsight.AttachSourcesProvider.AttachSourcesAction
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.jarFinder.InternetAttachSourceProvider.attachSourceJar
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory
import com.intellij.openapi.externalSystem.service.notification.NotificationData
import com.intellij.openapi.externalSystem.service.notification.NotificationSource
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.util.containers.ContainerUtil
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.execution.target.maybeGetLocalValue
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.service.cache.GradleLocalCacheHelper
import org.jetbrains.plugins.gradle.service.execution.loadDownloadSourcesInitScript
import org.jetbrains.plugins.gradle.service.execution.loadLegacyDownloadSourcesInitScript
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.service.task.LazyVersionSpecificInitScript
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.File
import java.io.IOException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.EnumSet
import java.util.UUID
import java.util.function.Predicate
import java.util.function.Supplier
import kotlin.io.path.inputStream

class GradleDownloadSourceAction(
  private val orderEntries: List<LibraryOrderEntry>,
  private val psiFile: PsiFile,
  private val gradleModuleProvider: Supplier<Map<LibraryOrderEntry, Module>>
) : AttachSourcesAction {

  companion object {
    private const val INIT_SCRIPT_FILE_PREFIX = "ijDownloadSources"
    private const val ANDROID_LIBRARY_SUFFIX = "@aar"

    private val LOG = Logger.getInstance(GradleDownloadSourceAction::class.java)
    private val GRADLE_5_6 = GradleVersion.version("5.6")

    @JvmStatic
    @VisibleForTesting
    fun getSourceArtifactNotation(artifactCoordinates: String,
                                  artifactIdChecker: Predicate<String>): String = artifactCoordinates.split(":")
      .let {
        when {
          it.size == 4 -> if (artifactIdChecker.test(it[1])) it[0] + ":" + it[1] + ":" + it[3] else artifactCoordinates
          it.size == 5 -> it[0] + ":" + it[1] + ":" + it[4]
          else -> artifactCoordinates
        }
      }
      .let { it.removeSuffix(ANDROID_LIBRARY_SUFFIX) + ":sources" }
  }

  override fun getName(): @Nls(capitalization = Nls.Capitalization.Title) String = GradleBundle.message("gradle.action.download.sources")

  override fun getBusyText(): @NlsContexts.LinkLabel String = GradleBundle.message("gradle.action.download.sources.busy.text")

  override fun perform(orderEntriesContainingFile: List<LibraryOrderEntry?>): ActionCallback {
    val gradleModules = gradleModuleProvider.get()
    if (gradleModules.isEmpty()) {
      return ActionCallback.REJECTED
    }
    val (libraryOrderEntry, module) = gradleModules.entries.first()
    if (CachedModuleDataFinder.getGradleModuleData(module) == null) {
      return ActionCallback.REJECTED
    }
    val libraryName = libraryOrderEntry.getLibraryName()
    if (libraryName == null) {
      return ActionCallback.REJECTED
    }
    val artifactCoordinates = libraryName.removePrefix("${GradleConstants.SYSTEM_ID.getReadableName()}: ")
    if (libraryName == artifactCoordinates) {
      return ActionCallback.REJECTED
    }
    val externalProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module)
    if (externalProjectPath == null) {
      return ActionCallback.REJECTED
    }
    val sourceArtifactNotation = getSourceArtifactNotation(artifactCoordinates) { idCandidate ->
      isArtifactId(idCandidate, libraryOrderEntry)
    }
    val cachedSourcesPath = lookupSourcesPathFromCache(libraryOrderEntry, sourceArtifactNotation, psiFile.getProject(), externalProjectPath)
    if (cachedSourcesPath != null && isValidJar(cachedSourcesPath)) {
      attachSources(cachedSourcesPath.toFile(), orderEntries)
      return ActionCallback.DONE
    }
    return downloadSources(psiFile, sourceArtifactNotation, externalProjectPath)
  }

  fun downloadSources(psiFile: PsiFile, sourceArtifactNotation: String, externalProjectPath: String)
    : ActionCallback {
    var sourcesLocationFilePath: String
    var sourcesLocationFile: File
    try {
      sourcesLocationFile = File(FileUtil.createTempDirectory("sources", "loc"), "path.tmp")
      sourcesLocationFilePath = StringUtil.escapeBackSlashes(sourcesLocationFile.getCanonicalPath())
      Runtime.getRuntime().addShutdownHook(Thread({ FileUtil.delete(sourcesLocationFile) }, "GradleAttachSourcesProvider cleanup"))
    }
    catch (e: IOException) {
      LOG.warn(e)
      return ActionCallback.REJECTED
    }
    val project = psiFile.getProject()
    val taskName = "ijDownloadSources" + UUID.randomUUID().toString().substring(0, 12)
    val settings = ExternalSystemTaskExecutionSettings().also {
      it.executionName = getName()
      it.externalProjectPath = externalProjectPath
      it.taskNames = listOf(taskName)
      it.vmOptions = GradleSettings.getInstance(project).getGradleVmOptions()
      it.externalSystemIdString = GradleConstants.SYSTEM_ID.id
    }
    val userData = prepareUserData(sourceArtifactNotation, taskName, sourcesLocationFilePath)
    val resultWrapper = ActionCallback()

    val callback = object : TaskCallback {
      override fun onSuccess() {
        val sourceJar: File
        try {
          val downloadedArtifactPath = Path.of(FileUtil.loadFile(sourcesLocationFile))
          if (!isValidJar(downloadedArtifactPath)) {
            GradleLog.LOG.warn("Incorrect file header: $downloadedArtifactPath. Unable to process downloaded file as a JAR file")
            FileUtil.delete(sourcesLocationFile)
            resultWrapper.setRejected()
            return
          }
          sourceJar = downloadedArtifactPath.toFile()
          FileUtil.delete(sourcesLocationFile)
        }
        catch (e: IOException) {
          GradleLog.LOG.warn(e);
          resultWrapper.setRejected();
          return;
        }
        attachSources(sourceJar, orderEntries)
        resultWrapper.setDone()
      }

      override fun onFailure() {
        resultWrapper.setRejected()
        val title = GradleBundle.message("gradle.notifications.sources.download.failed.title")
        val message = GradleBundle.message("gradle.notifications.sources.download.failed.content", sourceArtifactNotation)
        val notification = NotificationData(title, message, NotificationCategory.WARNING, NotificationSource.PROJECT_SYNC)
        notification.setBalloonNotification(true)
        ExternalSystemNotificationManager.getInstance(project).showNotification(GradleConstants.SYSTEM_ID, notification)
      }
    }
    ExternalSystemUtil.runTask(settings, DefaultRunExecutor.EXECUTOR_ID, project, GradleConstants.SYSTEM_ID,
                               callback, ProgressExecutionMode.NO_PROGRESS_ASYNC, false, userData)
    return resultWrapper
  }

  private fun prepareUserData(sourceArtifactNotation: String, taskName: String, sourcesLocationFilePath: String): UserDataHolderBase {
    val legacyInitScript = LazyVersionSpecificInitScript(
      scriptSupplier = { loadLegacyDownloadSourcesInitScript(sourceArtifactNotation, taskName, sourcesLocationFilePath) },
      filePrefix = INIT_SCRIPT_FILE_PREFIX,
      isApplicable = { GRADLE_5_6 > it }
    )
    val initScript = LazyVersionSpecificInitScript(
      scriptSupplier = { loadDownloadSourcesInitScript(sourceArtifactNotation, taskName, sourcesLocationFilePath) },
      filePrefix = INIT_SCRIPT_FILE_PREFIX,
      isApplicable = { GRADLE_5_6 <= it }
    )
    return UserDataHolderBase().apply {
      putUserData(GradleTaskManager.VERSION_SPECIFIC_SCRIPTS_KEY, listOf(legacyInitScript, initScript))
    }
  }

  private fun attachSources(sourcesJar: File, orderEntries: List<LibraryOrderEntry>) = ApplicationManager.getApplication()
    .invokeLater {
      val libraries = HashSet<Library>()
      orderEntries.forEach {
        ContainerUtil.addIfNotNull(libraries, it.library)
      }
      attachSourceJar(sourcesJar, libraries)
    }

  private fun isValidJar(path: Path): Boolean = runCatching {
    path.inputStream(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
      .use {
        val head = it.readNBytes(2)
        if (head.size < 2) {
          return false
        }
        return head[0] == 0x50.toByte() && head[1] == 0x4b.toByte()
      }
  }.getOrNull() ?: false

  private fun lookupSourcesPathFromCache(libraryOrderEntry: LibraryOrderEntry,
                                         sourceArtifactNotation: String,
                                         project: Project,
                                         projectPath: String): Path? {
    val rootFiles = libraryOrderEntry.getRootFiles(OrderRootType.CLASSES)
    if (rootFiles.size == 0) {
      return null
    }
    val buildLayoutParameters = GradleInstallationManager.getInstance().guessBuildLayoutParameters(project, projectPath)
    val gradleUserHome = buildLayoutParameters.gradleUserHome.maybeGetLocalValue()
    if (gradleUserHome == null) {
      return null
    }
    if (!FileUtil.isAncestor(gradleUserHome, rootFiles[0].getPath(), false)) {
      return null
    }
    val coordinates = getLibraryUnifiedCoordinates(sourceArtifactNotation)
    if (coordinates == null) {
      return null
    }
    val localArtifacts = GradleLocalCacheHelper.findArtifactComponents(
      coordinates,
      Path.of(gradleUserHome),
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