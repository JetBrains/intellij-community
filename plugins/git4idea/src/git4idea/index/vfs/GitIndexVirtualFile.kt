// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.vfs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.util.LocalTimeCounter
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcsUtil.VcsFileUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.i18n.GitBundle
import git4idea.index.GitIndexUtil
import git4idea.util.GitFileUtils
import java.io.*
import java.util.concurrent.atomic.AtomicReference

class GitIndexVirtualFile(private val project: Project,
                          val root: VirtualFile,
                          val filePath: FilePath) : VirtualFile(), VirtualFilePathWrapper {
  private val refresher: GitIndexFileSystemRefresher get() = GitIndexFileSystemRefresher.getInstance(project)

  private val cachedData: AtomicReference<CachedData?> = AtomicReference()

  @Volatile
  private var modificationStamp = LocalTimeCounter.currentTime()

  init {
    val task = Runnable { cachedData.compareAndSet(null, readCachedData()) }
    if (ApplicationManager.getApplication().isDispatchThread) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(task, GitBundle.message("stage.vfs.read.process", name),
                                                                        false, project)
    }
    else {
      task.run()
    }
  }

  override fun getFileSystem(): GitIndexFileSystem = GitIndexFileSystem.instance
  override fun getParent(): VirtualFile? = null
  override fun getChildren(): Array<VirtualFile> = EMPTY_ARRAY
  override fun isWritable(): Boolean = true
  override fun isDirectory(): Boolean = false
  override fun isValid(): Boolean = cachedData.get() != null
  override fun getName(): String = filePath.name
  override fun getPresentableName(): String = GitBundle.message("stage.vfs.presentable.file.name", filePath.name)
  override fun getPath(): String = encode(project, root, filePath)
  override fun getPresentablePath(): String = filePath.path
  override fun enforcePresentableName(): Boolean = true
  override fun getLength(): Long = cachedData.get()?.length ?: 0
  override fun getTimeStamp(): Long = 0
  override fun getModificationStamp(): Long = modificationStamp
  override fun getFileType(): FileType = filePath.virtualFile?.fileType ?: super.getFileType()
  override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
    refresher.refresh(listOf(this), asynchronous, postRunnable)
  }

  internal fun getRefresh(): Refresh? {
    val newCachedData = readCachedData()
    val oldCachedData = cachedData.get()
    if (oldCachedData != newCachedData) {
      LOG.debug("Preparing refresh for $this")
      return Refresh(oldCachedData, newCachedData, modificationStamp)
    }
    return null
  }

  @Throws(IOException::class)
  override fun getOutputStream(requestor: Any?,
                               newModificationStamp: Long,
                               newTimeStamp: Long): OutputStream {
    val outputStream: ByteArrayOutputStream = object : ByteArrayOutputStream() {
      override fun close() = write(requestor, toByteArray(), newModificationStamp)
    }
    return VfsUtilCore.outputStreamAddingBOM(outputStream, this)
  }

  private fun write(requestor: Any?, newContent: ByteArray, newModificationStamp: Long) {
    val newModStamp = if (newModificationStamp > 0) newModificationStamp else LocalTimeCounter.currentTime()
    refresher.changeContent(this, requestor, modificationStamp) {
      val oldCachedData = cachedData.get()
      if (oldCachedData != readCachedData()) {
        // TODO
        LOG.warn("Skipping write for $this as it is not up to date")
        return@changeContent
      }

      val isExecutable = oldCachedData?.isExecutable ?: false
      val newHash = GitIndexUtil.write(project, root, filePath, ByteArrayInputStream(newContent), isExecutable)
      LOG.debug("Written $this. newHash=$newHash")

      modificationStamp = newModStamp
      if (oldCachedData?.hash != newHash) {
        cachedData.compareAndSet(oldCachedData, CachedData(newHash, calculateLength(newHash.asString()), isExecutable))
      }
    }
  }

  @Throws(IOException::class)
  override fun getInputStream(): InputStream {
    return VfsUtilCore.byteStreamSkippingBOM(contentsToByteArray(), this)
  }

  @Throws(IOException::class)
  override fun contentsToByteArray(): ByteArray {
    return try {
      GitFileUtils.getFileContent(project, root, "", VcsFileUtil.relativePath(root, filePath))
    }
    catch (e: VcsException) {
      throw IOException(e)
    }
  }

  private fun readCachedData(): CachedData? {
    val stagedFile = GitIndexUtil.listStaged(project, root, listOf(filePath)).singleOrNull() ?: return null
    return CachedData(HashImpl.build(stagedFile.blobHash), calculateLength(stagedFile.blobHash), stagedFile.isExecutable)
  }

  @Throws(VcsException::class)
  private fun calculateLength(hash: String): Long {
    val h = GitLineHandler(project, root, GitCommand.CAT_FILE)
    h.setSilent(true)
    h.addParameters("-s")
    h.addParameters(hash)
    h.endOptions()
    val output = Git.getInstance().runCommand(h).getOutputOrThrow()
    return java.lang.Long.valueOf(output.trim())
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as GitIndexVirtualFile

    if (root != other.root) return false
    if (filePath != other.filePath) return false

    return true
  }

  override fun hashCode(): Int {
    var result = root.hashCode()
    result = 31 * result + filePath.hashCode()
    return result
  }

  override fun toString(): String {
    return "GitIndexVirtualFile: [${root.name}]/${VcsFileUtil.relativePath(root, filePath)}"
  }

  internal inner class Refresh internal constructor(private val oldCachedData: CachedData?,
                                                    private val newCachedData: CachedData?,
                                                    oldModificationStamp: Long) {
    val event: VFileContentChangeEvent = VFileContentChangeEvent(null, this@GitIndexVirtualFile, oldModificationStamp, -1,
                                                                 0, 0,
                                                                 oldCachedData?.length ?: 0, newCachedData?.length ?: 0, true)

    fun run(): Boolean {
      LOG.debug("Refreshing ${this@GitIndexVirtualFile}")
      return cachedData.compareAndSet(oldCachedData, newCachedData)
    }

    override fun toString(): String {
      return "GitIndexVirtualFile.Refresh: ${this@GitIndexVirtualFile}"
    }
  }

  companion object {
    private val LOG = Logger.getInstance(GitIndexVirtualFile::class.java)
    const val SEPARATOR = ':'

    private fun encode(project: Project, root: VirtualFile, filePath: FilePath): String {
      return StringUtil.escapeChar(project.locationHash, SEPARATOR) + SEPARATOR +
             StringUtil.escapeChar(root.path, SEPARATOR) + SEPARATOR +
             StringUtil.escapeChar(filePath.path, SEPARATOR)
    }

    fun decode(path: String): Triple<Project, VirtualFile, FilePath>? {
      val components = path.split(SEPARATOR)
      if (components.size != 3) return null

      val locationHash = StringUtil.unescapeChar(components[0], SEPARATOR)
      val project = ProjectManager.getInstance().openProjects.firstOrNull { it.locationHash == locationHash } ?: return null
      val root = LocalFileSystem.getInstance().findFileByPath(StringUtil.unescapeChar(components[1], SEPARATOR)) ?: return null
      val filePath = VcsUtil.getFilePath(StringUtil.unescapeChar(components[2], SEPARATOR))

      return Triple(project, root, filePath)
    }

    fun extractPresentableUrl(path: String): String {
      return path.substringAfterLast(SEPARATOR).replace('/', File.separatorChar)
    }
  }

  internal data class CachedData(val hash: Hash, val length: Long, val isExecutable: Boolean = false)
}

internal fun VirtualFile.filePath(): FilePath {
  return if (this is GitIndexVirtualFile) this.filePath
  else VcsUtil.getFilePath(this)
}