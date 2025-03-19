// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.VcsIgnoreManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.isDirectoryBased
import com.intellij.project.stateStore
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcsUtil.VcsImplUtil
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
    val projectConfigurationFiles = doFilterFiles(files)

    if (projectConfigurationFiles.isNotEmpty()) {
      if (foundProjectConfigurationFiles.compareAndSet(false, true)) {
        LOG.debug("Found new project configuration files ", projectConfigurationFiles)
      }
    }

    return files - projectConfigurationFiles
  }

  override fun unchangedFileStatusChanged(upToDate: Boolean) {
    if (upToDate && foundProjectConfigurationFiles.compareAndSet(true, false)) {
      val unversionedProjectConfigurationFiles = doFilterFiles(ChangeListManagerImpl.getInstanceImpl(project).unversionedFiles)
      if (unversionedProjectConfigurationFiles.isNotEmpty()) {
        setForCurrentProject(VcsImplUtil.isProjectSharedInVcs(project))
        processFiles(unversionedProjectConfigurationFiles.toList())
      }
    }
  }

  override fun doFilterFiles(files: Collection<VirtualFile>): Collection<VirtualFile> {
    val projectConfigDir = getProjectConfigDir(project)

    return files
      .asSequence()
      .filter {
        configurationFilesExtensionsOutsideStoreDirectory.contains(it.extension) || isProjectConfigurationFile(projectConfigDir, it)
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

private fun isProjectConfigurationFile(configDir: VirtualFile?, file: VirtualFile): Boolean {
  return configDir != null && VfsUtilCore.isAncestor(configDir, file, true)
}

private fun getProjectConfigDir(project: Project): VirtualFile? {
  if (!project.isDirectoryBased || project.isDefault) {
    return null
  }

  val projectConfigDir = project.stateStore.directoryStorePath?.let(LocalFileSystem.getInstance()::findFileByNioFile)
  if (projectConfigDir == null) {
    LOG.warn("Cannot find project config directory for non-default and non-directory based project ${project.name}")
  }
  return projectConfigDir
}
