// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ignore

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilesProcessorWithNotificationImpl
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ignore.IgnoreConfigurationProperty.ASKED_MANAGE_IGNORE_FILES_PROPERTY
import com.intellij.openapi.vcs.changes.ignore.IgnoreConfigurationProperty.MANAGE_IGNORE_FILES_PROPERTY
import com.intellij.openapi.vcs.changes.ignore.psi.util.addNewElementsToIgnoreBlock
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.project.getProjectStoreDirectory
import com.intellij.vcsUtil.VcsImplUtil.getIgnoredFileContentProvider
import com.intellij.vcsUtil.VcsUtil
import com.intellij.vfs.AsyncVfsEventsListener
import com.intellij.vfs.AsyncVfsEventsPostProcessor
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private val LOG = logger<IgnoreFilesProcessorImpl>()

class IgnoreFilesProcessorImpl(project: Project, private val vcs: AbstractVcs<*>, private val parentDisposable: Disposable)
  : FilesProcessorWithNotificationImpl(project, parentDisposable), AsyncVfsEventsListener, ChangeListListener {

  private val UNPROCESSED_FILES_LOCK = ReentrantReadWriteLock()

  private val unprocessedFiles = mutableSetOf<VirtualFile>()

  private val changeListManager = ChangeListManagerImpl.getInstanceImpl(project)
  private val vcsIgnoreManager = VcsIgnoreManager.getInstance(project)

  fun install() {
    runReadAction {
      if (!project.isDisposed) {
        changeListManager.addChangeListListener(this, parentDisposable)
        AsyncVfsEventsPostProcessor.getInstance().addListener(this, parentDisposable)
      }
    }
  }

  override fun changeListUpdateDone() {
    if (!needProcessIgnoredFiles() || ApplicationManager.getApplication().isUnitTestMode) return

    val files = UNPROCESSED_FILES_LOCK.read { unprocessedFiles.toList() }
    if (files.isEmpty()) return

    processFiles(files)

    UNPROCESSED_FILES_LOCK.write {
      unprocessedFiles.clear()
    }
  }

  override fun filesChanged(events: List<VFileEvent>) {
    if (!needProcessIgnoredFiles() || ApplicationManager.getApplication().isUnitTestMode) return

    val potentiallyIgnoredFiles =
      events.asSequence()
        .mapNotNull(::getAffectedFile)
        .filter(vcsIgnoreManager::isPotentiallyIgnoredFile)
        .toList()

    if (potentiallyIgnoredFiles.isEmpty()) return
    LOG.debug("Got potentially ignored files from VFS events", potentiallyIgnoredFiles)

    UNPROCESSED_FILES_LOCK.write {
      unprocessedFiles.addAll(potentiallyIgnoredFiles)
    }
  }

  override fun doActionOnChosenFiles(files: Collection<VirtualFile>) {
    runInEdt {
      writeIgnores(project, files)
    }
  }

  override fun dispose() {
    super.dispose()
    unprocessedFiles.clear()
  }

  private fun writeIgnores(project: Project, potentiallyIgnoredFiles: Collection<VirtualFile>) {
    if (potentiallyIgnoredFiles.isEmpty()) return

    LOG.debug("Try to write potential ignored files", potentiallyIgnoredFiles)
    val ignoreFileToContent = hashMapOf<VirtualFile, MutableList<IgnoreGroupContent>>()
    val providerToDescriptorMap = IgnoredFileProvider.IGNORE_FILE.extensions.associate { it to it.getIgnoredFiles(project) }

    for (potentiallyIgnoredFile in potentiallyIgnoredFiles) {
      VcsUtil.getVcsFor(project, potentiallyIgnoredFile)?.let { vcs ->
        getIgnoredFileContentProvider(project, vcs)?.let { ignoredContentProvider ->
          findOrCreateIgnoreFileByFile(project, ignoredContentProvider, potentiallyIgnoredFile)?.let { ignoreFile ->
            for ((ignoredFileProvider, descriptors) in providerToDescriptorMap) {
              for (ignoredFileDescriptor in descriptors.filter { it.matchesFile(potentiallyIgnoredFile) }) {
                val ignoreFileContent = ignoreFileToContent.computeIfAbsent(ignoreFile) { mutableListOf() }
                val groupDescription = " ${ignoredFileProvider.ignoredGroupDescription}"
                val ignoreFileGroupContent = ignoreFileContent.getOrInitialize(groupDescription)
                ignoreFileGroupContent.ignoredDescriptors.add(ignoredFileDescriptor)
              }
            }
          }
        }
      }
    }

    for ((ignoreFile, newContent) in ignoreFileToContent) {
      for (groupContent in newContent) {
        val ignoredDescriptors = groupContent.ignoredDescriptors
        LOG.debug("Write to ignore file ${ignoreFile} ignores: $ignoredDescriptors")
        addNewElementsToIgnoreBlock(project, ignoreFile, groupContent.group, *ignoredDescriptors.toTypedArray())
      }
    }
  }

  private fun MutableList<IgnoreGroupContent>.getOrInitialize(group: String): IgnoreGroupContent =
    find { it.group == group } ?: IgnoreGroupContent(group).apply { this@getOrInitialize.add(this) }

  private data class IgnoreGroupContent(val group: String, val ignoredDescriptors: MutableSet<IgnoredFileDescriptor> = mutableSetOf())

  private fun findOrCreateIgnoreFileByFile(project: Project,
                                           ignoredContentProvider: IgnoredFileContentProvider,
                                           file: VirtualFile): VirtualFile? {
    val storeDir = findStoreDir(project)

    val ignoreFileRoot =
      if (ignoredContentProvider.supportIgnoreFileNotInVcsRoot()
          && storeDir != null && file.underProjectStoreDir(storeDir)) storeDir
      else VcsUtil.getVcsRootFor(project, file) ?: return null

    return ignoreFileRoot.findChild(ignoredContentProvider.fileName) ?: runWriteAction {
      ignoreFileRoot.createChildData(this, ignoredContentProvider.fileName)
    }
  }

  private fun findStoreDir(project: Project): VirtualFile? {
    val projectBasePath = project.basePath ?: return null
    val projectBaseDir = LocalFileSystem.getInstance().findFileByPath(projectBasePath) ?: return null

    return getProjectStoreDirectory(projectBaseDir) ?: return null
  }

  private fun VirtualFile.underProjectStoreDir(storeDir: VirtualFile): Boolean {
    return VfsUtilCore.isAncestor(storeDir, this, true)
  }

  override fun doFilterFiles(files: Collection<VirtualFile>) =
    changeListManager.unversionedFiles.filter { isUnder(files, it) }

  override fun rememberForAllProjects() {
    val applicationSettings = VcsApplicationSettings.getInstance()
    applicationSettings.MANAGE_IGNORE_FILES = true
  }

  override val askedBeforeProperty = ASKED_MANAGE_IGNORE_FILES_PROPERTY

  override val doForCurrentProjectProperty = MANAGE_IGNORE_FILES_PROPERTY
  override val showActionText: String = VcsBundle.getString("ignored.file.manage.view")

  override val forCurrentProjectActionText: String = VcsBundle.getString("ignored.file.manage.this.project")
  override val forAllProjectsActionText: String? = VcsBundle.getString("ignored.file.manage.all.project")
  override val muteActionText: String = VcsBundle.getString("ignored.file.manage.notmanage")

  override val viewFilesDialogTitle: String? = VcsBundle.getString("ignored.file.manage.view.dialog.title")
  override val viewFilesDialogOkActionName: String = VcsBundle.message("ignored.file.manage.view.dialog.ignore.action")

  override fun notificationTitle() = ""
  override fun notificationMessage(): String = VcsBundle.message("ignored.file.manage.with.files.message",
                                                                 ApplicationNamesInfo.getInstance().fullProductName,
                                                                 getIgnoredFileContentProvider(project, vcs)?.fileName ?: "ignore file")

  private fun isUnder(parents: Collection<VirtualFile>, child: VirtualFile) = generateSequence(child) { it.parent }.any { it in parents }

  override fun needDoForCurrentProject(): Boolean {
    val appSettings = VcsApplicationSettings.getInstance()
    return !appSettings.DISABLE_MANAGE_IGNORE_FILES && (appSettings.MANAGE_IGNORE_FILES || super.needDoForCurrentProject())
  }

  private fun getAffectedFile(event: VFileEvent): VirtualFile? =
    runReadAction {
      when {
        event is VFileCreateEvent && event.parent.isValid -> event.file
        event is VFileMoveEvent || event.isRename() -> event.file
        event is VFileCopyEvent && event.newParent.isValid -> event.newParent.findChild(event.newChildName)
        else -> null
      }
    }

  private fun VFileEvent.isRename() = this is VFilePropertyChangeEvent && isRename

  private fun needProcessIgnoredFiles() = ApplicationManager.getApplication().isInternal || Registry.`is`("vcs.ignorefile.generation", true)
}