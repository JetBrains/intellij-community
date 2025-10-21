// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.configurationStore.OLD_NAME_CONVERTER
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.InitialVfsRefreshService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreFileType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.isDirectoryBased
import com.intellij.project.stateStore
import com.intellij.util.SlowOperations
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.vcsUtil.VcsImplUtil.findIgnoredFileContentProvider
import com.intellij.vcsUtil.VcsUtil
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.io.path.invariantSeparatorsPathString

private val LOG = logger<VcsIgnoreManagerImpl>()

private const val RUN_CONFIGURATIONS_DIRECTORY = "runConfigurations"

@Internal
class VcsIgnoreManagerImpl(private val project: Project, coroutineScope: CoroutineScope) : VcsIgnoreManager {
  companion object {
    @JvmStatic
    fun getInstanceImpl(project: Project) = VcsIgnoreManager.getInstance(project) as VcsIgnoreManagerImpl

    @JvmField
    val EP_NAME: ExtensionPointName<VcsIgnoreChecker> = ExtensionPointName("com.intellij.vcsIgnoreChecker")
  }

  val ignoreRefreshQueue: MergingUpdateQueue = MergingUpdateQueue.mergingUpdateQueue(
    name = "VcsIgnoreUpdate",
    mergingTimeSpan = 500,
    coroutineScope = coroutineScope,
  )

  init {
    checkProjectNotDefault(project)

    ignoreRefreshQueue.queue(object : Update("wait Project opening activities scan") {
      override fun run() {
        val vfsRefreshService = project.service<InitialVfsRefreshService>()
        runBlockingCancellable {
          vfsRefreshService.awaitInitialVfsRefreshFinished()
        }
      }

      override suspend fun execute() {
        project.serviceAsync<InitialVfsRefreshService>().awaitInitialVfsRefreshFinished()
      }
    })

    if (ApplicationManager.getApplication().isUnitTestMode) {
      project.messageBus.connect(coroutineScope).subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
        override fun projectClosing(project: Project) {
          if (this@VcsIgnoreManagerImpl.project === project) {
            try {
              @Suppress("TestOnlyProblems")
              ignoreRefreshQueue.waitForAllExecuted(10, TimeUnit.SECONDS)
            }
            catch (e: Exception) {
              LOG.warn("Queue '$ignoreRefreshQueue' wait for all executed failed with error:", e)
            }
          }
        }
      })
    }
  }

  fun findIgnoreFileType(vcs: AbstractVcs): IgnoreFileType? {
    val ignoredFileContentProvider = findIgnoredFileContentProvider(vcs) ?: return null
    return FileTypeManager.getInstance().getFileTypeByFileName(ignoredFileContentProvider.fileName) as? IgnoreFileType
  }

  override fun isDirectoryVcsIgnored(dirPath: String): Boolean {
    try {
      val checkForIgnore = { getDirectoryVcsIgnoredStatus(project, dirPath) is Ignored }
      return ProgressManager.getInstance()
        .runProcessWithProgressSynchronously<Boolean, IOException>(checkForIgnore,
                                                                   VcsBundle.message("checking.vcs.status.progress"),
                                                                   false, project)
    }
    catch (e: IOException) {
      LOG.warn(e)
    }
    return false
  }

  private fun getDirectoryVcsIgnoredStatus(project: Project, dirPathString: String): IgnoredCheckResult {
    val dirPath = VcsContextFactory.getInstance().createFilePath(dirPathString, true)
    val vcsRoot = VcsUtil.getVcsRootFor(project, dirPath) ?: return NotIgnored
    return getCheckerForFile(project, dirPath)?.isFilePatternIgnored(vcsRoot, dirPathString) ?: NotIgnored
  }

  override fun isRunConfigurationVcsIgnored(configurationName: String): Boolean {
    try {
      val configurationFileName = configurationNameToFileName(configurationName)
      val checkForIgnore = { checkConfigurationVcsIgnored(project, configurationFileName) is Ignored }
      return ProgressManager.getInstance()
        .runProcessWithProgressSynchronously<Boolean, IOException>(checkForIgnore,
                                                                   VcsBundle.message("changes.checking.configuration.0.for.ignore",
                                                                                     configurationName),
                                                                   false, project)
    }
    catch (e: IOException) {
      LOG.warn(e)
    }
    return false
  }

  override fun removeRunConfigurationFromVcsIgnore(configurationName: String) {
    try {
      val removeFromIgnore = {
        SlowOperations.knownIssue("IDEA-338210, EA-660187").use {
          removeConfigurationFromVcsIgnore(project, configurationName)
        }
      }
      ProgressManager.getInstance()
        .runProcessWithProgressSynchronously<Unit, IOException>(removeFromIgnore,
                                                                VcsBundle.message("changes.removing.configuration.0.from.ignore",
                                                                                  configurationName),
                                                                false, project)
    }
    catch (io: IOException) {
      LOG.warn(io)
    }
  }

  override fun isPotentiallyIgnoredFile(file: VirtualFile): Boolean = isPotentiallyIgnoredFile(VcsUtil.getFilePath(file))

  override fun isPotentiallyIgnoredFile(filePath: FilePath): Boolean {
    return runReadAction {
      if (project.isDisposed) {
        return@runReadAction false
      }
      return@runReadAction IgnoredFileProvider.IGNORE_FILE.extensionList.any { it.isIgnoredFile(project, filePath) }
    }
  }
}

private fun removeConfigurationFromVcsIgnore(project: Project, configurationName: String) {
  val projectFileOrConfigDir =
    if (project.isDirectoryBased) {
      VfsUtil.findFile(project.stateStore.directoryStorePath!!, true)!!
    }
    else {
      project.projectFile!!
    }

  val projectVcsRoot = VcsUtil.getVcsRootFor(project, projectFileOrConfigDir) ?: return
  val vcs = VcsUtil.getVcsFor(project, projectVcsRoot)
  if (vcs == null) {
    LOG.debug("Cannot get VCS for root " + projectVcsRoot.path)
    return
  }

  val ignoreContentProvider = findIgnoredFileContentProvider(vcs)
  if (ignoreContentProvider == null) {
    LOG.debug("Cannot get ignore content provider for vcs " + vcs.name)
    return
  }

  val checkResult = checkConfigurationVcsIgnored(project, configurationName)

  if (checkResult is Ignored) {
    val ignoreFile = checkResult.ignoreFile
    FileUtil.appendToFile(ignoreFile, ignoreContentProvider.buildUnignoreContent(checkResult.matchedPattern))
  }
}

private fun checkConfigurationVcsIgnored(project: Project, configurationFileName: String): IgnoredCheckResult {
  val stateStore = project.stateStore
  val dotIdea = stateStore.directoryStorePath
  if (dotIdea != null) {
    val dotIdeaVcsPath = VcsContextFactory.getInstance().createFilePath(dotIdea, true)
    val vcsRootForIgnore = VcsUtil.getVcsRootFor(project, dotIdeaVcsPath) ?: return NotIgnored
    val filePattern = "${dotIdea.invariantSeparatorsPathString}/$RUN_CONFIGURATIONS_DIRECTORY/$configurationFileName*.xml" // NON-NLS
    return getCheckerForFile(project, dotIdeaVcsPath)
             ?.isFilePatternIgnored(vcsRootForIgnore, filePattern) ?: NotIgnored
  }
  else {
    val projectFile = stateStore.projectFilePath
    val projectFileVcsPath = VcsContextFactory.getInstance().createFilePath(projectFile, false)
    val vcsRootForIgnore = VcsUtil.getVcsRootFor(project, projectFileVcsPath) ?: return NotIgnored
    return getCheckerForFile(project, projectFileVcsPath)
             ?.isIgnored(vcsRootForIgnore, projectFile) ?: NotIgnored
  }
}

private fun getCheckerForFile(project: Project, filePath: FilePath): VcsIgnoreChecker? {
  val vcs = VcsUtil.getVcsFor(project, filePath) ?: return null
  return VcsIgnoreManagerImpl.EP_NAME.findFirstSafe { checker -> checker.supportedVcs == vcs.keyInstanceMethod }
}

private fun configurationNameToFileName(configurationName: String): String {
  return OLD_NAME_CONVERTER(configurationName)
}

private fun checkProjectNotDefault(project: Project) {
  if (project.isDefault) {
    throw UnsupportedOperationException(VcsBundle.message("changes.error.default.project.not.supported"))
  }
}
