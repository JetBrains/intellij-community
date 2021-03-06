package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager

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

val VirtualFileUrl.virtualFile
  get() = if (this is VirtualFileUrlBridge) file else VirtualFileManager.getInstance().findFileByUrl(url)

// TODO: use segment names from virtualFiles?
fun VirtualFile.toVirtualFileUrl(virtualFileManager: VirtualFileUrlManager) = virtualFileManager.fromUrl(this.url)
