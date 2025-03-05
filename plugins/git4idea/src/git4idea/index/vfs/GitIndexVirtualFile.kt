// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.vfs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.util.LocalTimeCounter
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.vcs.log.Hash
import com.intellij.vcsUtil.VcsFileUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.NonNls
import java.io.*
import java.util.*

class GitIndexVirtualFile(
  private val project: Project,
  val root: VirtualFile,
  val filePath: FilePath,
) : VirtualFile(), VirtualFilePathWrapper {
  init {
    putUserData(FileDocumentManagerBase.TRACK_NON_PHYSICAL, true)
  }

  @Volatile
  internal var data: CachedData? = null // null: the file was not loaded yet

  @Volatile
  private var modificationStamp = LocalTimeCounter.currentTime()

  override fun getFileSystem(): GitIndexFileSystem = GitIndexFileSystem.instance
  override fun getParent(): VirtualFile? = null
  override fun getChildren(): Array<VirtualFile> = EMPTY_ARRAY
  override fun isWritable(): Boolean = true
  override fun isDirectory(): Boolean = false
  override fun isValid(): Boolean = !project.isDisposed && (data == null || data?.hash != null)
  override fun getName(): String = filePath.name
  override fun getPresentableName(): String = GitBundle.message("stage.vfs.presentable.file.name", filePath.name)
  override fun getPath(): String = encode(project, root, filePath)
  override fun getPresentablePath(): String = filePath.path
  override fun enforcePresentableName(): Boolean = true
  internal val isExecutable: Boolean get() = data?.isExecutable ?: false
  override fun getLength(): Long = data?.length ?: 0
  override fun getTimeStamp(): Long = 0
  override fun getModificationStamp(): Long = modificationStamp
  override fun getFileType(): FileType = filePath.virtualFile?.fileType ?: super.getFileType()
  override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
    LOG.error("Refreshing index files is not supported (called for $this). Use GitIndexFileSystemRefresher to refresh.")
  }

  internal fun setInitialData(newHash: Hash?, newLength: Long, newExecutable: Boolean) {
    data = CachedData(newHash, newLength, newExecutable)
  }

  @RequiresWriteLock
  internal fun setDataFromRefresh(newHash: Hash?, newLength: Long, newExecutable: Boolean) {
    data = CachedData(newHash, newLength, newExecutable)
  }

  @RequiresWriteLock
  internal fun setDataFromWrite(newHash: Hash, newLength: Long, newModificationStamp: Long) {
    data = CachedData(newHash, newLength, isExecutable)
    modificationStamp = newModificationStamp
  }

  @Throws(IOException::class)
  override fun getOutputStream(requestor: Any?,
                               newModificationStamp: Long,
                               newTimeStamp: Long): OutputStream {
    val outputStream: ByteArrayOutputStream = object : ByteArrayOutputStream() {
      override fun close() {
        GitIndexFileSystemRefresher.getInstance(project).write(this@GitIndexVirtualFile, requestor, toByteArray(), newModificationStamp)
      }
    }
    return VfsUtilCore.outputStreamAddingBOM(outputStream, this)
  }

  @Throws(IOException::class)
  override fun getInputStream(): InputStream {
    return VfsUtilCore.byteStreamSkippingBOM(contentsToByteArray(), this)
  }

  @Throws(IOException::class)
  override fun contentsToByteArray(): ByteArray {
    if (data == null) {
      GitIndexFileSystemRefresher.getInstance(project).initialRefresh(listOf(this))
    }

    try {
      if (ApplicationManager.getApplication().isDispatchThread) {
        return ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable<ByteArray, IOException> {
          GitIndexFileSystemRefresher.getInstance(project).readContentFromGit(root, filePath)
        }, GitBundle.message("stage.vfs.read.process", name), false, project)
      }
      else {
        return GitIndexFileSystemRefresher.getInstance(project).readContentFromGit(root, filePath)
      }
    }
    catch (e: Exception) {
      throw IOException(e)
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

  companion object {
    private val LOG = Logger.getInstance(GitIndexVirtualFile::class.java)
    private const val SEPARATOR = ':'

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
      val filePath = VcsUtil.getFilePath(StringUtil.unescapeChar(components[2], SEPARATOR), false)

      return Triple(project, root, filePath)
    }

    fun extractPresentableUrl(path: String): String {
      return path.substringAfterLast(SEPARATOR).replace('/', File.separatorChar)
    }
  }

  internal data class CachedData(val hash: Hash?, val length: Long, val isExecutable: Boolean)
}

internal fun VirtualFile.filePath(): FilePath {
  return if (this is GitIndexVirtualFile) this.filePath
  else VcsUtil.getFilePath(this)
}