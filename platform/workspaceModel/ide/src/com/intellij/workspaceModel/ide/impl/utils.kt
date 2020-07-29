package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.workspaceModel.storage.VirtualFileUrl
import com.intellij.workspaceModel.storage.VirtualFileUrlManager
import kotlin.concurrent.getOrSet

private val bracketIndent = ThreadLocal<Int>()

internal fun <R> Logger.bracket(message: String, block: () -> R): R {
  val indentLevel = bracketIndent.getOrSet { 0 }
  val indent = "  ".repeat(indentLevel)

  info("START: $indent$message")
  bracketIndent.set(indentLevel + 1)
  try {
    return block()
  }
  finally {
    bracketIndent.set(indentLevel)
    info("END  : $indent$message")
  }
}

// TODO Drop? Use standard function? Review usages.
internal fun executeOrQueueOnDispatchThread(block: () -> Unit) {
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
