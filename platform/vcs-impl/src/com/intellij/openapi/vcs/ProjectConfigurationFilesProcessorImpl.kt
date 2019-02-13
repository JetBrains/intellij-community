// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.getProjectStoreDirectory
import com.intellij.project.isDirectoryBased
import com.intellij.util.containers.ContainerUtil

private val LOG = Logger.getInstance(ProjectConfigurationFilesProcessorImpl::class.java)

private val configurationFilesExtensionsOutsideStoreDirectory =
  ContainerUtil.newHashSet(ProjectFileType.DEFAULT_EXTENSION, ModuleFileType.DEFAULT_EXTENSION)

private const val SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY = "SHARE_PROJECT_CONFIGURATION_FILES"
private const val ASKED_SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY = "ASKED_SHARE_PROJECT_CONFIGURATION_FILES"

class ProjectConfigurationFilesProcessorImpl(project: Project,
                                             parentDisposable: Disposable,
                                             private val vcsName: String,
                                             private val addChosenFiles: (Collection<VirtualFile>) -> Unit)
  : FilesProcessorWithNotificationImpl(project, parentDisposable), FilesProcessor {

  private val fileSystem = LocalFileSystem.getInstance()

  private val projectConfigurationFilesStore = getProjectConfigurationFilesStore(project)

  override fun doFilterFiles(files: Collection<VirtualFile>): Collection<VirtualFile> {
    val projectBasePath = project.basePath ?: return files
    val projectBaseDir = fileSystem.findFileByPath(projectBasePath) ?: return files
    val storeDir = getProjectStoreDirectory(projectBaseDir)
    if (project.isDirectoryBased && storeDir == null) {
      LOG.warn("Cannot find store directory for project in directory ${projectBaseDir.path}")
      return files
    }

    val filteredFiles = files.filter {
      configurationFilesExtensionsOutsideStoreDirectory.contains(it.extension) || isProjectConfigurationFile(storeDir, it)
    }

    val previouslyStoredFiles = projectConfigurationFilesStore.cleanupAndGetValidFiles()
    projectConfigurationFilesStore.addAll(filteredFiles)

    return filteredFiles + previouslyStoredFiles
  }

  override fun doActionOnChosenFiles(files: Collection<VirtualFile>) {
    addChosenFiles(files)
    projectConfigurationFilesStore.removeAll(files)
  }

  override val askedBeforeProperty = ASKED_SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY

  override val doForCurrentProjectProperty = SHARE_PROJECT_CONFIGURATION_FILES_PROPERTY

  override fun notificationTitle() = ""

  override fun notificationMessage(): String = VcsBundle.message("project.configuration.files.add.notification.message", vcsName)
  override val showActionText: String = VcsBundle.getString("project.configuration.files.add.notification.action.view")


  override val forCurrentProjectActionText: String = VcsBundle.getString("project.configuration.files.add.notification.action.add")
  override val forAllProjectsActionText: String? = null
  override val muteActionText: String = VcsBundle.getString("project.configuration.files.add.notification.action.mute")

  override fun rememberForAllProjects() {}

  private fun isProjectConfigurationFile(storeDir: VirtualFile?, file: VirtualFile) =
    storeDir != null && VfsUtilCore.isAncestor(storeDir, file, true)
}

@State(name = "ProjectConfigurationFiles", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class ProjectConfigurationFilesStoreState : PersistentStateComponent<ProjectConfigurationFilesStoreState.State> {

  private val fileSystem = LocalFileSystem.getInstance()

  data class State(var files: MutableList<String> = mutableListOf())

  var myState = State()

  fun cleanupAndGetValidFiles(): List<VirtualFile> {
    val validFiles = state.files.mapNotNull { fileSystem.findFileByPath(it)}
    state.files.retainAll(validFiles.map { it.path })
    return validFiles
  }

  fun addAll(files: Collection<VirtualFile>) =
    state.files.addAll(files.map(VirtualFile::getPath))

  fun removeAll(files: Collection<VirtualFile>) =
    state.files.removeAll(files.map(VirtualFile::getPath))

  override fun getState() = myState

  override fun loadState(state: State) {
    myState = state
  }
}

fun getProjectConfigurationFilesStore(project: Project): ProjectConfigurationFilesStoreState =
  ServiceManager.getService(project, ProjectConfigurationFilesStoreState::class.java)
