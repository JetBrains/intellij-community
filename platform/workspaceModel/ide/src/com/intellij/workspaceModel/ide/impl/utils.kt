package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
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

// TODO: use segment names from virtualFiles?
fun VirtualFile.toVirtualFileUrl(virtualFileManager: VirtualFileUrlManager) = virtualFileManager.fromUrl(this.url)
