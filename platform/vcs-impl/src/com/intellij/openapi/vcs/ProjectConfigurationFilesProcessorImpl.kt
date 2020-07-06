// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
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

internal const val SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY = "SHARE_PROJECT_CONFIGURATION_FILES"
internal const val ASKED_SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY = "ASKED_SHARE_PROJECT_CONFIGURATION_FILES"

class ProjectConfigurationFilesProcessorImpl(project: Project,
                                             private val parentDisposable: Disposable,
                                             private val vcsName: String,
                                             private val addChosenFiles: (Collection<VirtualFile>) -> Unit)
  : FilesProcessorWithNotificationImpl(project, parentDisposable), ChangeListListener {

  private val foundProjectConfigurationFiles = AtomicBoolean()

  private val fileSystem = LocalFileSystem.getInstance()

  private val vcsIgnoreManager = VcsIgnoreManager.getInstance(project)

  fun install() {
    runReadAction {
      if (!project.isDisposed) {
        project.messageBus.connect(parentDisposable).subscribe(ChangeListListener.TOPIC, this)
      }
    }
  }

  fun filterNotProjectConfigurationFiles(files: List<VirtualFile>): List<VirtualFile> {
    val projectConfigurationFiles = doFilterFiles(files)

    if (projectConfigurationFiles.isNotEmpty()) {
      if (foundProjectConfigurationFiles.compareAndSet(false, true)) {
        LOG.debug("Found new project configuration files ", projectConfigurationFiles)
      }
    }

    return files - projectConfigurationFiles
  }

  override fun changeListUpdateDone() {
    if (foundProjectConfigurationFiles.compareAndSet(true, false)) {
      val unversionedProjectConfigurationFiles = doFilterFiles(ChangeListManagerImpl.getInstanceImpl(project).unversionedFiles)
      if (unversionedProjectConfigurationFiles.isNotEmpty()) {
        PropertiesComponent.getInstance(project).setValue(SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY, VcsImplUtil.isProjectSharedInVcs(project))
        processFiles(unversionedProjectConfigurationFiles.toList())
      }
    }
  }

  override fun doFilterFiles(files: Collection<VirtualFile>): Collection<VirtualFile> {
    val projectConfigDir = project.getProjectConfigDir()

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

  override val notificationDisplayId: String = "project.configuration.files.added.notification"

  override val askedBeforeProperty = ASKED_SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY

  override val doForCurrentProjectProperty = SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY

  override fun notificationTitle() = ""

  override fun notificationMessage(): String = VcsBundle.message("project.configuration.files.add.notification.message", vcsName)

  override val showActionText: String = VcsBundle.getString("project.configuration.files.add.notification.action.view")
  override val forCurrentProjectActionText: String = VcsBundle.getString("project.configuration.files.add.notification.action.add")


  override val forAllProjectsActionText: String? = null
  override val muteActionText: String = VcsBundle.getString("project.configuration.files.add.notification.action.mute")
  override val viewFilesDialogTitle: String? = VcsBundle.message("project.configuration.files.view.dialog.title", vcsName)

  override fun rememberForAllProjects() {}

  private fun isProjectConfigurationFile(configDir: VirtualFile?, file: VirtualFile) =
    configDir != null && VfsUtilCore.isAncestor(configDir, file, true)

  private fun Project.getProjectConfigDir(): VirtualFile? {
    if (!isDirectoryBased || isDefault) return null

    val projectConfigDir = stateStore.directoryStorePath?.let(fileSystem::findFileByNioFile)
    if (projectConfigDir == null) {
      LOG.warn("Cannot find project config directory for non-default and non-directory based project ${name}")
    }
    return projectConfigDir
  }
}
