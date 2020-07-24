// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer
import com.intellij.workspace.api.*

data class LegacyBridgeFileContainer(
  val urls: List<VirtualFileUrl>,
  val jarDirectories: List<LegacyBridgeJarDirectory>
)

data class LegacyBridgeJarDirectory(val directoryUrl: VirtualFileUrl, val recursive: Boolean)

internal interface LegacyBridgeFilePointerProvider {
  fun getAndCacheFilePointer(url: VirtualFileUrl, scope: LegacyBridgeFilePointerScope): VirtualFilePointer
  fun getAndCacheFileContainer(description: LegacyBridgeFileContainer): VirtualFilePointerContainer

  companion object {
    @JvmStatic
    fun getInstance(project: Project): LegacyBridgeFilePointerProvider = project.service()

    @JvmStatic
    fun getInstance(module: Module): LegacyBridgeFilePointerProvider =
      module.getService(LegacyBridgeFilePointerProvider::class.java)!!

  }
}

internal fun LegacyBridgeFileContainer.getAndCacheVirtualFilePointerContainer(provider: LegacyBridgeFilePointerProvider) =
  provider.getAndCacheFileContainer(this)

/**
 * This class defines rules when virtual file url became invalid and virtual file pointer should be disposed
 *   VirtualFilePointer will be disposed in case [checkUrl] returns true
 *
 * This class is designed to control a very limited scope of virtual file pointers. It means that it's not created for common cases and
 *   it's probably already used everywhere it should be used.
 */
internal sealed class LegacyBridgeFilePointerScope(
  val checkUrl: (TypedEntity, VirtualFileUrl) -> Boolean
) {
  object SourceRoot : LegacyBridgeFilePointerScope({ entity, url -> entity is SourceRootEntity && entity.url == url })
  object ExcludedRoots : LegacyBridgeFilePointerScope({ entity, url -> entity is ContentRootEntity && url in entity.excludedUrls })
  object ContentRoots : LegacyBridgeFilePointerScope({ entity, url -> entity is ContentRootEntity && entity.url == url })
  class Module(val name: String) : LegacyBridgeFilePointerScope({ entity, _ -> entity is ModuleEntity && entity.name == name })

  // Just any content root entity
  object Test: LegacyBridgeFilePointerScope({ _, _ -> false })
}
