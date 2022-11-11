package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager

fun VirtualFileUrl.toVirtualFile(): VirtualFile? = if (this is VirtualFileUrlBridge) file else VirtualFileManager.getInstance().findFileByUrl(url)

// TODO: use segment names from virtualFiles?
fun VirtualFile.toVirtualFileUrl(virtualFileManager: VirtualFileUrlManager): VirtualFileUrl = virtualFileManager.fromUrl(this.url)
