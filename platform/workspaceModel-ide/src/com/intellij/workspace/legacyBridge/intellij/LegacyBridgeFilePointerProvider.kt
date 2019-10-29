package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer
import com.intellij.workspace.api.VirtualFileUrl

data class LegacyBridgeFileContainer(
  val urls: List<VirtualFileUrl>,
  val jarDirectories: List<LegacyBridgeJarDirectory>
)

data class LegacyBridgeJarDirectory(val directoryUrl: VirtualFileUrl, val recursive: Boolean)

interface LegacyBridgeFilePointerProvider {
  fun getAndCacheFilePointer(url: VirtualFileUrl): VirtualFilePointer
  fun getAndCacheFileContainer(description: LegacyBridgeFileContainer): VirtualFilePointerContainer

  companion object {
    @JvmStatic
    fun getInstance(project: Project): LegacyBridgeFilePointerProvider = project.service()

    @JvmStatic
    fun getInstance(module: Module): LegacyBridgeFilePointerProvider =
      ModuleServiceManager.getService(module, LegacyBridgeFilePointerProvider::class.java)!!
  }
}
