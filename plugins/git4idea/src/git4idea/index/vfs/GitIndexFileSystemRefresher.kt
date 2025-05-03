// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.vfs

import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.codeInsight.daemon.OutsidersPsiFileSupport
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.progress.util.PotemkinProgress
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.encoding.EncodingManager
import com.intellij.openapi.vfs.encoding.EncodingManagerListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent.REFRESH_REQUESTOR
import com.intellij.util.LocalTimeCounter
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.messages.MessageBusConnection
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.i18n.GitBundle
import git4idea.index.GitIndexUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.util.GitFileUtils
import org.jetbrains.annotations.NonNls
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class GitIndexFileSystemRefresher(private val project: Project) : Disposable {
  private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Git index file system refresher", 1)
  private val disposable = Disposer.newDisposable("Git Index File System")

  @Volatile
  private var isShutDown = false

  private val cache = Caffeine.newBuilder()
    .weakValues()
    .build(CacheLoader<Key, GitIndexVirtualFile?> { key ->
      createIndexVirtualFile(key)
    })

  init {
    val connection: MessageBusConnection = project.messageBus.connect(disposable)
    connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { repository ->
      LOG.debug("Scheduling refresh for repository ${repository.root.name}")
      refresh { it.root == repository.root }
    })
    connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosing(p: Project) {
        if (project == p) {
          isShutDown = true
          Disposer.dispose(disposable)
          executor.shutdown()
          ProgressManager.getInstance().runProcessWithProgressSynchronously({ executor.awaitTermination(100, TimeUnit.MILLISECONDS) },
                                                                            GitBundle.message("stage.vfs.shutdown.process"),
                                                                            false, project)
        }
      }
    })
    connection.subscribe(EncodingManagerListener.ENCODING_MANAGER_CHANGES, MyEncodingManagerListener())
  }

  fun findFile(
    root: VirtualFile, filePath: FilePath,
  ): GitIndexVirtualFile? {
    return cache.get(Key(root, filePath))?.takeIf { it.isValid }
  }

  @RequiresBackgroundThread
  fun createFile(
    root: VirtualFile, filePath: FilePath,
  ): GitIndexVirtualFile? {
    val indexFile = cache.get(Key(root, filePath)) ?: return null
    if (indexFile.data != null) {
      return indexFile
    }

    val stagedFile = readMetadataFromGit(root, filePath) ?: return null
    val length = readLengthFromGit(root, stagedFile.blobHash)

    indexFile.setInitialData(stagedFile.hash(), length, stagedFile.isExecutable)

    return indexFile
  }

  private fun createIndexVirtualFile(key: Key): GitIndexVirtualFile? {
    if (isShutDown) {
      return null
    }

    val indexFile = GitIndexVirtualFile(project, key.root, key.filePath)
    OutsidersPsiFileSupport.markFile(indexFile, key.filePath)
    return indexFile
  }

  fun refresh(condition: (GitIndexVirtualFile) -> Boolean) {
    val filesToRefresh = cache.asMap().values
      .asSequence()
      .filterNotNull()
      .filter { it.data != null }
      .filter(condition)
      .toList()
    if (filesToRefresh.isEmpty()) {
      return
    }
    refreshImpl(filesToRefresh)
  }

  fun initialRefresh(filesToRefresh: List<GitIndexVirtualFile>) {
    refreshImpl(filesToRefresh)
  }

  private fun refreshImpl(filesToRefresh: List<GitIndexVirtualFile>) {
    if (isShutDown) return

    LOG.debug("Starting async refresh for ${filesToRefresh.joinToString { it.path }}")

    BackgroundTaskUtil.execute(executor, disposable) {
      val fileDataList = mutableListOf<IndexFileData>()
      for (file in filesToRefresh) {
        readFromGit(file)?.let { fileDataList.add(it) }
      }
      if (fileDataList.isEmpty()) return@execute
      ProgressManager.getInstance().progressIndicator?.checkCanceled()
      writeInEdtAndWait {
        if (isShutDown) return@writeInEdtAndWait

        val dataToApply = fileDataList.filter { !it.isOutdated() }
        if (dataToApply.isEmpty()) return@writeInEdtAndWait

        applyRefresh(dataToApply)
      }
    }
  }

  private fun readFromGit(file: GitIndexVirtualFile): IndexFileData? {
    val (oldData, oldModificationStamp) = runReadAction { Pair(file.data, file.modificationStamp) }
    return readFromGit(file, oldData, oldModificationStamp)
  }

  private fun readFromGit(
    file: GitIndexVirtualFile,
    oldData: GitIndexVirtualFile.CachedData?,
    oldModificationStamp: Long,
  ): IndexFileData? {
    val stagedFile = readMetadataFromGit(file.root, file.filePath)
    val newHash = stagedFile?.hash()
    if (oldData == null || oldData.hash != newHash) {
      val newLength = if (stagedFile != null) readLengthFromGit(file.root, stagedFile.blobHash) else 0
      LOG.debug("Preparing refresh for $file")
      return IndexFileData(file, oldData, newHash, file.length, newLength, stagedFile?.isExecutable ?: false, oldModificationStamp)
    }
    return null
  }

  private fun applyRefresh(fileDataList: List<IndexFileData>) {
    val events = fileDataList.map { it.event }
    ApplicationManager.getApplication().messageBus.syncPublisher(VirtualFileManager.VFS_CHANGES).before(events)
    fileDataList.forEach { it.apply() }
    ApplicationManager.getApplication().messageBus.syncPublisher(VirtualFileManager.VFS_CHANGES).after(events)
  }

  internal fun write(file: GitIndexVirtualFile, requestor: Any?, newContent: ByteArray, newModificationStamp: Long) {
    try {
      val newModStamp = if (newModificationStamp > 0) newModificationStamp else LocalTimeCounter.currentTime()
      val event = VFileContentChangeEvent(requestor, file, file.modificationStamp, newModStamp)
      ApplicationManager.getApplication().messageBus.syncPublisher(VirtualFileManager.VFS_CHANGES).before(listOf(event))

      val oldData = file.data
      val oldModificationStamp = file.modificationStamp

      val applyChanges = computeUnderPotemkinProgress(project, GitBundle.message("stage.vfs.write.process", file.name)) {
        val indexFileData = readFromGit(file, oldData, oldModificationStamp)
        if (indexFileData != null) {
          LOG.info("Detected memory-disk conflict in $file")
          return@computeUnderPotemkinProgress {
            applyRefresh(listOf(indexFileData))
          }
        }
        val newHash = GitIndexUtil.write(project, file.root, file.filePath, ByteArrayInputStream(newContent), file.isExecutable)
        LOG.debug("Written $file. newHash=$newHash")
        return@computeUnderPotemkinProgress {
          file.setDataFromWrite(newHash, newContent.size.toLong(), newModStamp)
        }
      }

      applyChanges.invoke()

      ApplicationManager.getApplication().messageBus.syncPublisher(VirtualFileManager.VFS_CHANGES).after(listOf(event))
    }
    catch (e: Exception) {
      throw IOException(e)
    }
  }

  @Throws(IOException::class)
  internal fun readContentFromGit(root: VirtualFile, filePath: FilePath): ByteArray {
    return try {
      GitFileUtils.getFileContent(project, root, "", VcsFileUtil.relativePath(root, filePath))
    }
    catch (e: VcsException) {
      throw IOException(e)
    }
  }

  private fun readMetadataFromGit(root: VirtualFile, filePath: FilePath): GitIndexUtil.StagedFile? {
    return GitIndexUtil.listStaged(project, root, listOf(filePath)).singleOrNull()
  }

  private fun readLengthFromGit(root: VirtualFile, hash: String): Long {
    try {
      val h = GitLineHandler(project, root, GitCommand.CAT_FILE)
      h.setSilent(true)
      h.addParameters("-s")
      h.addParameters(hash)
      h.endOptions()
      val output = Git.getInstance().runCommand(h).getOutputOrThrow()
      return java.lang.Long.valueOf(output.trim())
    }
    catch (e: VcsException) {
      LOG.warn(e)
      return 0
    }
  }

  override fun dispose() {
    cache.invalidateAll()
  }

  companion object {
    private val LOG = Logger.getInstance(GitIndexFileSystemRefresher::class.java)

    @JvmStatic
    fun getInstance(project: Project): GitIndexFileSystemRefresher = project.service<GitIndexFileSystemRefresher>()

    @JvmStatic
    fun refreshFilePaths(project: Project, paths: Collection<FilePath>) {
      val pathsSet = paths.toSet()
      project.serviceIfCreated<GitIndexFileSystemRefresher>()?.refresh { pathsSet.contains(it.filePath) }
    }

    @JvmStatic
    fun refreshFilePaths(project: Project, paths: Map<VirtualFile, Collection<FilePath>>) {
      project.serviceIfCreated<GitIndexFileSystemRefresher>()?.refresh {
        paths[it.root]?.contains(it.filePath) == true
      }
    }

    @JvmStatic
    fun refreshVirtualFiles(project: Project, paths: Collection<VirtualFile>) {
      refreshFilePaths(project, paths.map { it.filePath() })
    }

    @JvmStatic
    fun refreshRoots(project: Project, roots: Collection<VirtualFile>) {
      project.serviceIfCreated<GitIndexFileSystemRefresher>()?.refresh {
        roots.contains(it.root)
      }
    }

    private fun GitIndexUtil.StagedFile.hash(): Hash = HashImpl.build(blobHash)

    private fun writeInEdtAndWait(action: () -> Unit) {
      ApplicationManager.getApplication().invokeAndWait {
        ApplicationManager.getApplication().runWriteAction {
          action()
        }
      }
    }

    private fun <T> computeUnderPotemkinProgress(project: Project, @NlsContexts.ProgressTitle message: String, computation: () -> T): T {
      val result = Ref<T>(null)
      PotemkinProgress(message, project, null, null).runInBackground {
        result.set(computation())
      }
      return result.get()
    }
  }

  private inner class IndexFileData(
    private val file: GitIndexVirtualFile,
    private val oldData: GitIndexVirtualFile.CachedData?,
    private val newHash: Hash?,
    oldLength: Long,
    private val newLength: Long,
    private val newExecutable: Boolean,
    oldModificationStamp: Long,
  ) {
    val event = VFileContentChangeEvent(REFRESH_REQUESTOR, file, oldModificationStamp, VFileContentChangeEvent.UNDEFINED_TIMESTAMP_OR_LENGTH.toLong(), 0, 0, oldLength, newLength)

    fun isOutdated() = file.data != null && file.data?.hash != oldData?.hash

    fun apply() {
      LOG.debug("Refreshing $file")
      file.setDataFromRefresh(newHash, newLength, newExecutable)
    }

    override fun toString(): @NonNls String {
      return "IndexFileData: $file"
    }
  }

  private data class Key(val root: VirtualFile, val filePath: FilePath)

  /**
   * @see com.intellij.lang.properties.Native2AsciiListener
   */
  private inner class MyEncodingManagerListener : EncodingManagerListener {
    override fun propertyChanged(eventDocument: Document?, propertyName: String, oldValue: Any?, newValue: Any?) {
      if (EncodingManager.PROP_NATIVE2ASCII_SWITCH == propertyName ||
          EncodingManager.PROP_PROPERTIES_FILES_ENCODING == propertyName) {
        ApplicationManager.getApplication().invokeLater {
          ApplicationManager.getApplication().runWriteAction {
            reloadCachedPropertiesFiles()
          }
        }
      }
    }

    private fun reloadCachedPropertiesFiles() {
      val virtualFiles = cache.asMap().values.filter { it != null && FileTypeRegistry.getInstance().isFileOfType(it, StdFileTypes.PROPERTIES) }
      for (file in virtualFiles) {
        val document = FileDocumentManager.getInstance().getCachedDocument(file!!)
        if (document != null) FileDocumentManager.getInstance().saveDocument(document)

        file.setCharset(null)
      }
      FileDocumentManager.getInstance().reloadFiles(*VfsUtil.toVirtualFileArray(virtualFiles))
    }
  }
}
