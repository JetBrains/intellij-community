package com.intellij.driver.sdk

import com.intellij.driver.client.Remote

@Remote("com.intellij.openapi.vfs.VirtualFile")
interface VirtualFile {
  fun getName(): String

  fun findChild(name: String): VirtualFile?
  fun findFileByRelativePath(relPath: String): VirtualFile?
}
