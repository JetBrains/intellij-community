// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.vfs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.PotemkinProgress
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.*
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
import org.jetbrains.annotations.NonNls
import java.io.*
import java.util.*

class GitIndexVirtualFile(private val project: Project,
                          val root: VirtualFile,
                          val filePath: FilePath) : VirtualFile(), VirtualFilePathWrapper {
  @Volatile
  private var hash: Hash? = null
  @Volatile
  private var length: Long = 0
  @Volatile
  private var isExecutable = false
  @Volatile
  private var modificationStamp = LocalTimeCounter.currentTime()

  init {
    val task = Runnable {
      val stagedFile = readMetadataFromGit() ?: return@Runnable
      hash = stagedFile.hash()
      length = readLengthFromGit(stagedFile.blobHash)
      isExecutable = stagedFile.isExecutable
    }
    if (ApplicationManager.getApplication().isDispatchThread) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(task,
                                                                        GitBundle.message("stage.vfs.read.process", name),
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
  override fun isValid(): Boolean = hash != null && !project.isDisposed
  override fun getName(): String = filePath.name
  override fun getPresentableName(): String = GitBundle.message("stage.vfs.presentable.file.name", filePath.name)
  override fun getPath(): String = encode(project, root, filePath)
  override fun getPresentablePath(): String = filePath.path
  override fun enforcePresentableName(): Boolean = true
  override fun getLength(): Long = length
  override fun getTimeStamp(): Long = 0
  override fun getModificationStamp(): Long = modificationStamp
  override fun getFileType(): FileType = filePath.virtualFile?.fileType ?: super.getFileType()
  override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
    LOG.error("Refreshing index files is not supported (called for $this). Use GitIndexFileSystemRefresher to refresh.")
  }

  internal fun readFromGit(): IndexFileData? {
    val oldHash = hash
    val stagedFile = readMetadataFromGit()
    val newHash = stagedFile?.hash()
    if (oldHash != newHash) {
      val newLength = if (stagedFile != null) readLengthFromGit(stagedFile.blobHash) else 0
      LOG.debug("Preparing refresh for $this")
      return IndexFileData(oldHash, newHash, length, newLength, stagedFile?.isExecutable ?: false, modificationStamp)
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
    try {
      val newModStamp = if (newModificationStamp > 0) newModificationStamp else LocalTimeCounter.currentTime()
      val event = VFileContentChangeEvent(requestor, this, modificationStamp, newModStamp, false)
      ApplicationManager.getApplication().messageBus.syncPublisher(VirtualFileManager.VFS_CHANGES).before(listOf(event))

      val oldHash = hash
      val newHash = Ref<Hash>(null)

      PotemkinProgress(GitBundle.message("stage.vfs.write.process", this.name), project, null, null).runInBackground {
        if (oldHash != readMetadataFromGit()?.hash()) {
          LOG.warn("Skipping write for $this as it is not up to date")
          return@runInBackground
        }
        newHash.set(GitIndexUtil.write(project, root, filePath, ByteArrayInputStream(newContent), isExecutable))
        LOG.debug("Written $this. newHash=$newHash")
      }

      if (newHash.get() != null) {
        hash = newHash.get()
        length = newContent.size.toLong()
        modificationStamp = newModStamp
      }

      ApplicationManager.getApplication().messageBus.syncPublisher(VirtualFileManager.VFS_CHANGES).after(listOf(event))
    }
    catch (e: Exception) {
      throw IOException(e)
    }
  }

  @Throws(IOException::class)
  override fun getInputStream(): InputStream {
    return VfsUtilCore.byteStreamSkippingBOM(contentsToByteArray(), this)
  }

  @Throws(IOException::class)
  override fun contentsToByteArray(): ByteArray {
    try {
      if (ApplicationManager.getApplication().isDispatchThread) {
        return ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable<ByteArray, IOException> {
          contentToByteArrayImpl()
        }, GitBundle.message("stage.vfs.read.process", name), false, project)
      }
      else {
        return contentToByteArrayImpl()
      }
    }
    catch (e: Exception) {
      throw IOException(e)
    }
  }

  private fun contentToByteArrayImpl(): ByteArray {
    return try {
      GitFileUtils.getFileContent(project, root, "", VcsFileUtil.relativePath(root, filePath))
    }
    catch (e: VcsException) {
      throw IOException(e)
    }
  }

  private fun readMetadataFromGit(): GitIndexUtil.StagedFile? {
    return GitIndexUtil.listStaged(project, root, listOf(filePath)).singleOrNull()
  }

  private fun readLengthFromGit(hash: String): Long {
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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as GitIndexVirtualFile

    if (project != other.project) return false
    if (root != other.root) return false
    if (filePath != other.filePath) return false

    return true
  }

  override fun hashCode(): Int {
    return Objects.hash(project, root, filePath)
  }

  override fun toString(): @NonNls String {
    return "GitIndexVirtualFile: [${root.name}]/${VcsFileUtil.relativePath(root, filePath)}"
  }

  internal inner class IndexFileData internal constructor(private val oldHash: Hash?,
                                                          private val newHash: Hash?,
                                                          oldLength: Long,
                                                          private val newLength: Long,
                                                          private val newExecutable: Boolean,
                                                          oldModificationStamp: Long) {
    val event: VFileContentChangeEvent = VFileContentChangeEvent(null, this@GitIndexVirtualFile, oldModificationStamp, -1,
                                                                 0, 0,
                                                                 oldLength, newLength, true)

    fun apply(): Boolean {
      LOG.debug("Refreshing ${this@GitIndexVirtualFile}")
      if (hash != oldHash) return false
      hash = newHash
      length = newLength
      isExecutable = newExecutable
      return true
    }

    override fun toString(): @NonNls String {
      return "IndexFileData: ${this@GitIndexVirtualFile}"
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

    private fun GitIndexUtil.StagedFile.hash(): Hash = HashImpl.build(blobHash)
  }
}

internal fun VirtualFile.filePath(): FilePath {
  return if (this is GitIndexVirtualFile) this.filePath
  else VcsUtil.getFilePath(this)
}