// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.vfs

import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.VirtualFileFilteringListener
import git4idea.i18n.GitBundle
import java.util.concurrent.ConcurrentHashMap

private const val PROTOCOL = "gitIndexFs"

class GitIndexFileSystem : VirtualFileSystem(), NonPhysicalFileSystem {
  private val listenerWrappers: MutableMap<VirtualFileListener, VirtualFileListener> = ConcurrentHashMap()

  override fun getProtocol(): String = PROTOCOL
  override fun isReadOnly(): Boolean = false

  override fun findFileByPath(path: String): VirtualFile? {
    val (project, root, filePath) = GitIndexVirtualFile.decode(path) ?: return null

    // we do not check actual content here
    // the returned file is valid, but may stop being such after the first read access
    // this is a tradeoff with making EditorHistoryManager load every file that was ever loaded
    return GitIndexFileSystemRefresher.getInstance(project).findFile(root, filePath)
  }

  override fun refreshAndFindFileByPath(path: String): VirtualFile? = findFileByPath(path)
  override fun refresh(asynchronous: Boolean) {}

  override fun extractPresentableUrl(path: String): String {
    val extractPresentableUrl = GitIndexVirtualFile.extractPresentableUrl(path)
    return GitBundle.message("stage.vfs.presentable.file.name", extractPresentableUrl)
  }

  override fun addVirtualFileListener(listener: VirtualFileListener) {
    val wrapper: VirtualFileListener = VirtualFileFilteringListener(listener, this)
    VirtualFileManager.getInstance().addVirtualFileListener(wrapper)
    listenerWrappers[listener] = wrapper
  }

  override fun removeVirtualFileListener(listener: VirtualFileListener) {
    listenerWrappers.remove(listener)?.let {
      VirtualFileManager.getInstance().removeVirtualFileListener(it)
    }
  }

  override fun deleteFile(requestor: Any?, vFile: VirtualFile) = throw UnsupportedOperationException()
  override fun moveFile(requestor: Any?, vFile: VirtualFile, newParent: VirtualFile) = throw UnsupportedOperationException()
  override fun renameFile(requestor: Any?, vFile: VirtualFile, newName: String) = throw UnsupportedOperationException()
  override fun createChildFile(requestor: Any?, vDir: VirtualFile, fileName: String): VirtualFile = throw UnsupportedOperationException()
  override fun createChildDirectory(requestor: Any?, vDir: VirtualFile, dirName: String): VirtualFile = throw UnsupportedOperationException()
  override fun copyFile(requestor: Any?, virtualFile: VirtualFile, newParent: VirtualFile, copyName: String): VirtualFile  = throw UnsupportedOperationException()

  companion object {
    @JvmStatic
    val instance: GitIndexFileSystem
      get() = VirtualFileManager.getInstance().getFileSystem(PROTOCOL) as GitIndexFileSystem
  }
}