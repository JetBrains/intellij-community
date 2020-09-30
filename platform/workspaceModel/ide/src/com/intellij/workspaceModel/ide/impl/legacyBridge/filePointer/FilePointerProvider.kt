// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.filePointer

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer
import com.intellij.workspaceModel.storage.VirtualFileUrl

data class FileContainerDescription(
  val urls: List<VirtualFileUrl>,
  val jarDirectories: List<JarDirectoryDescription>
)

data class JarDirectoryDescription(val directoryUrl: VirtualFileUrl, val recursive: Boolean)

interface FilePointerProvider {
  fun getAndCacheSourceRoot(url: VirtualFileUrl): VirtualFilePointer
  fun getAndCacheContentRoot(url: VirtualFileUrl): VirtualFilePointer
  fun getAndCacheExcludedRoot(url: VirtualFileUrl): VirtualFilePointer
  fun getAndCacheModuleRoot(name: String, url: VirtualFileUrl): VirtualFilePointer
  fun getAndCacheFileContainer(description: FileContainerDescription, scope: Disposable): VirtualFilePointerContainer

  companion object {
    @JvmStatic
    fun getInstance(project: Project): FilePointerProvider = project.service()

    @JvmStatic
    fun getInstance(module: Module): FilePointerProvider =
      module.getService(FilePointerProvider::class.java)!!

  }
}

fun FileContainerDescription.getAndCacheVirtualFilePointerContainer(provider: FilePointerProvider, scope: Disposable) =
  provider.getAndCacheFileContainer(this, scope)
