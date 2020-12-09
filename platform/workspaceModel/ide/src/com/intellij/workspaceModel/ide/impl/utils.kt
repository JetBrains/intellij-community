package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.workspaceModel.storage.VirtualFileUrl
import com.intellij.workspaceModel.storage.VirtualFileUrlManager

// TODO Drop? Use standard function? Review usages.
fun executeOrQueueOnDispatchThread(block: () -> Unit) {
  val application = ApplicationManager.getApplication()
  if (application.isDispatchThread) {
    block()
  }
  else {
    application.invokeLater(block)
  }
}

// TODO In the future it may be optimised by caching virtualFile in every trie node
val VirtualFileUrl.virtualFile
  get() = VirtualFileManager.getInstance().findFileByUrl(url)

// TODO: use segment names from virtualFiles?
fun VirtualFile.toVirtualFileUrl(virtualFileManager: VirtualFileUrlManager) = virtualFileManager.fromUrl(this.url)
