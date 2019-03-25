// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.configurationStore.OLD_NAME_CONVERTER
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.Ignored
import com.intellij.openapi.vcs.IgnoredCheckResult
import com.intellij.openapi.vcs.NotIgnored
import com.intellij.openapi.vcs.VcsIgnoreChecker
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.isDirectoryBased
import com.intellij.project.stateStore
import com.intellij.util.PathUtil
import com.intellij.vcsUtil.VcsImplUtil.findIgnoredFileContentProvider
import com.intellij.vcsUtil.VcsUtil
import java.io.IOException
import java.nio.file.Paths

private val LOG = Logger.getInstance(VcsIgnoreManagerImpl::class.java)

private const val RUN_CONFIGURATIONS_DIRECTORY = "runConfigurations"

class VcsIgnoreManagerImpl(private val project: Project) : VcsIgnoreManager {

  init {
    checkProjectNotDefault(project)
  }

  override fun isRunConfigurationVcsIgnored(configurationName: String): Boolean {
    try {
      val configurationFileName = configurationNameToFileName(configurationName)
      val checkForIgnore = { checkConfigurationVcsIgnored(project, configurationFileName) is Ignored }
      return ProgressManager.getInstance()
        .runProcessWithProgressSynchronously<Boolean, IOException>(checkForIgnore,
                                                                   "Checking configuration $configurationName for ignore...",
                                                                   false, project)
    }
    catch (e: IOException) {
      LOG.warn(e)
    }
    return false
  }

  override fun removeRunConfigurationFromVcsIgnore(configurationName: String) {
    try {
      val removeFromIgnore = { removeConfigurationFromVcsIgnore(project, configurationName) }
      ProgressManager.getInstance()
        .runProcessWithProgressSynchronously<Unit, IOException>(removeFromIgnore,
                                                                "Removing configuration ${configurationName} from ignore...",
                                                                false, project)
    }
    catch (io: IOException) {
      LOG.warn(io)
    }
  }

  private fun removeConfigurationFromVcsIgnore(project: Project, configurationName: String) {
    val projectFileOrConfigDir =
      if (project.isDirectoryBased) {
        VfsUtil.findFile(Paths.get(project.stateStore.projectConfigDir!!), true)!!
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

    val ignoreContentProvider = findIgnoredFileContentProvider(project, vcs)
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
    return if (project.isDirectoryBased) {
      val projectConfigDir = VfsUtil.findFile(Paths.get(project.stateStore.projectConfigDir!!), true)!!
      val checkForIgnore = virtualToIoFile(projectConfigDir)
        .resolve(RUN_CONFIGURATIONS_DIRECTORY)
        .resolve("$configurationFileName*.xml")
      val vcsRootForIgnore = VcsUtil.getVcsRootFor(project, projectConfigDir) ?: return NotIgnored

      getCheckerForFile(project, projectConfigDir)
        ?.isFilePatternIgnored(vcsRootForIgnore, PathUtil.toSystemIndependentName(checkForIgnore.path)) ?: NotIgnored
    }
    else {
      val projectFile = project.projectFile!!
      val checkForIgnore = virtualToIoFile(projectFile)
      val vcsRootForIgnore = VcsUtil.getVcsRootFor(project, projectFile) ?: return NotIgnored

      getCheckerForFile(project, projectFile)?.isIgnored(vcsRootForIgnore, checkForIgnore) ?: NotIgnored
    }
  }

  private fun getCheckerForFile(project: Project, file: VirtualFile): VcsIgnoreChecker? {
    val vcs = VcsUtil.getVcsFor(project, file) ?: return null
    return VcsIgnoreChecker.EXTENSION_POINT_NAME.getExtensionList(project).find { checker -> checker.supportedVcs == vcs.keyInstanceMethod }
  }

  private fun configurationNameToFileName(configurationName: String): String {
    return OLD_NAME_CONVERTER(configurationName)
  }

  private fun checkProjectNotDefault(project: Project) {
    if (project.isDefault) {
      throw UnsupportedOperationException("Default project not supported")
    }
  }
}