// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ignore

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ignore.IgnoreConfigurationProperty.ASKED_MANAGE_IGNORE_FILES_PROPERTY
import com.intellij.openapi.vcs.changes.ignore.IgnoreConfigurationProperty.MANAGE_IGNORE_FILES_PROPERTY
import com.intellij.openapi.vcs.changes.ignore.psi.util.addNewElementsToIgnoreBlock
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.project.getProjectStoreDirectory
import com.intellij.project.stateStore
import com.intellij.vcsUtil.VcsImplUtil.findIgnoredFileContentProvider
import com.intellij.vcsUtil.VcsUtil
import com.intellij.vfs.AsyncVfsEventsListener
import com.intellij.vfs.AsyncVfsEventsPostProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write
import kotlin.coroutines.coroutineContext

private val LOG = logger<IgnoreFilesProcessorImpl>()

/**
 * Automatically generate or update .ignore files basing on [IgnoredFileProvider] extension point.
 */
internal class IgnoreFilesProcessorImpl(project: Project, parentDisposable: Disposable, private val vcs: AbstractVcs)
  : FilesProcessorWithNotificationImpl(project, parentDisposable), AsyncVfsEventsListener, ChangeListListener {

  private val UNPROCESSED_FILES_LOCK = ReentrantReadWriteLock()

  private val unprocessedFiles = mutableSetOf<VirtualFile>()

  private val vcsIgnoreManager = VcsIgnoreManager.getInstance(project)

  fun install(coroutineScope: CoroutineScope) {
    project.messageBus.connect(coroutineScope).subscribe(ChangeListListener.TOPIC, this)
    AsyncVfsEventsPostProcessor.getInstance().addListener(this, coroutineScope)
  }

  override fun unchangedFileStatusChanged(upToDate: Boolean) {
    if (!upToDate) return
    if (ApplicationManager.getApplication().isUnitTestMode) return

    val files: List<VirtualFile>
    UNPROCESSED_FILES_LOCK.write {
      files = unprocessedFiles.toList()
      unprocessedFiles.clear()
    }
    if (files.isEmpty()) return

    val restFiles = silentlyIgnoreFilesInsideConfigDir(files)
    if (needProcessIgnoredFiles() && restFiles.isNotEmpty()) {
      processFiles(restFiles)
    }
  }

  private fun silentlyIgnoreFilesInsideConfigDir(files: List<VirtualFile>): List<VirtualFile> {
    val configDir = project.stateStore.directoryStorePath ?: return files
    val configDirFile = LocalFileSystem.getInstance().findFileByNioFile(configDir) ?: return files
    val filesInConfigDir = files.filter { VfsUtil.isAncestor(configDirFile, it, true) }
    val unversionedFilesInConfigDir = doFilterFiles(filesInConfigDir)

    runInEdt {
      writeIgnores(project, unversionedFilesInConfigDir)
    }

    return files - filesInConfigDir
  }

  override suspend fun filesChanged(events: List<VFileEvent>) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    val potentiallyIgnoredFiles = events.asSequence()
        .filter {
          val filePath = getAffectedFilePath(it)
          filePath != null && vcsIgnoreManager.isPotentiallyIgnoredFile(filePath)
        }
        .mapNotNull { it.file }
        .toList()

    if (potentiallyIgnoredFiles.isEmpty()) {
      return
    }

    LOG.debug { "Got potentially ignored files from VFS events $potentiallyIgnoredFiles" }

    coroutineContext.job.ensureActive()

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
    UNPROCESSED_FILES_LOCK.write {
      unprocessedFiles.clear()
    }
  }

  private fun writeIgnores(project: Project, potentiallyIgnoredFiles: Collection<VirtualFile>) {
    if (project.isDisposed) return
    if (potentiallyIgnoredFiles.isEmpty()) return

    LOG.debug("Try to write potential ignored files", potentiallyIgnoredFiles)
    val ignoreFileToContent = hashMapOf<VirtualFile, MutableList<IgnoreGroupContent>>()
    val providerToDescriptorMap = IgnoredFileProvider.IGNORE_FILE.extensions.associate { it to it.getIgnoredFiles(project) }

    for (potentiallyIgnoredFile in potentiallyIgnoredFiles) {
      VcsUtil.getVcsFor(project, potentiallyIgnoredFile)?.let { vcs ->
        findIgnoredFileContentProvider(vcs)?.let { ignoredContentProvider ->
          findOrCreateIgnoreFileByFile(project, ignoredContentProvider, potentiallyIgnoredFile)?.let { ignoreFile ->
            for ((ignoredFileProvider, descriptors) in providerToDescriptorMap) {
              for (ignoredFileDescriptor in descriptors.filter { it.matchesFile(VcsUtil.getFilePath(potentiallyIgnoredFile)) }) {
                val ignoreFileContent = ignoreFileToContent.computeIfAbsent(ignoreFile) { mutableListOf() }
                val groupDescription = ignoredContentProvider.buildIgnoreGroupDescription(ignoredFileProvider)
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
      if (storeDir != null
          && ignoredContentProvider.supportIgnoreFileNotInVcsRoot()
          && file.underProjectStoreDir(storeDir)) {
        if (ignoredContentProvider.canCreateIgnoreFileInStateStoreDir()) {
          storeDir
        }
        else return null
      }
      else VcsUtil.getVcsRootFor(project, file) ?: return null

    return ignoreFileRoot.findChild(ignoredContentProvider.fileName) ?: runWriteAction {
      ignoreFileRoot.createChildData(this, ignoredContentProvider.fileName)
    }
  }

  private fun findStoreDir(project: Project): VirtualFile? {
    val projectBasePath = project.basePath ?: return null
    val projectBaseDir = LocalFileSystem.getInstance().findFileByPath(projectBasePath) ?: return null

    return getProjectStoreDirectory(projectBaseDir)
  }

  private fun VirtualFile.underProjectStoreDir(storeDir: VirtualFile): Boolean {
    return VfsUtilCore.isAncestor(storeDir, this, true)
  }

  override fun doFilterFiles(files: Collection<VirtualFile>): List<VirtualFile> {
    val parents = files.toHashSet()
    return ChangeListManagerImpl.getInstanceImpl(project).unversionedFiles
      .filter { isUnder(parents, it) }
  }

  override fun rememberForAllProjects() {
    val applicationSettings = VcsApplicationSettings.getInstance()
    applicationSettings.MANAGE_IGNORE_FILES = true
  }

  override val notificationDisplayId: String = VcsNotificationIdsHolder.MANAGE_IGNORE_FILES

  override val askedBeforeProperty = ASKED_MANAGE_IGNORE_FILES_PROPERTY

  override val doForCurrentProjectProperty = MANAGE_IGNORE_FILES_PROPERTY
  override val showActionText: String = VcsBundle.message("ignored.file.manage.view")

  override val forCurrentProjectActionText: String = VcsBundle.message("ignored.file.manage.this.project")
  override val forAllProjectsActionText: String = VcsBundle.message("ignored.file.manage.all.project")
  override val muteActionText: String = VcsBundle.message("ignored.file.manage.notmanage")

  override val viewFilesDialogTitle: String = VcsBundle.message("ignored.file.manage.view.dialog.title")
  override val viewFilesDialogOkActionName: String = VcsBundle.message("ignored.file.manage.view.dialog.ignore.action")

  override fun notificationTitle() = ""
  override fun notificationMessage(): String = VcsBundle.message("ignored.file.manage.with.files.message",
                                                                 ApplicationNamesInfo.getInstance().fullProductName,
                                                                 findIgnoredFileContentProvider(vcs)?.fileName ?: VcsBundle.message("changes.ignore.file"))

  private fun isUnder(parents: Set<VirtualFile>, child: VirtualFile) = generateSequence(child) { it.parent }.any { it in parents }

  override fun needDoForCurrentProject(): Boolean {
    val appSettings = VcsApplicationSettings.getInstance()
    return !appSettings.DISABLE_MANAGE_IGNORE_FILES && (appSettings.MANAGE_IGNORE_FILES || super.needDoForCurrentProject())
  }

  private fun getAffectedFilePath(event: VFileEvent): FilePath? {
    if (event.fileSystem !is LocalFileSystem) {
      return null
    }

    return when {
      event is VFileCreateEvent -> VcsUtil.getFilePath(event.path, event.isDirectory)
      event is VFileMoveEvent ||
      event is VFileCopyEvent ||
      event is VFilePropertyChangeEvent && event.isRename -> {
        VcsUtil.getFilePath(event.path, event.file!!.isDirectory)
      }
      else -> null
    }
  }

  private fun needProcessIgnoredFiles() = Registry.`is`("vcs.ignorefile.generation")
}
