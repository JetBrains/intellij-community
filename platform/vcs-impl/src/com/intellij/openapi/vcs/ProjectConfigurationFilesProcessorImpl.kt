// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.VcsIgnoreManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore.isAncestor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.isDirectoryBased
import com.intellij.project.stateStore
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcsUtil.VcsImplUtil
import com.intellij.vcsUtil.VcsUtil
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = Logger.getInstance(ProjectConfigurationFilesProcessorImpl::class.java)

private val configurationFilesExtensionsOutsideStoreDirectory =
  ContainerUtil.newHashSet(ProjectFileType.DEFAULT_EXTENSION, ModuleFileType.DEFAULT_EXTENSION)

internal const val SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY = "SHARE_PROJECT_CONFIGURATION_FILES" //NON-NLS
internal const val ASKED_SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY = "ASKED_SHARE_PROJECT_CONFIGURATION_FILES" //NON-NLS

/**
 * Component for managing project configuration files: add/propose to add potentially shared project configuration files to VCS.
 *
 * Overrides behavior of [VcsConfiguration.StandardConfirmation.ADD] flag for project configuration files (ex: .idea/misc.xml).
 */
internal class ProjectConfigurationFilesProcessorImpl(
  project: Project,
  private val parentDisposable: Disposable,
  private val vcs: AbstractVcs,
  private val addChosenFiles: (Collection<VirtualFile>) -> Unit,
) : FilesProcessorWithNotificationImpl(project, parentDisposable), ChangeListListener {

  private val foundProjectConfigurationFiles = AtomicBoolean()
  private val vcsIgnoreManager = VcsIgnoreManager.getInstance(project)

  fun install() {
    project.messageBus.connect(parentDisposable).subscribe(ChangeListListener.TOPIC, this)
  }

  /**
   * Remove project configuration files from "Add to VCS" dialog.
   * Schedule notification or silent addition instead.
   */
  fun filterNotProjectConfigurationFiles(files: List<VirtualFile>): List<VirtualFile> {
    val projectConfigurationFiles = filterProjectConfigurationFiles(files)

    if (projectConfigurationFiles.isNotEmpty()) {
      if (foundProjectConfigurationFiles.compareAndSet(false, true)) {
        LOG.debug("Found new project configuration files ", projectConfigurationFiles)
      }
    }

    return files - projectConfigurationFiles
  }

  override fun unchangedFileStatusChanged(upToDate: Boolean) {
    if (upToDate && foundProjectConfigurationFiles.compareAndSet(true, false)) {
      val unversionedProjectConfigurationFiles = getUnversionedConfigurationFiles().mapNotNull { it.virtualFile }
      if (unversionedProjectConfigurationFiles.isNotEmpty()) {
        setForCurrentProject(VcsImplUtil.isProjectSharedInVcs(project))
        processFiles(unversionedProjectConfigurationFiles)
      }
    }
  }

  private fun filterProjectConfigurationFiles(files: Collection<VirtualFile>): Set<VirtualFile> {
    val projectConfigDir = getProjectConfigDirPath(project)?.let {
      LocalFileSystem.getInstance().findFileByNioFile(it)
    }

    return files
      .asSequence()
      .filter {
        configurationFilesExtensionsOutsideStoreDirectory.contains(it.extension)
        || projectConfigDir != null && isAncestor(projectConfigDir, it, true)
      }
      .filterNot(vcsIgnoreManager::isPotentiallyIgnoredFile)
      .toSet()
  }

  private fun getUnversionedConfigurationFiles(): Set<FilePath> {
    val projectConfigDirPath = getProjectConfigDirPath(project)?.let {
      VcsUtil.getFilePath(it, true)
    }

    return ChangeListManager.getInstance(project).unversionedFilesPaths
      .asSequence()
      .filter {
        configurationFilesExtensionsOutsideStoreDirectory.contains(FileUtilRt.getExtension(it.name))
        || projectConfigDirPath != null && it.isUnder(projectConfigDirPath, true)
      }
      .filterNot(vcsIgnoreManager::isPotentiallyIgnoredFile)
      .toSet()
  }

  override fun doActionOnChosenFiles(files: Collection<VirtualFile>) {
    addChosenFiles(files)
  }

  override val notificationDisplayId: String = VcsNotificationIdsHolder.PROJECT_CONFIGURATION_FILES_ADDED

  override val askedBeforeProperty = ASKED_SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY

  override val doForCurrentProjectProperty = SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY

  override fun notificationTitle() = ""

  override fun notificationMessage(): String = VcsBundle.message("project.configuration.files.add.notification.message", vcs.displayName)

  override val showActionText: String = VcsBundle.message("project.configuration.files.add.notification.action.view")
  override val forCurrentProjectActionText: String = VcsBundle.message("project.configuration.files.add.notification.action.add")

  override val muteActionText: String = VcsBundle.message("project.configuration.files.add.notification.action.mute")
  override val viewFilesDialogTitle: String = VcsBundle.message("project.configuration.files.view.dialog.title", vcs.displayName)
}

private fun getProjectConfigDirPath(project: Project): Path? {
  if (!project.isDirectoryBased || project.isDefault) {
    return null
  }

  val projectConfigDir = project.stateStore.directoryStorePath
  if (projectConfigDir == null) {
    LOG.warn("Cannot find project config directory for non-default and non-directory based project ${project.name}")
  }
  return projectConfigDir
}
