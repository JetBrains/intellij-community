// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
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
      module.getService(LegacyBridgeFilePointerProvider::class.java)!!

  }
}

fun LegacyBridgeFileContainer.getAndCacheVirtualFilePointerContainer(provider: LegacyBridgeFilePointerProvider) =
  provider.getAndCacheFileContainer(this)
